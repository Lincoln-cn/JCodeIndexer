#!/usr/bin/env python3
"""Multi-project MCP test"""
import subprocess, json, sys, time, os, glob, shutil

# Configurable paths via environment variables
JAR = os.environ.get("JAR_PATH")
JAVA = os.environ.get("JAVA_HOME")
CONFIG = os.environ.get("MULTI_PROJECT_CONFIG")

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

# Auto-detect config if not set
if not CONFIG:
    CONFIG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-multi-project.yaml")

# Auto-detect project roots from config or use defaults
JINDEXER_ROOT = os.environ.get("JINDEXER_ROOT", os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
TEST_PROJECT_ROOT = os.environ.get("TEST_PROJECT_ROOT", "/tmp/test-multi-project")

if not JAR:
    print("ERROR: JAR not found. Set JAR_PATH env var or build the project first.")
    sys.exit(1)
if not JAVA:
    print("ERROR: Java not found. Set JAVA_HOME env var or ensure java is in PATH.")
    sys.exit(1)

print(f"JAR: {JAR}")
print(f"JAVA: {JAVA}")
print(f"CONFIG: {CONFIG}")

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
print("\n=== 初始化项目 ===")
for name, root in [("jindexer", JINDEXER_ROOT), ("test-project", TEST_PROJECT_ROOT)]:
    os.makedirs(f"{root}/.jindexer", exist_ok=True)
    init_proc = subprocess.run([JAVA, "-jar", JAR, "--project-root", root, "--init"],
                               capture_output=True, text=True, timeout=30)
    print(f"  {name}: {'OK' if init_proc.returncode == 0 else 'FAIL'}")

# Index both projects
print("\n=== 索引项目 ===")
for name, root in [("jindexer", JINDEXER_ROOT), ("test-project", TEST_PROJECT_ROOT)]:
    idx_proc = subprocess.run([JAVA, "-jar", JAR, "--project-root", root, "--index"],
                              capture_output=True, text=True, timeout=60)
    print(f"  {name}: {'OK' if idx_proc.returncode == 0 else 'FAIL'}")

# Start MCP server with multi-project config
print("\n=== MCP 多项目测试 ===")

# Generate dynamic config with correct paths
config_content = f"""projects:
  - name: jindexer
    root: {JINDEXER_ROOT}
  - name: test-project
    root: {TEST_PROJECT_ROOT}

data_dir: .jindexer
db_name: index.db
extract_javadoc: false
follow_symlinks: false
embedding:
  enabled: false
"""
config_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "test-multi-project-dynamic.yaml")
with open(config_path, "w") as f:
    f.write(config_content)

env = os.environ.copy()
env["JINDEXER_CONFIG"] = config_path

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
