# Packaging (portable, cross-platform)

A **self-contained, portable bundle per OS** that a user unzips and runs — no
system Java install required. Built by the Gradle `bundle` / `portableZip`
tasks (`gradle/packaging.gradle`).

## Build

```bash
./gradlew fetchGhidra     # once: download + verify + extract the pinned Ghidra
./gradlew bundle          # assemble dist/ghidra-universal/
./gradlew portableZip     # -> dist/ghidra-universal-<os>.zip
```

## Bundle layout

```
ghidra-universal/                 (the portable folder the user unzips)
├── runtime/                      bundled JDK — runs the app AND Ghidra
├── app/
│   ├── lib/                      application + dependency jars (classpath)
│   └── javafx/                   JavaFX module jars for this OS (module path)
├── ghidra/                       the bundled Ghidra install
├── licenses/                     Ghidra + runtime license notices
├── ghidra-universal.sh           launcher (Linux/macOS)
└── ghidra-universal.bat          launcher (Windows)
```

Run `./ghidra-universal.sh` (GUI) or `./ghidra-universal.sh serve <binary>`
(headless MCP). The launcher points `GHIDRA_INSTALL_DIR` at the bundled
`ghidra/` and runs on the bundled `runtime/`; `GhidraInstall.discover()` also
finds the bundled copy automatically.

## Why a full JDK as the runtime

Ghidra compiles its analysis scripts **at runtime**, so the bundled runtime must
include the Java compiler (`jdk.compiler`). Rather than curate a `jlink` module
set broad enough for all of Ghidra, the bundle ships a full JDK (minus
build-only dirs: `jmods`, `demo`, `man`, `include`, `sample`). JavaFX rides on
the module path from `app/javafx/`.

- Ghidra 11.4.3 → **JDK 21**. (Ghidra 12.x would require JDK 25.)
- Approx size: ~1 GB (Ghidra ~765 MB, runtime ~205 MB, app ~10 MB).
- *Future optimization:* a `jlink` runtime including `jdk.compiler` + the modules
  Ghidra headless actually needs would cut the runtime substantially.

On Unix the runtime is copied with `cp -a` so dangling symlinks in the JDK
(e.g. the optional `libatk-wrapper.so` GTK shim) are preserved rather than
followed.

## Cross-platform

`bundle` runs identically on each OS and copies **that OS's** JDK and JavaFX
jars, so run it on each target (Windows, Linux, macOS) — a CI matrix with a
job per OS, each doing `fetchGhidra` + `portableZip`, produces the three
bundles. `portableZip` names its output `ghidra-universal-<version>-<os>.zip`.

## Why not a single tiny .exe

Ghidra requires a JVM and a native decompiler; the JRE + Ghidra dominate the
size. The deliverable is a portable *folder* launched by a thin stub — not a
5 MB single file.
