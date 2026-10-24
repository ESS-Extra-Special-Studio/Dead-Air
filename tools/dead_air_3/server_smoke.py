"""Boot the local dedicated test server, run console commands, print relevant output, stop.

Usage: server_smoke.py <server_dir> <java_args_file> [commands...]
"""
import re
import subprocess
import sys
import threading
import time

server_dir, args_file = sys.argv[1], sys.argv[2]
commands = sys.argv[3:]

launch = (["cmd", "/c", "gradlew.bat", "runServer", "--no-daemon"] if args_file == "gradle"
          else ["java", "@user_jvm_args.txt", f"@{args_file}", "nogui"])
proc = subprocess.Popen(
    launch,
    cwd=server_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
    text=True, encoding="utf-8", errors="replace", bufsize=1,
)
lines = []
done = threading.Event()


def pump():
    for line in proc.stdout:
        lines.append(line.rstrip())
        if "Done (" in line:
            done.set()


threading.Thread(target=pump, daemon=True).start()
if not done.wait(300):
    print("SERVER DID NOT START")
    proc.kill()
    for l in lines[-60:]:
        print(l)
    sys.exit(1)

start = len(lines)
for cmd in commands:
    proc.stdin.write(cmd + "\n")
    proc.stdin.flush()
    time.sleep(1.5)
time.sleep(3)
proc.stdin.write("stop\n")
proc.stdin.flush()
try:
    proc.wait(90)
except subprocess.TimeoutExpired:
    proc.kill()

pattern = re.compile(r"ERROR|Exception|Caused by|dead_air|Failed|Unknown|Couldn't|Invalid|Placed|Summoned|Spawned|loot", re.I)
print("=== startup issues ===")
for l in lines[:start]:
    if re.search(r"(ERROR|Exception|Caused by|Failed to|Couldn't)", l) and "dead_air" in l.lower() or "dead_air" in l and "WARN" in l:
        print(l)
print("=== command output ===")
for l in lines[start:]:
    if pattern.search(l) or "]: " in l:
        print(l)
