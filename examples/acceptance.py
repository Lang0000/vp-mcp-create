#!/usr/bin/env python3
"""Acceptance check against a running Visual Paradigm.

For every example diagram: create_diagram on :8900, read it back with get_diagram_by_url of vpmcp.server on
:8899 and compare names, node and edge counts, then export a PNG. Finally sends a few bad requests.

    python3 examples/acceptance.py /absolute/output/dir [--keep]

Without --keep the test diagrams are deleted again (with their model elements).
"""
import json
import os
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
EXAMPLES = ["class", "usecase", "activity", "deployment", "sequence", "component", "flowchart"]
REQUIRED_TOOLS = {"create_diagram", "add_elements", "update_element", "delete_element", "layout_diagram",
                  "export_diagram", "list_diagrams", "save_project"}


def rpc(port, method, params=None):
    body = json.dumps({"jsonrpc": "2.0", "id": 1, "method": method, "params": params or {}}).encode("utf-8")
    req = urllib.request.Request(f"http://127.0.0.1:{port}/mcp", body,
                                 {"Content-Type": "application/json",
                                  "Accept": "application/json, text/event-stream"})
    return json.load(urllib.request.urlopen(req, timeout=300))


def call(port, tool, args):
    res = rpc(port, "tools/call", {"name": tool, "arguments": args})
    if "error" in res:
        return None, res["error"]["message"]
    r = res["result"]
    text = r["content"][0]["text"]
    if r.get("isError"):
        return None, text
    return json.loads(text), None


def main():
    if len(sys.argv) < 2 or not os.path.isabs(sys.argv[1]):
        print(__doc__)
        sys.exit(2)
    out_dir, keep = sys.argv[1], "--keep" in sys.argv
    failures = 0

    def check(ok, what):
        nonlocal failures
        print(("ok    " if ok else "FAIL  ") + what)
        failures += 0 if ok else 1

    tools = {t["name"] for t in rpc(8900, "tools/list")["result"]["tools"]}
    check(REQUIRED_TOOLS <= tools, "tools/list has the eight tools")

    created = []
    for name in EXAMPLES:
        spec = json.load(open(os.path.join(HERE, name + ".json"), encoding="utf-8"))
        res, err = call(8900, "create_diagram", spec)
        if err:
            check(False, f"{name}: create_diagram: {err}")
            continue
        d = res["diagram"]
        created.append(d["id"])
        check(not [w for w in res["warnings"] if not w.startswith("node ")], f"{name}: no warnings {res['warnings']}")
        got, err = call(8899, "get_diagram_by_url", {"vpp_url": d["url"]})
        if err:
            check(False, f"{name}: get_diagram_by_url: {err}")
        else:
            # partitions are drawn as one swimlane with headers, so compare the named non-partition nodes
            want = sorted(n.get("name", "") for n in spec["nodes"] if n["kind"] not in ("partition", "lane"))
            have = [n["name"] or "" for n in got["nodes"]
                    if n["shape_type"] not in ("ActivitySwimlane2", "ActivityPartitionHeader",
                                               "ActivitySwimlane2Compartment")]
            missing = [w for w in want if w and w not in have]
            check(not missing, f"{name}: round trip finds every node name {missing or ''}")
            check(len(got["edges"]) == len(spec.get("edges", [])),
                  f"{name}: round trip edges {len(got['edges'])} == {len(spec.get('edges', []))}")
        exp, err = call(8900, "export_diagram", {"diagram": d["id"], "directory": out_dir, "file_name": name,
                                                 "scale": 1.5})
        check(err is None and exp["bytes"] > 1000, f"{name}: PNG {exp['path'] if exp else err}")

    for args, what in [({"type": "nope", "name": "x"}, "unknown diagram type"),
                       ({"type": "class", "name": "x", "nodes": [{"id": "a", "kind": "class"}],
                         "edges": [{"from": "a", "to": "b"}]}, "edge to a missing node"),
                       ({"type": "class", "name": "x", "nodes": [{"kind": "class", "style": {"fill": "#1"}}]},
                        "bad colour")]:
        res, err = call(8900, "create_diagram", args)
        check(res is None and err and err.startswith("Error:"), f"bad request ({what}) -> {err}")

    if not keep:
        for did in created:
            call(8900, "delete_element", {"diagram": did, "delete_diagram": True, "from_model": True})
    print("ACCEPTANCE: OK" if failures == 0 else f"ACCEPTANCE: {failures} FAILURE(S)")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
