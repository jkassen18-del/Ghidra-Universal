# Packaging (portable, cross-platform)

Goal: a **self-contained, portable bundle per OS** that a user unzips and runs,
with no system Java install required. (Full implementation lands in Phase 5;
this describes the strategy and the layout the launcher stubs assume.)

## What a bundle contains

```
ghidra-universal/                 (the portable folder the user unzips)
├── runtime/                      a trimmed JRE (jlink) — runs app + Ghidra
├── app/                          Ghidra-Universal jars (core, engine, mcp, app)
├── ghidra/                       the bundled Ghidra install (ghidra_<ver>_PUBLIC)
├── licenses/                     Ghidra, OpenJDK, third-party notices
├── ghidra-universal              launcher (Linux/macOS)   → packaging/launchers
└── ghidra-universal.bat          launcher (Windows)       → packaging/launchers
```

The launcher points `GHIDRA_INSTALL_DIR` at the bundled `ghidra/` and runs the
app on the bundled `runtime/`. `GhidraInstall.discover()` also finds the bundled
copy automatically when the app's directory contains `ghidra/`.

## How bundles are built (Phase 5)

1. `./gradlew fetchGhidra` — download + verify + extract the pinned Ghidra
   (see `gradle.properties`) into `third_party/`.
2. **Runtime** — produce a trimmed JRE with `jlink` (or a full `jpackage` image).
   Because Ghidra needs a *full* JDK feature set for its launcher, the bundled
   runtime is a JDK-class image, not a minimal module set.
   - Ghidra 11.4.3 → **JDK 21**. (Ghidra 12.x would require JDK 25.)
3. **App image** — assemble the application jars + the runtime via `jpackage`
   (`--type app-image`) for a double-clickable layout per OS.
4. **Compose** — drop `third_party/ghidra/ghidra_<ver>_PUBLIC` into `ghidra/`,
   copy license files, add the launcher stub, zip the folder.

### Cross-platform note
`jpackage` and `jlink` produce images for the OS they run on. Cross-platform
bundles are therefore built on (or via CI runners for) each target OS:
Windows, Linux, macOS. The Gradle tasks are written to run identically on each;
CI matrix wiring is part of Phase 5.

## Why not a single tiny .exe
Ghidra requires a JVM and a native decompiler; the JRE + Ghidra dominate the
size (~hundreds of MB). The deliverable is a portable *folder* launched by a
thin native stub — not a 5 MB single file.
