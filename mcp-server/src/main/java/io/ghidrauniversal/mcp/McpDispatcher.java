package io.ghidrauniversal.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ghidrauniversal.core.Json;

import java.util.List;
import java.util.Optional;

/**
 * Handles MCP JSON-RPC 2.0 methods. Transport-agnostic: it takes a parsed
 * request node and returns the response node to send back (empty for
 * notifications, which carry no id).
 */
public final class McpDispatcher {

    /** MCP protocol revision this server implements. */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private static final ObjectMapper M = Json.mapper();

    private final String serverName;
    private final String serverVersion;
    private final List<McpTool> tools;

    public McpDispatcher(String serverName, String serverVersion, List<McpTool> tools) {
        this.serverName = serverName;
        this.serverVersion = serverVersion;
        this.tools = tools;
    }

    public Optional<ObjectNode> handle(JsonNode request) {
        JsonNode idNode = request.get("id");
        boolean isNotification = idNode == null || idNode.isNull();
        String method = request.path("method").asText("");
        JsonNode params = request.get("params");

        try {
            return switch (method) {
                case "initialize" -> respond(idNode, initialize());
                case "ping" -> respond(idNode, M.createObjectNode());
                case "tools/list" -> respond(idNode, toolsList());
                case "tools/call" -> respond(idNode, toolsCall(params));
                case "notifications/initialized", "notifications/cancelled" -> Optional.empty();
                default -> {
                    if (isNotification) {
                        yield Optional.empty();
                    }
                    yield error(idNode, -32601, "method not found: " + method);
                }
            };
        } catch (IllegalArgumentException e) {
            return error(idNode, -32602, e.getMessage());
        } catch (Exception e) {
            return error(idNode, -32603, "internal error: " + e.getMessage());
        }
    }

    private ObjectNode initialize() {
        ObjectNode result = M.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode caps = result.putObject("capabilities");
        caps.putObject("tools").put("listChanged", false);
        ObjectNode info = result.putObject("serverInfo");
        info.put("name", serverName);
        info.put("version", serverVersion);
        return result;
    }

    private ObjectNode toolsList() {
        ObjectNode result = M.createObjectNode();
        ArrayNode arr = result.putArray("tools");
        for (McpTool t : tools) {
            ObjectNode spec = arr.addObject();
            spec.put("name", t.name());
            spec.put("description", t.description());
            spec.set("inputSchema", t.inputSchema());
        }
        return result;
    }

    private ObjectNode toolsCall(JsonNode params) throws Exception {
        if (params == null || !params.hasNonNull("name")) {
            throw new IllegalArgumentException("tools/call requires a 'name'");
        }
        String name = params.get("name").asText();
        JsonNode arguments = params.get("arguments");
        McpTool tool = tools.stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown tool: " + name));

        ObjectNode result = M.createObjectNode();
        ArrayNode content = result.putArray("content");
        try {
            String text = tool.call(arguments);
            content.addObject().put("type", "text").put("text", text);
            result.put("isError", false);
        } catch (Exception e) {
            content.addObject().put("type", "text")
                    .put("text", "tool '" + name + "' failed: " + e.getMessage());
            result.put("isError", true);
        }
        return result;
    }

    // --- JSON-RPC envelope helpers ---

    private Optional<ObjectNode> respond(JsonNode id, ObjectNode result) {
        if (id == null || id.isNull()) {
            return Optional.empty();
        }
        ObjectNode resp = M.createObjectNode();
        resp.put("jsonrpc", "2.0");
        resp.set("id", id);
        resp.set("result", result);
        return Optional.of(resp);
    }

    private Optional<ObjectNode> error(JsonNode id, int code, String message) {
        ObjectNode resp = M.createObjectNode();
        resp.put("jsonrpc", "2.0");
        if (id != null && !id.isNull()) {
            resp.set("id", id);
        } else {
            resp.putNull("id");
        }
        ObjectNode err = resp.putObject("error");
        err.put("code", code);
        err.put("message", message != null ? message : "error");
        return Optional.of(resp);
    }
}
