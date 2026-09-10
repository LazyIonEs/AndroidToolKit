#!/usr/bin/env python3
"""Run reflection, native and loopback update contracts with the actual packaged runtime/JAR.

Close the isolated release UI before invoking this script. Only the disposable
migration bundle's launcher configuration is temporarily changed and restored.
"""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import subprocess
import sys
import threading
import zipfile

root = Path(__file__).resolve().parents[2]
jdk = Path(sys.argv[1]).resolve()
app = root / "composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app"
cfg = app / "Contents/app/AndroidToolKit.cfg"
original = cfg.read_text()
assert "migration.smoke.ReleaseSmokePreferences" in original, "Isolate preferences first"
assert "-javaagent:" not in original, "Stop the UI observer and restore its configuration first"
jar = next((app / "Contents/app").glob("composeApp-jvm-*.jar"))
work = root / "shared/build/migration/release-contracts"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
(work / "result.json").unlink(missing_ok=True)
with zipfile.ZipFile(jar) as z:
    app_classes = {n: z.read(n) for n in z.namelist() if n.startswith("org/tool/kit/") and n.endswith(".class")}


def unique_class(marker):
    found = [n[:-6].replace("/", ".") for n, data in app_classes.items() if marker.encode() in data]
    assert len(found) == 1, (marker, found)
    return found[0]


serializer = unique_class("androidx.navigation.runtime.NavKey")
keys = ["org.tool.kit.feature." + name for name in re.findall(r'"([\w.]+NavKey)" to',
    (root / "shared/src/jvmTest/kotlin/org/tool/kit/migration/NavigationSerializationTest.kt").read_text())]
assert len(keys) == 9
nav_classes = [unique_class(name) for name in keys]
download_block = unique_class("downloadFile 开始下载")
transport = download_block.rsplit("$", 1)[0]
check_block = unique_class("checkUpdate 开始检查更新")
sources = [root / "scripts/migration" / name for name in
           ("ReleaseNavigationSmoke.java", "ReleaseNativeSmoke.java", "ReleaseUpdateSmoke.java")]
subprocess.run([str(jdk / "bin/javac"), "--release", "21", "-cp", str(jar), "-d", str(classes), *map(str, sources)], check=True)
payload = bytes(range(251)) * 2048
dmg = next((app.parents[1] / "dmg").glob("*.dmg"))
sha = lambda b: hashlib.sha256(b).hexdigest()
requests = []


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def do_GET(self):
        requests.append(self.path)
        if self.path == "/release":
            data = {"url": "", "html_url": "https://example.invalid/phase10", "assets_url": "", "upload_url": "",
                    "tarball_url": None, "zipball_url": None, "id": 10, "node_id": "phase10", "tag_name": "v99.0.0",
                    "target_commitish": "fixture", "name": "Phase 10 fixture", "body": "Local release validation",
                    "draft": False, "prerelease": False, "immutable": False, "created_at": "2026-09-10T00:00:00Z",
                    "published_at": None, "updated_at": None, "assets": [{
                        "url": "", "browser_download_url": f"http://127.0.0.1:{self.server.server_port}/installer.dmg",
                        "id": 10, "node_id": "phase10-asset", "name": "AndroidToolKit-macos-arm64.dmg", "label": None,
                        "state": "uploaded", "content_type": "application/octet-stream", "size": dmg.stat().st_size,
                        "digest": None, "download_count": 0, "created_at": "2026-09-10T00:00:00Z", "updated_at": None,
                    }]}
            body = json.dumps(data).encode()
        elif self.path == "/installer.dmg":
            body = dmg.read_bytes()
        elif self.path in ("/known", "/unknown", "/broken"):
            body = payload[:1024] if self.path == "/broken" else payload
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/json" if self.path == "/release" else "application/octet-stream")
        if self.path != "/unknown":
            self.send_header("Content-Length", str(len(payload) * 2 if self.path == "/broken" else len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass


server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
server_thread = threading.Thread(target=server.serve_forever, daemon=True)
server_thread.start()
outputs = []
try:
    for name, args in [
        ("ReleaseNavigationSmoke", [serializer, *nav_classes]),
        ("ReleaseNativeSmoke", [str(work / "native")]),
        ("ReleaseUpdateSmoke", [transport, check_block, f"http://127.0.0.1:{server.server_port}", str(work / "downloads"),
                                sha(payload), sha(dmg.read_bytes())]),
    ]:
        text = original.replace("app.mainclass=org.tool.kit.MainKt", "app.mainclass=migration.smoke." + name)
        text = text.replace("app.classpath=$APPDIR/" + jar.name, "app.classpath=$APPDIR/" + jar.name + ":" + str(classes))
        if name == "ReleaseUpdateSmoke":
            recording = work / "update-recording"
            recording.mkdir(exist_ok=True)
            (recording / "scenario.txt").write_text("loopback-update\n")
            observer = root / "shared/build/migration/release-profile/observer.jar"
            assert observer.exists(), "Build the optional observer with prepare_release_profile.py first"
            text += f"\njava-options=-javaagent:{observer}={recording}\n"
        cfg.write_text(text)
        try:
            result = subprocess.run([str(app / "Contents/MacOS/AndroidToolKit"), *args], capture_output=True, text=True, timeout=90)
        except subprocess.TimeoutExpired as failure:
            output = "".join(value.decode(errors="replace") if isinstance(value, bytes) else value or ""
                             for value in (failure.stdout, failure.stderr))
            (work / (name + ".timeout.txt")).write_text(output)
            raise
        (work / (name + ".txt")).write_text(result.stdout + result.stderr)
        print(result.stdout, end="")
        assert result.returncode == 0, result.stderr + "\n" + result.stdout
        outputs.append({"entry": name, "exitCode": result.returncode})
finally:
    cfg.write_text(original)
    server.shutdown()
    server.server_close()
    server_thread.join(timeout=5)
assert cfg.read_text() == original
(work / "result.json").write_text(json.dumps({"entries": outputs, "requests": requests, "launcherRestored": True,
    "navigationKeys": dict(zip(keys, nav_classes)), "downloadedDmgSha256": sha((work / "downloads/AndroidToolKit-macos-arm64.dmg").read_bytes()),
    "scope": "Delivered optimized classes/runtime. Test entry and loopback fixture replace UI/network origin only; no production implementation replacement."}, indent=2) + "\n")
