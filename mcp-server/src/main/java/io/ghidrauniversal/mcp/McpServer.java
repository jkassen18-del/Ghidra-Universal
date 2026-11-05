package io.ghidrauniversal.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.ghidrauniversal.core.Json;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Local MCP server over the HTTP+SSE transport, built on the JDK's
 * {@link HttpServer} (no web framework).
 *
 * <ul>
 *   <li>GET {@code ssePath} opens an event stream; the first event, {@code endpoint},
 *       tells the client where to POST.</li>
 *   <li>POST {@code messagePath?sessionId=…} carries a JSON-RPC request; the reply
 *       is pushed back over that session's stream.</li>
 * </ul>
 *
 * Bound to loopback by default so it is not reachable off the machine.
 */
public final class McpServer {

    private static final ObjectMapper M = Json.mapper();
    private static final long KEEPALIVE_MS = 15_000;

    private final McpServerConfig config;
    private final McpDispatcher dispatcher;
    private final Consumer<String> log;
    private final Map<String, SseSession> sessions = new ConcurrentHashMap<>();

    private HttpServer http;
    private int boundPort = -1;

    public McpServer(McpServerConfig config, McpDispatcher dispatcher, Consumer<String> log) {
        this.config = config;
        this.dispatcher = dispatcher;
        this.log = log != null ? log : l -> {};
    }

    public synchronized void start() throws IOException {
        if (http != null) {
            return;
        }
        http = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
        http.createContext(config.ssePath(), this::handleSse);
        http.createContext(config.messagePath(), this::handleMessage);
        http.setExecutor(Executors.newCachedThreadPool());
        http.start();
        boundPort = http.getAddress().getPort();
        log.accept("MCP server listening at " + sseUrl());
    }

    public synchronized void stop() {
        if (http != null) {
            sessions.values().forEach(SseSession::close);
            sessions.clear();
            http.stop(0);
            http = null;
            boundPort = -1;
        }
    }

    public int port() {
        return boundPort;
    }

    /** The URL an MCP client connects to (the SSE endpoint). */
    public String sseUrl() {
        return "http://" + config.host() + ":" + boundPort + config.ssePath();
    }

    // --- handlers ---

    private void handleSse(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            respondEmpty(ex, 405);
            return;
        }
        SseSession session = new SseSession(UUID.randomUUID().toString());
        sessions.put(session.id(), session);
        log.accept("SSE client connected: " + session.id());

        ex.getResponseHeaders().add("Content-Type", "text/event-stream");
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        ex.getResponseHeaders().add("Connection", "keep-alive");
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, 0); // 0 => chunked, open-ended

        try (OutputStream os = ex.getResponseBody()) {
            String endpoint = config.messagePath() + "?sessionId="
                    + URLEncoder.encode(session.id(), StandardCharsets.UTF_8);
            writeEvent(os, "endpoint", endpoint);
            while (session.isOpen()) {
                String msg = session.poll(KEEPALIVE_MS);
                if (msg != null) {
                    writeEvent(os, "message", msg);
                } else {
                    os.write(": keepalive\n\n".getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // client disconnected or server stopping
        } finally {
            sessions.remove(session.id());
            log.accept("SSE client disconnected: " + session.id());
        }
    }

    private void handleMessage(HttpExchange ex) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            respondEmpty(ex, 204);
            return;
        }
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            respondEmpty(ex, 405);
            return;
        }
        String sessionId = queryParam(ex, "sessionId");
        SseSession session = sessionId != null ? sessions.get(sessionId) : null;
        if (session == null) {
            respondText(ex, 404, "unknown or expired sessionId");
            return;
        }

        byte[] body = ex.getRequestBody().readAllBytes();
        try {
            JsonNode root = M.readTree(body);
            if (root.isArray()) {
                for (JsonNode req : root) {
                    dispatchToSession(session, req);
                }
            } else {
                dispatchToSession(session, root);
            }
            respondEmpty(ex, 202); // accepted; reply goes over the SSE stream
        } catch (Exception e) {
            respondText(ex, 400, "invalid JSON-RPC: " + e.getMessage());
        }
    }

    private void dispatchToSession(SseSession session, JsonNode request) {
        Optional<? extends JsonNode> response = dispatcher.handle(request);
        response.ifPresent(node -> session.enqueue(node.toString()));
    }

    // --- low-level helpers ---

    private static void writeEvent(OutputStream os, String event, String data) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("event: ").append(event).append('\n');
        // data may be multi-line; prefix each line with "data: "
        for (String line : data.split("\n", -1)) {
            sb.append("data: ").append(line).append('\n');
        }
        sb.append('\n');
        os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        os.flush();
    }

    private static String queryParam(HttpExchange ex, String key) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void respondEmpty(HttpExchange ex, int code) throws IOException {
        ex.sendResponseHeaders(code, -1);
        ex.close();
    }

    private static void respondText(HttpExchange ex, int code, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }
}
