package io.ghidrauniversal.mcp;

/**
 * Configuration for the local MCP server.
 *
 * @param host        bind address; defaults to loopback so the server is not
 *                    exposed off the machine
 * @param port        TCP port; 0 lets the OS choose a free port
 * @param ssePath     path of the SSE event stream endpoint
 * @param messagePath path clients POST JSON-RPC messages to
 */
public record McpServerConfig(String host, int port, String ssePath, String messagePath) {

    public static McpServerConfig defaults() {
        return new McpServerConfig("127.0.0.1", 0, "/sse", "/message");
    }

    public McpServerConfig withPort(int newPort) {
        return new McpServerConfig(host, newPort, ssePath, messagePath);
    }
}
