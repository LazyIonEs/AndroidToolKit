#!/usr/bin/env python3
"""Add a test-only EDT observer to an already preference-isolated migration bundle."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
jdk = Path(sys.argv[1]).resolve()
work = root / "shared/build/migration/release-profile"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
cfg = root / "composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app/Contents/app/AndroidToolKit.cfg"
original = cfg.read_text()
assert "migration.smoke.ReleaseSmokePreferences" in original, "Isolate preferences first"
assert "-javaagent:" not in original, "Rebuild disposable bundle before another observer run"
subprocess.run([str(jdk / "bin/javac"), "--release", "21", "-d", str(classes),
                str(root / "scripts/migration/ReleaseProfileAgent.java")], check=True)
manifest = work / "MANIFEST.MF"
manifest.write_text("Manifest-Version: 1.0\nPremain-Class: migration.smoke.ReleaseProfileAgent\n\n")
jar = work / "observer.jar"
subprocess.run([str(jdk / "bin/jar"), "--create", "--file", str(jar), "--manifest", str(manifest),
                "-C", str(classes), "."], check=True)
cfg.with_suffix(".cfg.before-profile").write_text(original)
cfg.write_text(original + f"\njava-options=-javaagent:{jar}={work / 'recording'}\n")
print(work / "recording")
