#!/usr/bin/env python3
"""Quick diagnostic: can we talk to the MCP server at all?"""
import subprocess, json, time, sys

JAR = "/home/ubuntu/jairouter/mcp/java-code-indexer/target/java-code-indexer-1.0.0-SNAPSHOT.jar"
JAVA = "/usr/lib/jvm/java-21-openjdk-amd64/bin/java"
ROOT = "/home/ubuntu/jairouter/mcp/java-code-indexer"

msg = json.dumps({"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05"}})
frame = f"Content-Length: {len(msg.encode('utf-8'))}\r\n\r\n{msg}"

print(f"Sending {len(frame)} bytes to stdin...")
print(f"Frame: {frame[:80]}...")

proc = subprocess.Popen(
    [JAVA, "-jar", JAR, "--project-root", ROOT],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
)

try:
    proc.stdin.write(frame.encode("utf-8"))
    proc.stdin.flush()
    print("Frame sent, waiting for response...")

    import select
    ready = select.select([proc.stdout], [], [], 5)
    if ready[0]:
        # Read headers
        line = proc.stdout.readline()
        print(f"Got line: {line!r}")
        if line:
            # Read more headers until empty line
            while line.strip():
                line = proc.stdout.readline()
                print(f"Got line: {line!r}")
            # Read Content-Length
            # Actually, let me just read the whole response
            pass
    else:
        print("No data available after 5s")
        stderr_data = proc.stderr.read(4096) if select.select([proc.stderr], [], [], 0)[0] else b""
        print(f"stderr: {stderr_data.decode(errors='replace')[:500]}")
except Exception as e:
    print(f"Error: {e}")
finally:
    proc.kill()
    proc.wait()
