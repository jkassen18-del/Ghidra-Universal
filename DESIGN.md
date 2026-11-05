# Ghidra-Universal — Design

A portable, cross-platform reverse-engineering tool that extracts program
structure from a binary using [Ghidra](https://github.com/NationalSecurityAgency/ghidra)
and exposes that structure to **any MCP client** (e.g. the user's own Claude
CLI / Claude Desktop) so an AI — or the user by hand — can understand and
reconstruct the program.

The tool never ships its own API keys or AI provider. It uses *your* existing
AI infrastructure by speaking the Model Context Protocol (MCP). Point your MCP
client at the tool's local server and your AI gains read/annotate access to the
analyzed binary.

---

## Goals

- **Drop in a binary → get rich, structured analysis** (functions, decompiled
  C, disassembly, strings, imports/exports, symbols, xrefs, call graph).
- **Expose that analysis over MCP** so the user's own AI can read it and assist
  with reverse engineering and AI-assisted source reconstruction.
- **Let the AI write understanding back** into the project (rename functions,
  add comments, retype variables).
- **Portable & cross-platform** — Windows, Linux, macOS — as a self-contained
  bundle that needs no system Java install.
- **Bring-your-own AI** — zero credentials in this tool.

## Non-goals (v1)

- Guaranteed recompilable output. "Recreate" means **AI-assisted source
  reconstruction**: readable, commented C/pseudocode plus a described rebuild,
  human-guided, not guaranteed to compile.
- A tiny single-file `.exe`. Ghidra requires a JVM and native decompiler, so the
  portable artifact is a self-contained folder/bundle (~300–500 MB), launched by
  a thin native stub.
- Building Ghidra from source. v1 bundles **official Ghidra release binaries**
  (Apache-2.0 licensed, redistributable).

---

## Decisions (locked)

| Area | Choice | Rationale |
|------|--------|-----------|
| Interface | **Desktop GUI** | Real app feel; not a web service. |
| GUI stack | **JavaFX** | Reuses the JRE already bundled for Ghidra — no second runtime, smallest footprint. |
| AI wiring | **MCP server only (BYO client)** | Uses the user's existing AI; no keys in the tool. |
| Platforms | **Cross-platform** (Win/Linux/mac) | "Anyone can use." |
| Ghidra source | **Official release binaries** | Reliable, reproducible, fast to iterate. (Pluggable user-pointed install later.) |
| MCP transport | **Local HTTP/SSE server** | A running GUI holds a live session any MCP client can attach to by URL. |
| Ghidra invocation | **Headless subprocess** (`analyzeHeadless`) | Robust, isolated, matches official usage. In-process mode is a later phase. |
| Recreate scope | **AI-assisted source reconstruction** | Realistic and genuinely useful for v1. |

---

## Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Single JVM  (bundled JRE — shared by GUI + server)        │
│                                                            │
│   JavaFX GUI ─────┐                                        │
│                   ├──►  Core                               │
│   MCP Server ─────┘      • project store (persistent)      │
│   (HTTP/SSE, localhost)  • job runner                      │
│                          • JSON models                     │
│                                   │                        │
│                                   ▼                        │
│                   Ghidra engine layer                      │
│              spawns  analyzeHeadless  with:                │
│                • export script  → analysis JSON            │
│                • write script   → mutate saved project     │
│                   ⇅ persistent Ghidra project on disk      │
└──────────────────────────────────────────────────────────┘
      ▲ user drops a binary into the GUI
      ▲ user's Claude CLI / Desktop connects to the MCP URL
```

### Why persistent project
Write-back tools can't work with a fire-and-exit subprocess. The tool keeps a
**persistent Ghidra project** per analyzed binary:
- **Reads** run an export script that dumps requested data as JSON.
- **Writes** run a small headless script that opens the saved project, applies
  the mutation, and re-saves.

Each write re-spawns Ghidra (acceptable latency for v1); the future in-process
mode removes that cost.

---

## MCP tool surface (v1)

**Read**
- `list_functions` — name, address, size, signature
- `get_decompiled_c(function)` — Ghidra decompiler output
- `get_disassembly(function)` — instruction listing
- `get_strings` — defined strings + locations
- `get_imports` / `get_exports`
- `get_symbols`
- `get_xrefs(address)` — references to/from
- `get_call_graph` — caller/callee edges

**Write (annotate)**
- `rename_function(address, new_name)`
- `set_comment(address, comment)`
- `retype_variable(function, var, type)`

**Meta**
- `get_project_info` — binary metadata, analysis status
- `search(query)` — symbols / strings

---

## Build phases

1. **Scaffolding** — repo layout, Gradle build, JRE + Ghidra bundling strategy,
   thin launcher stubs for Win/Linux/mac.
2. **Ghidra engine layer** — headless invocation, export script → JSON,
   persistent project lifecycle.
3. **MCP server** — HTTP/SSE endpoint; read tools first, then write tools.
4. **JavaFX GUI** — drop-in binary, function browser, decompiled-C view,
   MCP status/URL panel.
5. **Packaging** — portable cross-platform bundles + docs for registering the
   MCP URL in a Claude client.

---

## Licensing

- **Ghidra** — Apache License 2.0; redistributable when the NOTICE/LICENSE are
  carried along. We bundle official release binaries unmodified.
- **Bundled JRE** — a build from an OpenJDK distribution under GPLv2+Classpath
  Exception (or equivalent); carry its license.
- **Ghidra-Universal** — license TBD (default intent: open source so "anyone can
  use" it).

## Repository layout (planned)

```
Ghidra-Universal/
├── DESIGN.md
├── README.md
├── settings.gradle(.kts)
├── gradle/                     # wrapper
├── buildSrc/ or build logic    # bundling tasks (fetch Ghidra, jlink JRE)
├── app/                        # JavaFX GUI + bootstrap
│   └── src/main/java/...
├── core/                       # project store, models, job runner
├── engine-ghidra/              # headless invocation + scripts
│   └── ghidra_scripts/         # export.java / write.java (run inside Ghidra)
├── mcp-server/                 # HTTP/SSE MCP implementation + tool handlers
└── packaging/                  # per-OS launcher stubs, jpackage/jlink config
```
