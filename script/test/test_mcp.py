#!/usr/bin/env python3
"""MCP Server end-to-end test (CLI-based, no index_project tool)."""
import subprocess, sys, json, time, os, threading

JAR = "/home/ubuntu/jairouter/mcp/java-code-indexer/target/java-code-indexer-1.0.0-SNAPSHOT.jar"
JAVA = "/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
PROJECT_ROOT = "/home/ubuntu/jairouter/mcp/java-code-indexer"


def make_frame(obj):
    """Encode a JSON-RPC object as a raw JSON line (MCP stdio spec)."""
    return json.dumps(obj, ensure_ascii=False) + "\n"


def read_response(proc):
    """Read one response line (raw JSON + newline) from proc.stdout."""
    buf = b""
    while True:
        ch = os.read(proc.stdout.fileno(), 1)
        if not ch:
            return None
        if ch == b"\n":
            break
        buf += ch
    line = buf.decode("utf-8").strip()
    if not line:
        return None
    return json.loads(line)


def send(proc, obj):
    """Send a raw JSON line MCP request/notification."""
    data = make_frame(obj)
    os.write(proc.stdin.fileno(), data.encode("utf-8"))


def call_tool(proc, name, arguments, req_id):
    send(proc, {"jsonrpc": "2.0", "id": req_id, "method": "tools/call",
                "params": {"name": name, "arguments": arguments}})
    resp = read_response(proc)
    if not resp:
        return None, "no response"
    if "error" in resp:
        return None, str(resp["error"])
    content = resp.get("result", {}).get("content", [])
    if content:
        return json.loads(content[0].get("text", "{}")), None
    return None, "empty content"


def main():
    print("=" * 60)
    print("MCP Server End-to-End Test")
    print("=" * 60)

    proc = subprocess.Popen(
        [JAVA, "-jar", JAR, "--project-root", PROJECT_ROOT],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )

    errors = []
    req_id = 0

    def next_id():
        nonlocal req_id
        req_id += 1
        return req_id

    # Drain stderr in background
    def drain_stderr():
        while True:
            data = os.read(proc.stderr.fileno(), 4096)
            if not data:
                break
    threading.Thread(target=drain_stderr, daemon=True).start()

    # 1. Initialize
    print("\n[1] initialize")
    send(proc, {"jsonrpc": "2.0", "id": next_id(), "method": "initialize",
                "params": {"protocolVersion": "2024-11-05", "capabilities": {}}})
    resp = read_response(proc)
    server_info = resp.get("result", {}).get("serverInfo", {}) if resp else {}
    server_name = server_info.get("name")
    print(f"    serverName: {server_name}")
    print(f"    protocolVersion: {resp.get('result', {}).get('protocolVersion') if resp else 'N/A'}")
    if server_name != "java-code-indexer":
        errors.append("initialize: wrong serverName")

    # 2. Send initialized notification (no id)
    print("\n[2] notifications/initialized")
    send(proc, {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}})
    time.sleep(0.3)
    print("    (notification sent, no response expected)")

    # 3. tools/list
    print("\n[3] tools/list")
    send(proc, {"jsonrpc": "2.0", "id": next_id(), "method": "tools/list", "params": {}})
    resp = read_response(proc)
    tools = resp.get("result", {}).get("tools", []) if resp else []
    tool_names = [t["name"] for t in tools]
    print(f"    tools: {tool_names}")
    expected = ["find_symbol", "find_references", "get_call_graph",
                "search_code", "get_file_info", "semantic_search"]
    for name in expected:
        if name not in tool_names:
            errors.append(f"tools/list: missing tool '{name}'")

    # 4. find_symbol
    print("\n[4] find_symbol (query='McpServer')")
    result, err = call_tool(proc, "find_symbol", {"query": "McpServer", "limit": 5}, next_id())
    if result:
        print(f"    total={result.get('total')}")
        for s in result.get("symbols", [])[:5]:
            print(f"      - [{s['id']}] {s['name']} ({s['kind']}) @ {s['file']}:{s['line']}")
    else:
        print(f"    ERROR: {err}")
        errors.append(f"find_symbol: {err}")

    # 5. find_references
    if result and result.get("symbols"):
        sym_id = result["symbols"][0]["id"]
        print(f"\n[5] find_references (symbol_id={sym_id} = {result['symbols'][0]['name']})")
        ref_result, err = call_tool(proc, "find_references", {"symbol_id": sym_id}, next_id())
        if ref_result:
            print(f"    total={ref_result.get('total')}")
            for r in ref_result.get("references", [])[:5]:
                print(f"      - {r['from_file']}:{r['from_line']}  {r.get('context', '')[:60]}")
        else:
            print(f"    ERROR: {err}")
            errors.append(f"find_references: {err}")
    else:
        print("\n[5] find_references SKIPPED (no symbol found)")

    # 6. get_call_graph
    print("\n[6] get_call_graph (McpServer.start)")
    result, err = call_tool(proc, "get_call_graph",
                            {"method_name": "com.sodlinken.jindexer.mcp.McpServer.start",
                             "direction": "both"}, next_id())
    if result:
        print(f"    callers={len(result.get('callers', []))}, callees={len(result.get('callees', []))}")
        for c in result.get("callers", [])[:3]:
            print(f"      caller: {c['method']} @ {c['file']}:{c['line']}")
        for c in result.get("callees", [])[:3]:
            print(f"      callee: {c['method']} @ {c.get('file', '?')}:{c['line']}")
    else:
        print(f"    ERROR: {err}")
        errors.append(f"get_call_graph: {err}")

    # 7. search_code
    print("\n[7] search_code (query='DataInputStream')")
    result, err = call_tool(proc, "search_code", {"query": "DataInputStream", "limit": 5}, next_id())
    if result:
        print(f"    total_hits={result.get('total_hits')}, query_time={result.get('query_time_ms')}ms")
        for s in result.get("symbols", [])[:3]:
            print(f"      symbol: {s['name']} ({s['kind']}) @ {s['file']}:{s['line']}")
        for c in result.get("chunks", [])[:3]:
            print(f"      chunk: {c['name']} ({c['type']}) @ {c['file']}:{c['line']}")
    else:
        print(f"    ERROR: {err}")
        errors.append(f"search_code: {err}")

    # 8. get_file_info
    print("\n[8] get_file_info (McpServer.java)")
    result, err = call_tool(proc, "get_file_info",
                            {"file_path": "src/main/java/com/sodlinken/jindexer/mcp/McpServer.java"},
                            next_id())
    if result:
        print(f"    symbols={result.get('symbol_count')}, chunks={result.get('chunk_count')}")
        for s in result.get("symbols", [])[:5]:
            print(f"      symbol: {s['name']} ({s['kind']}) @ line {s['line']}")
    else:
        print(f"    ERROR: {err}")
        errors.append(f"get_file_info: {err}")

    # 9. semantic_search (expect placeholder)
    print("\n[9] semantic_search (expect placeholder)")
    result, err = call_tool(proc, "semantic_search", {"query": "test"}, next_id())
    if result and result.get("error"):
        print(f"    placeholder: {result['error'][:60]}")
    else:
        print(f"    ERROR: unexpected response: {result}")
        errors.append("semantic_search: unexpected response")

    # Shutdown
    try:
        os.close(proc.stdin.fileno())
    except Exception:
        pass
    try:
        proc.wait(timeout=5)
    except Exception:
        proc.kill()

    # Summary
    print("\n" + "=" * 60)
    if errors:
        print(f"FAILED: {len(errors)} error(s)")
        for e in errors:
            print(f"  ✗ {e}")
        sys.exit(1)
    else:
        print("ALL 6 TOOLS PASSED ✓")
        sys.exit(0)


if __name__ == "__main__":
    main()
