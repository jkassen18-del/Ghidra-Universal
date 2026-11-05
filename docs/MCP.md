# Connecting your AI to Ghidra-Universal (MCP)

Ghidra-Universal runs a local **MCP server** over the HTTP + SSE transport. It
holds no API keys and calls no AI provider — you connect *your* MCP client to
it. The server is bound to loopback (`127.0.0.1`) so it is not reachable off
your machine.

## Start the server

From the portable bundle (or a dev checkout), analyze a binary and serve it:

```bash
ghidra-universal serve /path/to/target.bin
```

It prints the SSE URL, e.g.:

```
MCP server ready. Connect your AI client to this SSE URL:
    http://127.0.0.1:53421/sse
```

(The GUI, from Phase 4, starts this server for the binary you load and shows the
same URL.)

## Register it with a client

### Claude CLI

```bash
claude mcp add --transport sse ghidra-universal http://127.0.0.1:53421/sse
```

Then, in a session, Claude can call the tools below. Example prompts:

- "List the functions, then show me the decompiled C for `main`."
- "Trace the call graph from `main` and summarize what the program does."
- "Rename `FUN_00101149` to `parse_header` and add a comment explaining it."

### Claude Desktop

Add an SSE server entry pointing at the printed URL in your MCP server settings.

### Any other MCP client

Point it at the SSE URL. The server implements MCP protocol `2024-11-05`:
`initialize`, `tools/list`, `tools/call`, `ping`.

## Tool surface

**Read**

| Tool | Arguments | Returns |
|------|-----------|---------|
| `get_project_info` | — | program metadata (format, processor, image base, SHA-256, function count) |
| `list_functions` | `query?`, `limit?` | functions (name, address, size, signature) |
| `get_decompiled_c` | `function` | decompiled C for a function (by name or address) |
| `get_disassembly` | `function` | instruction listing for a function |
| `get_strings` | `query?` | defined strings and addresses |
| `get_imports` | — | imported/external symbols |
| `get_exports` | — | exported symbols / entry points |
| `get_symbols` | `query?` | symbol table |
| `get_xrefs` | `address` | references to/from an address |
| `get_call_graph` | `function?` | caller/callee edges |
| `search` | `query` | matching functions, symbols and strings |

**Write-back (annotate the Ghidra project)**

| Tool | Arguments |
|------|-----------|
| `rename_function` | `address`, `newName` |
| `set_comment` | `address`, `comment` |
| `retype_variable` | `function`, `variable`, `type` |

Write-back is applied to a persistent Ghidra project and the analysis cache is
refreshed, so subsequent reads reflect the change. Each write re-runs Ghidra
headlessly, so expect a short delay.

## Notes

- The server exposes whichever binary is currently open. Analyzing a new binary
  changes what the tools return.
- Results are returned as JSON text in the tool result content.
- Because the transport is local HTTP/SSE, the server must be running while the
  client is connected.
