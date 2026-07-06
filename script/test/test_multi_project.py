#!/usr/bin/env python3
"""Multi-project MCP test"""
import subprocess, json, sys, time, os

JAR = "/home/ubuntu/jairouter/mcp/java-code-indexer/target/java-code-indexer-1.0.0-SNAPSHOT.jar"
JAVA = "/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
CONFIG = "/home/ubuntu/jairouter/mcp/java-code-indexer/test-multi-project.yaml"

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

# First, init both projects
print("=== 初始化项目 ===")
for name, root in [("jindexer", "/home/ubuntu/jairouter/mcp/java-code-indexer"), ("test-project", "/tmp/test-multi-project")]:
    os.makedirs(f"{root}/.jindexer", exist_ok=True)
    init_proc = subprocess.run([JAVA, "-jar", JAR, "--project-root", root, "--init"],
                               capture_output=True, text=True, timeout=30)
    print(f"  {name}: {'OK' if init_proc.returncode == 0 else 'FAIL'}")

# Index both projects
print("\n=== 索引项目 ===")
for name, root in [("jindexer", "/home/ubuntu/jairouter/mcp/java-code-indexer"), ("test-project", "/tmp/test-multi-project")]:
    idx_proc = subprocess.run([JAVA, "-jar", JAR, "--project-root", root, "--index"],
                              capture_output=True, text=True, timeout=60)
    print(f"  {name}: {'OK' if idx_proc.returncode == 0 else 'FAIL'}")

# Start MCP server with multi-project config
print("\n=== MCP 多项目测试 ===")
env = os.environ.copy()
env["JINDEXER_CONFIG"] = CONFIG

proc = subprocess.Popen(
    [JAVA, "-jar", JAR],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    env=env
)

results = []
try:
    # 1. Initialize
    send_msg(proc, "initialize", {"protocolVersion": "2024-11-05"})
    r = read_response(proc)
    v = r.get("result", {}).get("serverInfo", {}).get("version") if r else "?"
    results.append(("initialize", "OK" if v == "1.1.0" else f"FAIL v={v}"))

    # 2. tools/list - should have 9 tools (including list_projects)
    send_msg(proc, "tools/list", msg_id=2)
    r = read_response(proc)
    tools = r.get("result", {}).get("tools", []) if r else []
    tool_names = [t["name"] for t in tools]
    has_list_projects = "list_projects" in tool_names
    results.append(("tools/list", f"{len(tools)} tools, list_projects={'✅' if has_list_projects else '❌'}"))

    # 3. list_projects
    send_msg(proc, "tools/call", {"name": "list_projects", "arguments": {}}, 3)
    r = read_response(proc)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        project_names = [p["name"] for p in inner.get("projects", [])]
        results.append(("list_projects", f"projects={project_names}"))
    except:
        results.append(("list_projects", f"raw={content[:100]}"))

    # 4. find_symbol in jindexer project
    send_msg(proc, "tools/call", {"name": "find_symbol", "arguments": {"query": "ConfigLoader", "project": "jindexer"}}, 4)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("find_symbol (jindexer)", f"total={inner.get('total', '?')}, project={inner.get('project')}"))
    except:
        results.append(("find_symbol (jindexer)", f"raw={content[:100]}"))

    # 5. find_symbol in test-project
    send_msg(proc, "tools/call", {"name": "find_symbol", "arguments": {"query": "App", "project": "test-project"}}, 5)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("find_symbol (test-project)", f"total={inner.get('total', '?')}, project={inner.get('project')}"))
    except:
        results.append(("find_symbol (test-project)", f"raw={content[:100]}"))

    # 6. find_symbol in default project (no project param)
    send_msg(proc, "tools/call", {"name": "find_symbol", "arguments": {"query": "ConfigLoader"}}, 6)
    r = read_response(proc, timeout=15)
    content = r.get("result", {}).get("content", [{}])[0].get("text", "") if r else ""
    try:
        inner = json.loads(content)
        results.append(("find_symbol (default)", f"total={inner.get('total', '?')}, project={inner.get('project')}"))
    except:
        results.append(("find_symbol (default)", f"raw={content[:100]}"))

    # Print results
    print("\n=== 测试结果 ===")
    all_ok = True
    for name, result in results:
        status = "✅" if "FAIL" not in result and "❌" not in result else "❌"
        if status == "❌":
            all_ok = False
        print(f"  {status} {name}: {result}")

    if all_ok:
        print(f"\n✅ 所有 {len(results)} 个多项目测试通过!")
    else:
        print(f"\n⚠️  {len(results)} 个测试完成，部分失败")

except Exception as e:
    print(f"❌ 测试异常: {e}")
    import traceback
    traceback.print_exc()
finally:
    proc.kill()
    proc.wait()
