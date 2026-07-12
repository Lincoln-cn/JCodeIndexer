#!/usr/bin/env python3
"""Test initialize response format"""
import subprocess
import json
import sys
import os
import glob
import shutil

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

init_msg = json.dumps({
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
        "protocolVersion": "2024-11-05",
        "capabilities": {},
        "clientInfo": {"name": "test-client", "version": "1.0.0"}
    }
})

print(f"JAR: {JAR}")
print(f"JAVA: {JAVA}")
print(f"ROOT: {ROOT}")
print("\nSending initialize request...")

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", ROOT],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE
)

try:
    proc.stdin.write((init_msg + "\n").encode())
    proc.stdin.flush()
    
    import select
    ready, _, _ = select.select([proc.stdout], [], [], 5)
    if ready:
        line = proc.stdout.readline().decode().strip()
        print(f"\nResponse: {line}")
        
        resp = json.loads(line)
        result = resp.get("result", {})
        print(f"\nprotocolVersion: {result.get('protocolVersion')}")
        print(f"serverInfo: {result.get('serverInfo')}")
        print(f"capabilities: {result.get('capabilities')}")
        
        # Check if serverInfo is correct
        if result.get("serverInfo") and result["serverInfo"].get("name") == "java-code-indexer":
            print("\n✓ serverInfo format is correct!")
        else:
            print("\n✗ serverInfo format is wrong!")
    else:
        print("No response within 5 seconds")
        
    # Send initialized notification
    initialized_msg = json.dumps({
        "jsonrpc": "2.0",
        "method": "notifications/initialized"
    })
    print(f"\nSending: {initialized_msg}")
    proc.stdin.write((initialized_msg + "\n").encode())
    proc.stdin.flush()
    
    # Send tools/list
    tools_msg = json.dumps({
        "jsonrpc": "2.0",
        "id": 2,
        "method": "tools/list",
        "params": {}
    })
    print(f"\nSending: {tools_msg}")
    proc.stdin.write((tools_msg + "\n").encode())
    proc.stdin.flush()
    
    ready, _, _ = select.select([proc.stdout], [], [], 5)
    if ready:
        line = proc.stdout.readline().decode().strip()
        resp = json.loads(line)
        tools = resp.get("result", {}).get("tools", [])
        print(f"\nTools count: {len(tools)}")
        for t in tools:
            print(f"  - {t['name']}")
    
finally:
    proc.stdin.close()
    proc.terminate()
    proc.wait(timeout=5)
