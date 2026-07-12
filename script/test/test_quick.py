#!/usr/bin/env python3
"""Quick diagnostic: can we talk to the MCP server at all?"""
import subprocess, json, time, sys, os, glob, shutil

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

print(f"JAR: {JAR}")
print(f"JAVA: {JAVA}")
print(f"ROOT: {ROOT}")

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
        line = proc.stdout.readline()
        print(f"Got line: {line!r}")
        if line:
            while line.strip():
                line = proc.stdout.readline()
                print(f"Got line: {line!r}")
    else:
        print("No data available after 5s")
        stderr_data = proc.stderr.read(4096) if select.select([proc.stderr], [], [], 0)[0] else b""
        print(f"stderr: {stderr_data.decode(errors='replace')[:500]}")
except Exception as e:
    print(f"Error: {e}")
finally:
    proc.kill()
    proc.wait()
