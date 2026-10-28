#!/usr/bin/env bash
# Builds vpmcp.create.jar and installs it, with plugin.xml and the icon, into Visual Paradigm's plugin directory.
#
#   ./build.sh            build + install
#   ./build.sh --check    also run the offline checks (layout, parser, HTTP transport)
#   ./build.sh --no-install
#
# Environment overrides:
#   VP_HOME     Visual Paradigm app bundle      (default: /Applications/Visual Paradigm CE.app)
#   VP_PLUGINS  plugin directory               (default: ~/Library/Application Support/VisualParadigm/plugins)
#   JAVA_HOME   JDK 11+ used to run Maven       (default: Homebrew openjdk@17 if present)
set -euo pipefail

cd "$(dirname "$0")"

VP_HOME="${VP_HOME:-/Applications/Visual Paradigm CE.app}"
VP_PLUGINS="${VP_PLUGINS:-$HOME/Library/Application Support/VisualParadigm/plugins}"
OPENAPI="$VP_HOME/Contents/Resources/app/lib/openapi.jar"
DEST="$VP_PLUGINS/vpmcp.create"

CHECK=0
INSTALL=1
for a in "$@"; do
  case "$a" in
    --check) CHECK=1 ;;
    --no-install) INSTALL=0 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown option: $a" >&2; exit 2 ;;
  esac
done

if [[ ! -f "$OPENAPI" ]]; then
  echo "openapi.jar not found at: $OPENAPI" >&2
  echo "Set VP_HOME to your Visual Paradigm .app bundle." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
  for j in /opt/homebrew/opt/openjdk@17 /opt/homebrew/opt/openjdk /usr/local/opt/openjdk@17; do
    if [[ -x "$j/bin/javac" ]]; then export JAVA_HOME="$j"; break; fi
  done
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  export JAVA_HOME
fi
echo "JDK      : ${JAVA_HOME:-<system>}"

# offline first (everything cached); otherwise let Maven download what is missing once
if ! mvn -q -o -Dvp.home="$VP_HOME" clean package 2>/dev/null; then
  mvn -q -Dvp.home="$VP_HOME" clean package
fi
JAR="target/vpmcp.create.jar"
echo "Built    : $JAR ($(wc -c < "$JAR" | tr -d ' ') bytes)"

if [[ $CHECK -eq 1 ]]; then
  GSON="$(ls "$HOME"/.m2/repository/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar)"
  mkdir -p target/test-classes
  "$JAVA_HOME/bin/javac" --release 11 -encoding UTF-8 -d target/test-classes \
    -cp "target/classes:$GSON:$OPENAPI" src/test/java/vpmcp/create/OfflineCheck.java
  "$JAVA_HOME/bin/java" -Djava.awt.headless=true -cp "target/test-classes:target/classes:$GSON:$OPENAPI" \
    vpmcp.create.OfflineCheck
fi

if [[ $INSTALL -eq 1 ]]; then
  mkdir -p "$DEST/lib" "$DEST/icons"
  cp "$JAR" "$DEST/lib/vpmcp.create.jar"
  cp plugin.xml "$DEST/plugin.xml"
  cp icons/mcp-create.png "$DEST/icons/mcp-create.png"
  echo "Installed: $DEST"
  if curl -s -m 2 -o /dev/null -X POST http://127.0.0.1:8900/mcp \
      -H 'Content-Type: application/json' -d '{"jsonrpc":"2.0","id":1,"method":"ping"}'; then
    echo "The server on :8900 is running the previous build; restart Visual Paradigm to load this one."
  else
    echo "Restart Visual Paradigm, then check Tools > MCP Create Server (status RUNNING)."
  fi
fi
