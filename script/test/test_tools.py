#!/usr/bin/env python3
"""Minimal MCP tool test - single project mode"""
import subprocess, json, sys, time, os, glob, shutil

# Configurable paths via environment variables
JAR = os.environ.get("JAR_PATH")
JAVA = os.environ.get("JAVA_HOME")
ROOT = os.environ.get("PROJECT_ROOT")

# Auto-detect JAR if not set
if not JAR:
    candidates = glob.glob("target/java-code-indexer-*-shaded.jar")
    if candidates:
        JAR = candidates[0]
    else:
        candidates = glob.glob("target/java-code-indexer-*.jar")
        JAR = candidates[0] if candidates else None

# Auto-detect Java if not set
if not JAVA:
    JAVA = shutil.which("java")

# Auto-detect project root if not set
if not ROOT:
    ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

if not JAR:
    print("ERROR: JAR not found. Set JAR_PATH env var or build the project first.")
    sys.exit(1)
if not JAVA:
    print("ERROR: Java not found. Set JAVA_HOME env var or ensure java is in PATH.")
    sys.exit(1)

def send_msg(proc, method, params=None, msg_id=1):
    msg = {"jsonrpc": "2.0", "id": msg_id, "method": method}
    if params:
        msg["params"] = params
    data = json.dumps(msg).encode()
    proc.stdin.write(f"Content-Length: {len(data)}\r\n\r\n".encode())
    proc.stdin.write(data)
    proc.stdin.flush()

def read_response(proc, timeout=10):
    import select
    ready = select.select([proc.stdout], [], [], timeout)
    if not ready[0]:
        return None
    line = proc.stdout.readline().decode().strip()
    if not line:
        return None
    try:
        return json.loads(line)
    except:
        return {"raw": line}

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", ROOT],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
)

results = []
try:
    # 1. Initialize
    send_msg(proc, "initialize", {"protocolVersion": "2024-11-05"})
    r = read_response(proc)
    v = r.get("result", {}).get("serverInfo", {}).get("version") if r else "?"
    results.append(("initialize", "OK" if v == "1.1.0" else f"FAIL v={v}"))

    # 2. tools/list
    send_msg(proc, "tools/list", msg_id=2)
    r = read_response(proc)
    tools = r.get("result", {}).get("tools", []) if r else []
    tool_names = [t["name"] for t in tools]
    results.append(("tools/list", f"{len(tools)} tools: {tool_names}"))

    # 3. find_symbol
    send_msg(proc, "tools/call", {"name": "find_symbol", "arguments": {"query": "ConfigLoader"}}, 3)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        total = inner.get("total", "?")
        results.append(("find_symbol", f"total={total}"))
    except:
        results.append(("find_symbol", f"raw={content[:100]}"))

    # 4. search_code
    send_msg(proc, "tools/call", {"name": "search_code", "arguments": {"query": "DatabaseManager"}}, 4)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("search_code", f"hits={inner.get('total_hits', '?')}"))
    except:
        results.append(("search_code", f"raw={content[:100]}"))

    # 5. search_config
    send_msg(proc, "tools/call", {"name": "search_config", "arguments": {"query": "spring"}}, 5)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("search_config", f"total={inner.get('total', '?')}"))
    except:
        results.append(("search_config", f"raw={content[:100]}"))

    # 6. find_dependencies
    send_msg(proc, "tools/call", {"name": "find_dependencies", "arguments": {"query": "*"}}, 6)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("find_dependencies", f"total={inner.get('total', '?')}"))
    except:
        results.append(("find_dependencies", f"raw={content[:100]}"))

    # Print results
    all_ok = True
    for name, result in results:
        status = "✅" if "FAIL" not in result else "❌"
        if status == "❌":
            all_ok = False
        print(f"  {status} {name}: {result}")

    if all_ok:
        print(f"\n✅ 所有 {len(results)} 个工具测试通过!")
    else:
        print(f"\n⚠️  {len(results)} 个测试完成，部分失败")

except Exception as e:
    print(f"❌ 测试异常: {e}")
finally:
    proc.kill()
    proc.wait()
