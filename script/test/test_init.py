#!/usr/bin/env python3
"""Test initialize response format"""
import subprocess
import json
import sys

JAR = "/home/ubuntu/jairouter/mcp/java-code-indexer/target/java-code-indexer-1.0.0-SNAPSHOT.jar"
JAVA = "/usr/lib/jvm/java-21-openjdk-amd64/bin/java"

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

print("Sending initialize request...")
print(f"Request: {init_msg}")

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", "/home/ubuntu/jairouter/mcp/java-code-indexer"],
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
