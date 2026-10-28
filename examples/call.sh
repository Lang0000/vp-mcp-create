#!/usr/bin/env bash
# Calls one MCP tool of the running plugin with curl.
#   examples/call.sh create_diagram examples/class.json
#   examples/call.sh list_diagrams '{}'
#   PORT=8899 examples/call.sh get_diagram_by_url '{"vpp_url":"<id>"}'   # the read-only vpmcp.server
set -euo pipefail
TOOL="$1"
ARGS="${2:-{\}}"
if [[ -f "$ARGS" ]]; then ARGS="$(cat "$ARGS")"; fi
PORT="${PORT:-8900}"
python3 - "$TOOL" "$ARGS" "$PORT" <<'EOF'
import json, sys, urllib.request
tool, args, port = sys.argv[1], json.loads(sys.argv[2]), sys.argv[3]
body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": "tools/call",
                   "params": {"name": tool, "arguments": args}}).encode("utf-8")
req = urllib.request.Request(f"http://127.0.0.1:{port}/mcp", body,
                             {"Content-Type": "application/json", "Accept": "application/json, text/event-stream"})
res = json.load(urllib.request.urlopen(req, timeout=300))
if "error" in res:
    print(json.dumps(res["error"], ensure_ascii=False, indent=2)); sys.exit(1)
r = res["result"]
print(r["content"][0]["text"])
sys.exit(1 if r.get("isError") else 0)
EOF
