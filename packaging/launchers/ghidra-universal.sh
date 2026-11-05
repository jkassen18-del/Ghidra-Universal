#!/usr/bin/env bash
# Portable launcher for Ghidra-Universal (Linux / macOS).
# Assumes the bundle layout described in packaging/README.md:
#   <bundle>/runtime, <bundle>/app, <bundle>/ghidra
set -euo pipefail

# Resolve the directory this script lives in (the bundle root), following symlinks.
SOURCE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE" ]; do
  DIR="$(cd -P "$(dirname "$SOURCE")" && pwd)"
  SOURCE="$(readlink "$SOURCE")"
  [[ "$SOURCE" != /* ]] && SOURCE="$DIR/$SOURCE"
done
BUNDLE="$(cd -P "$(dirname "$SOURCE")" && pwd)"

JAVA="$BUNDLE/runtime/bin/java"
[ -x "$JAVA" ] || JAVA="$(command -v java)"   # dev fallback: system java

export GHIDRA_INSTALL_DIR="${GHIDRA_INSTALL_DIR:-$BUNDLE/ghidra}"

exec "$JAVA" \
  -Dapp.home="$BUNDLE" \
  -cp "$BUNDLE/app/*" \
  io.ghidrauniversal.app.Launcher "$@"
