#!/usr/bin/env python3
"""Test health tool"""
import subprocess, json, os, time

JAR = os.environ.get("JAR_PATH")
ROOT = os.environ.get("PROJECT_ROOT")
JAVA = r"C:\Users\Administrator\scoop\apps\temurin-jdk\26.0.1-8\bin\java.exe"

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", ROOT],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
)

# 等待进程启动
time.sleep(2)

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
    return json.loads(buf.decode().strip())

send({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}})
read()
send({"jsonrpc":"2.0","method":"notifications/initialized","params":{}})
time.sleep(0.5)

# 测试 health
send({"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"health","arguments":{}}})
r = read()
content = r.get("result",{}).get("content",[])
health = json.loads(content[0].get("text","{}"))
print(json.dumps(health, indent=2, ensure_ascii=False))

proc.kill()
proc.wait()
