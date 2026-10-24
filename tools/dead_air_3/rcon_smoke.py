"""Start a dev server via `gradlew runServer`, run commands over RCON, print replies, stop.

Usage: rcon_smoke.py <project_dir> <port> <password> [commands...]
RCON must already be enabled in <project_dir>/run/server.properties.
"""
import socket
import struct
import subprocess
import sys
import time

project, port, password = sys.argv[1], int(sys.argv[2]), sys.argv[3]
commands = sys.argv[4:]

proc = subprocess.Popen(["cmd", "/c", "gradlew.bat", "runServer", "--no-daemon"], cwd=project,
                        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def packet(sock, req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(data)) + data)
    size = struct.unpack("<i", sock.recv(4))[0]
    buf = b""
    while len(buf) < size:
        buf += sock.recv(size - len(buf))
    return buf[8:-2].decode("utf-8", "replace")


sock = None
deadline = time.time() + 420
while time.time() < deadline:
    try:
        sock = socket.create_connection(("127.0.0.1", port), timeout=10)
        break
    except OSError:
        time.sleep(5)
if sock is None:
    print("RCON never came up")
    proc.kill()
    sys.exit(1)

packet(sock, 1, 3, password)
for i, cmd in enumerate(commands, start=2):
    try:
        reply = packet(sock, i, 2, cmd)
    except Exception as e:  # noqa: BLE001
        reply = f"<error {e}>"
    print(f"> {cmd}\n  {reply.strip()}")
    time.sleep(1)
try:
    packet(sock, 999, 2, "stop")
except Exception:  # noqa: BLE001
    pass
try:
    proc.wait(120)
except subprocess.TimeoutExpired:
    proc.kill()
