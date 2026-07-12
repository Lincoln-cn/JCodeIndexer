#!/usr/bin/env python3
"""FTS5 全文搜索功能测试"""
import subprocess, json, os, time

JAR = os.environ.get("JAR_PATH")
ROOT = os.environ.get("PROJECT_ROOT")
JAVA = r"C:\Users\Administrator\scoop\apps\temurin-jdk\26.0.1-8\bin\java.exe"

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", ROOT],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
)

def send(obj):
    data = json.dumps(obj).encode()
    proc.stdin.write(f"Content-Length: {len(data)}\r\n\r\n".encode())
    proc.stdin.write(data)
    proc.stdin.flush()

def read():
    buf = b""
    while True:
        ch = os.read(proc.stdout.fileno(), 1)
        if not ch: return None
        if ch == b"\n": break
        buf += ch
    line = buf.decode().strip()
    if not line: return None
    return json.loads(line)

def call(name, args, rid):
    send({"jsonrpc":"2.0","id":rid,"method":"tools/call","params":{"name":name,"arguments":args}})
    r = read()
    if not r: return None
    content = r.get("result",{}).get("content",[])
    if content: return json.loads(content[0].get("text","{}"))
    return None

send({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}})
read()
send({"jsonrpc":"2.0","method":"notifications/initialized","params":{}})
time.sleep(0.3)

print("=" * 60)
print("FTS5 全文搜索功能测试")
print("=" * 60)

# Test 1: Basic keyword search
r = call("search_code", {"query": "StorageService", "limit": 5}, 2)
print(f"\n[1] 基本关键字搜索: StorageService")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")
for s in r.get("symbols", [])[:3]:
    print(f"    符号: {s['name']} ({s['kind']}) @ {s['file']}:{s['line']}")
for c in r.get("chunks", [])[:3]:
    print(f"    代码块: {c['name']} ({c['type']}) @ {c['file']}:{c['line']}")

# Test 2: Partial match
r = call("search_code", {"query": "Config", "limit": 5}, 3)
print(f"\n[2] 部分匹配搜索: Config")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")
for s in r.get("symbols", [])[:3]:
    print(f"    符号: {s['name']} ({s['kind']}) @ {s['file']}")

# Test 3: Code content search
r = call("search_code", {"query": "PreparedStatement", "limit": 5}, 4)
print(f"\n[3] 代码内容搜索: PreparedStatement")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")
for s in r.get("symbols", [])[:3]:
    print(f"    符号: {s['name']} ({s['kind']}) @ {s['file']}")
for c in r.get("chunks", [])[:3]:
    print(f"    代码块: {c['name']} ({c['type']}) @ {c['file']}")

# Test 4: Wildcard search
r = call("search_code", {"query": "*", "limit": 3}, 5)
print(f"\n[4] 通配符搜索: *")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")

# Test 5: find_symbol (should use FTS)
r = call("find_symbol", {"query": "McpServer", "limit": 3}, 6)
print(f"\n[5] find_symbol: McpServer (使用 FTS)")
print(f"    结果数: {r['total']}")
for s in r.get("symbols", [])[:3]:
    print(f"    {s['name']} ({s['kind']}) @ {s['file']}:{s['line']}")

# Test 6: Performance comparison
start = time.time()
for i in range(10):
    call("search_code", {"query": "java", "limit": 10}, 100+i)
elapsed = (time.time() - start) * 1000
print(f"\n[6] 性能测试: 10 次搜索")
print(f"    总耗时: {elapsed:.0f}ms, 平均: {elapsed/10:.1f}ms/次")

# Test 7: FTS5 specific features
r = call("search_code", {"query": "Config AND Loader", "limit": 5}, 7)
print(f"\n[7] FTS5 布尔搜索: Config AND Loader")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")
for s in r.get("symbols", [])[:3]:
    print(f"    符号: {s['name']} ({s['kind']}) @ {s['file']}")

# Test 8: Prefix search
r = call("search_code", {"query": "Mcp*", "limit": 5}, 8)
print(f"\n[8] FTS5 前缀搜索: Mcp*")
print(f"    结果数: {r['total_hits']}, 耗时: {r['query_time_ms']}ms")
for s in r.get("symbols", [])[:3]:
    print(f"    符号: {s['name']} ({s['kind']}) @ {s['file']}")

proc.kill()
proc.wait()

print("\n" + "=" * 60)
print("所有测试完成!")
print("=" * 60)
