package io.ghidrauniversal.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** A single MCP tool: a name, a description, an input JSON Schema, and a handler. */
public interface McpTool {

    String name();

    String description();

    /** JSON Schema (an object with {@code type:"object"}, {@code properties}, …). */
    ObjectNode inputSchema();

    /**
     * Execute the tool.
     *
     * @param arguments the client-supplied arguments object (never null)
     * @return text to return as the tool result (typically JSON)
     * @throws Exception any failure; the transport reports it as an MCP tool error
     */
    String call(JsonNode arguments) throws Exception;
}
