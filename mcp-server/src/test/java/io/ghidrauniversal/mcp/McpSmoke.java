package io.ghidrauniversal.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ghidrauniversal.core.Json;
import io.ghidrauniversal.engine.GhidraInstall;
import io.ghidrauniversal.engine.HeadlessGhidraEngine;
import io.ghidrauniversal.core.project.GuProject;
import io.ghidrauniversal.core.project.Workspace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * End-to-end check of the MCP server: drives it over HTTP+SSE the way an MCP
 * client would (initialize, tools/list, tools/call), against a real analyzed
 * binary. Run via the {@code mcpSmoke} Gradle task.
 */
public final class McpSmoke {

    private static final ObjectMapper M = Json.mapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private final Map<Integer, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private volatile String postUrl;
    private volatile String origin;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: McpSmoke <binary> <workspaceRoot>");
            System.exit(2);
        }
        new McpSmoke().run(Path.of(args[0]), Path.of(args[1]));
    }

    private void run(Path binary, Path wsRoot) throws Exception {
        String dir = System.getenv("GHIDRA_INSTALL_DIR");
        if (dir == null) {
            System.err.println("GHIDRA_INSTALL_DIR not set");
            System.exit(2);
        }
        Workspace ws = new Workspace(wsRoot);
        HeadlessGhidraEngine engine = new HeadlessGhidraEngine(GhidraInstall.at(Path.of(dir)), ws, l -> {});
        AnalysisContext ctx = new AnalysisContext(engine);

        System.out.println("== analyze ==");
        GuProject project = ws.importBinary(binary, null);
        ctx.open(project);

        McpDispatcher dispatcher = new McpDispatcher(
                "ghidra-universal", "0.1.0", new GhidraTools(ctx).all());
        McpServer server = new McpServer(McpServerConfig.defaults(), dispatcher, l -> {});
        server.start();
        this.origin = "http://127.0.0.1:" + server.port();
        System.out.println("MCP at " + server.sseUrl());

        try {
            openSseStream(server.sseUrl());
            waitFor(() -> postUrl != null, 5000, "endpoint event");
            System.out.println("endpoint: " + postUrl);

            JsonNode init = rpc("initialize", M.createObjectNode());
            check("initialize returns protocolVersion",
                    init.path("result").hasNonNull("protocolVersion"));

            JsonNode list = rpc("tools/list", M.createObjectNode());
            int toolCount = list.path("result").path("tools").size();
            check("tools/list returns the full surface (>=14)", toolCount >= 14);
            System.out.println("tools: " + toolCount);

            JsonNode funcs = callTool("list_functions", M.createObjectNode());
            String funcsText = toolText(funcs);
            check("list_functions returns JSON array", funcsText.trim().startsWith("["));

            JsonNode dec = callTool("get_decompiled_c", M.createObjectNode().put("function", "main"));
            check("get_decompiled_c(main) returns code", toolText(dec).contains("main"));

            JsonNode info = callTool("get_project_info", M.createObjectNode());
            check("get_project_info has sha256", toolText(info).contains("sha256"));

            JsonNode cg = callTool("get_call_graph", M.createObjectNode().put("function", "main"));
            check("call graph for main mentions compute", toolText(cg).contains("compute"));

            // write-back through MCP, then confirm via a read
            JsonNode add = firstFunctionNamed(funcsText, "add");
            if (add != null) {
                String addr = add.get("address").asText();
                JsonNode ren = callTool("rename_function",
                        M.createObjectNode().put("address", addr).put("newName", "mcp_renamed_add"));
                check("rename_function ok", toolText(ren).contains("\"ok\":true"));
                JsonNode after = callTool("list_functions",
                        M.createObjectNode().put("query", "mcp_renamed_add"));
                check("rename visible via list_functions", toolText(after).contains("mcp_renamed_add"));
            }

            System.out.println("ALL MCP CHECKS PASSED");
        } finally {
            server.stop();
        }
    }

    // --- MCP/JSON-RPC over HTTP+SSE ---

    private void openSseStream(String sseUrl) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(sseUrl))
                .header("Accept", "text/event-stream").GET().build();
        client.sendAsync(req, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(resp -> consume(resp.body()));
    }

    private void consume(Stream<String> lines) {
        String[] event = {null};
        StringBuilder data = new StringBuilder();
        lines.forEach(line -> {
            if (line.isEmpty()) {
                dispatchEvent(event[0], data.toString());
                event[0] = null;
                data.setLength(0);
            } else if (line.startsWith("event: ")) {
                event[0] = line.substring(7);
            } else if (line.startsWith("data: ")) {
                if (data.length() > 0) {
                    data.append('\n');
                }
                data.append(line.substring(6));
            }
        });
    }

    private void dispatchEvent(String event, String data) {
        if ("endpoint".equals(event)) {
            postUrl = origin + data;
        } else if ("message".equals(event) && !data.isBlank()) {
            try {
                JsonNode node = M.readTree(data);
                JsonNode id = node.get("id");
                if (id != null && id.isInt()) {
                    CompletableFuture<JsonNode> f = pending.remove(id.asInt());
                    if (f != null) {
                        f.complete(node);
                    }
                }
            } catch (Exception ignore) {
                // non-correlatable notification
            }
        }
    }

    private JsonNode rpc(String method, JsonNode params) throws Exception {
        int id = ids.incrementAndGet();
        var payload = M.createObjectNode();
        payload.put("jsonrpc", "2.0").put("id", id).put("method", method);
        payload.set("params", params);
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(id, future);
        HttpRequest req = HttpRequest.newBuilder(URI.create(postUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
        HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
        if (resp.statusCode() != 202) {
            throw new IllegalStateException("POST returned " + resp.statusCode());
        }
        return future.get(60, TimeUnit.SECONDS);
    }

    private JsonNode callTool(String name, JsonNode arguments) throws Exception {
        var params = M.createObjectNode();
        params.put("name", name);
        params.set("arguments", arguments);
        return rpc("tools/call", params);
    }

    private static String toolText(JsonNode response) {
        JsonNode content = response.path("result").path("content");
        if (content.isArray() && content.size() > 0) {
            return content.get(0).path("text").asText("");
        }
        return "";
    }

    private JsonNode firstFunctionNamed(String funcsJson, String name) throws Exception {
        JsonNode arr = M.readTree(funcsJson);
        for (JsonNode f : arr) {
            if (name.equals(f.path("name").asText())) {
                return f;
            }
        }
        return null;
    }

    // --- tiny assertion helpers ---

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS " : "FAIL ") + label);
        if (!ok) {
            throw new AssertionError(label);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier cond, long timeoutMs, String what)
            throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for " + what);
    }

    private McpSmoke() {}
}
