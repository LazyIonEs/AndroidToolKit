#!/usr/bin/env python3
"""Interactive JSON-lines client for the official Hot Reload stdio MCP server.

Pass the JBR 21 home as the sole argument. After initialization, enter MCP tool
calls as {"name": "status", "arguments": {}}. Responses and PNGs stay in build/.
"""
import base64
import json
from pathlib import Path
import queue
import subprocess
import sys
import threading
import tempfile

sys.dont_write_bytecode = True


def main():
    root = Path(__file__).resolve().parents[2]
    output = root / "shared/build/migration/hot-mcp"
    output.mkdir(parents=True, exist_ok=True)
    output = Path(tempfile.mkdtemp(prefix="session-", dir=output))
    print(f"Evidence directory: {output}", flush=True)
    jbr = Path(sys.argv[1]).resolve()
    release = (jbr / "release").read_text()
    if 'IMPLEMENTOR="JetBrains' not in release or 'JAVA_VERSION="21.' not in release:
        raise SystemExit("Supply a JetBrains JDK 21 home")
    command = [str(root / "gradlew"), "--no-daemon", "--quiet", "--console=plain",
               f"-Dorg.gradle.java.home={jbr}", "-PmigrationHotReload=true", ":shared:hotMcpServerJvm"]
    with (output / "server.log").open("a") as log:
        server = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE,
                                  stdout=subprocess.PIPE, stderr=log, text=True, bufsize=1)
        messages = queue.Queue()

        def read_server():
            for line in server.stdout:
                try:
                    messages.put(json.loads(line))
                except json.JSONDecodeError:
                    log.write(line)
                    log.flush()
            messages.put(None)

        threading.Thread(target=read_server, daemon=True).start()
        sequence = 0

        def send(message):
            server.stdin.write(json.dumps({"jsonrpc": "2.0", **message}) + "\n")
            server.stdin.flush()

        def request(method, params):
            nonlocal sequence
            sequence += 1
            send(dict(id=sequence, method=method, params=params))
            while True:
                response = messages.get(timeout=180)
                if response is None:
                    raise RuntimeError("MCP server exited; inspect server.log")
                if response.get("id") == sequence:
                    return response

        try:
            initialized = request("initialize", dict(protocolVersion="2024-11-05",
                capabilities={}, clientInfo=dict(name="migration-baseline", version="1")))
            (output / "initialize.json").write_text(json.dumps(initialized, indent=2) + "\n")
            print(json.dumps(initialized["result"]["serverInfo"]), flush=True)
            send(dict(method="notifications/initialized"))
            available = request("tools/list", {})
            (output / "tools.json").write_text(json.dumps(available, indent=2) + "\n")
            print("Tools: " + ", ".join(t["name"] for t in available["result"]["tools"]), flush=True)
            def call_tool(name, arguments=None):
                call = dict(name=name, arguments=arguments or {})
                response = request("tools/call", call)
                stem = f"{sequence:03d}-{call['name']}"
                # Store PNG bytes returned by the official MCP without re-encoding.
                for i, item in enumerate(response.get("result", {}).get("content", [])):
                    if item.get("type") == "image":
                        if item.get("mimeType") != "image/png":
                            raise RuntimeError(f"Unexpected image encoding: {item.get('mimeType')}")
                        path = output / f"{stem}-{i}.png"
                        path.write_bytes(base64.b64decode(item.pop("data"), validate=True))
                        item["saved_path"] = str(path)
                (output / f"{stem}.json").write_text(json.dumps(response, ensure_ascii=False, indent=2) + "\n")
                if "error" in response or response.get("result", {}).get("isError"):
                    raise RuntimeError(response)
                return response["result"]["content"]

            if "--phase2-smoke" in sys.argv[2:]:
                from hot_mcp_phase2 import smoke_phase2
                smoke_phase2(call_tool, output, root)
            elif "--smoke" in sys.argv[2:]:
                from hot_mcp_scenarios import capture_baseline
                capture_baseline(call_tool, output)
            else:
                for line in sys.stdin:
                    if line.strip():
                        call = json.loads(line)
                        print(json.dumps(call_tool(call["name"], call.get("arguments")), ensure_ascii=False), flush=True)
        finally:
            server.stdin.close()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.terminate()


if __name__ == "__main__":
    main()
