#!/usr/bin/env bash
# Portable launcher for Ghidra-Universal (Linux / macOS).
# Bundle layout (see packaging/README.md):
#   <bundle>/runtime   bundled JDK (runs the app AND Ghidra)
#   <bundle>/app/lib   application + dependency jars
#   <bundle>/app/javafx JavaFX module jars (this OS)
#   <bundle>/ghidra    bundled Ghidra install
set -euo pipefail

SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$DIR/$SOURCE"
done
BUNDLE="$(cd -P "$(dirname "$SOURCE")" && pwd)"

JAVA="$BUNDLE/runtime/bin/java"
[ -x "$JAVA" ] || JAVA="$(command -v java)"   # dev fallback

export GHIDRA_INSTALL_DIR="${GHIDRA_INSTALL_DIR:-$BUNDLE/ghidra}"

exec "$JAVA" \
  -Dapp.home="$BUNDLE" \
  --module-path "$BUNDLE/app/javafx" \
  --add-modules javafx.controls,javafx.graphics,javafx.base \
  -cp "$BUNDLE/app/lib/*" \
  io.ghidrauniversal.app.Launcher "$@"
