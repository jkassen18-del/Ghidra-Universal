# Ghidra-Universal

A portable, cross-platform reverse-engineering tool. Drop in a binary, and
Ghidra-Universal extracts its structure (functions, decompiled C, disassembly,
strings, imports/exports, symbols, cross-references, call graph) and exposes it
over the **Model Context Protocol (MCP)** so **your own AI** — Claude CLI/Desktop
or any MCP client — can help you understand and reconstruct the program. Or work
through it by hand.

**Bring your own AI.** The tool ships no API keys and talks to no AI provider
itself. It runs a local MCP server; you point your existing AI client at it.

> Status: early development. See [`DESIGN.md`](DESIGN.md) for the architecture
> and the phased build plan.

## How it works

```
binary ─▶ Ghidra (headless) ─▶ structured analysis ─▶ MCP server ─▶ your AI client
                                                                 └▶ you (GUI)
```

- **GUI:** JavaFX desktop app (reuses the bundled Java runtime).
- **Engine:** Ghidra's headless analyzer, run as a subprocess against a
  persistent project (so AI annotations can be written back).
- **AI wiring:** a local HTTP/SSE MCP server — no credentials in this tool.
- **Portable:** self-contained per-OS bundle (Windows/Linux/macOS); no system
  Java install needed.

## Building (developers)

Requires JDK 21 (matches the pinned Ghidra 11.4.3).

```bash
./gradlew build          # compile all modules
./gradlew fetchGhidra    # download + verify + extract pinned Ghidra into third_party/
export GHIDRA_INSTALL_DIR=$PWD/third_party/ghidra/ghidra_11.4.3_PUBLIC

./gradlew :app:run       # launch the JavaFX GUI
```

`fetchGhidra` pins the Ghidra release and its SHA-256 in `gradle.properties`.

### Using it

- **GUI:** open a binary; it is analyzed and the function list, decompiled C and
  disassembly populate. The local MCP server starts automatically and its URL is
  shown in the toolbar. Right-click a function to rename or comment (write-back).
- **Headless:** `./gradlew :app:run --args="serve /path/to/binary"` analyzes a
  binary and runs just the MCP server, printing the SSE URL.
- **Connect your AI:** see [`docs/MCP.md`](docs/MCP.md).

## Modules

| Module | Purpose |
|--------|---------|
| `core` | Shared JSON models, persistent-project store |
| `engine-ghidra` | Headless Ghidra invocation + export/write scripts |
| `mcp-server` | Local HTTP/SSE MCP server exposing the engine |
| `app` | Application bootstrap and (Phase 4) the JavaFX GUI |

## Licensing

Bundles official **Ghidra** release binaries (Apache-2.0) and an OpenJDK-based
runtime; their license notices travel with every bundle. Project license TBD.
