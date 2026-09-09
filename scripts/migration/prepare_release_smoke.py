#!/usr/bin/env python3
"""Isolate only the disposable -PmigrationPackage=true macOS release bundle.

The application JAR and resources remain the actual ProGuard output. A Java-only
preferences factory on the bootstrap path prevents access to the user's toolkit
preferences. The normal release output and launcher configuration are untouched.
"""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
jdk = Path(sys.argv[1]).resolve()
bundle = root / "composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app"
cfg = bundle / "Contents/app/AndroidToolKit.cfg"
original = cfg.with_suffix(".cfg.original")
if original.exists():
    raise SystemExit("Already isolated; rebuild the disposable bundle for another run")
work = root / "shared/build/migration/release-smoke"
classes = work / "classes"
classes.mkdir(parents=True, exist_ok=True)
output = root / "shared/build/migration/fixtures/output"
output.mkdir(parents=True, exist_ok=True)
subprocess.run([str(jdk / "bin/javac"), "--release", "21", "-d", str(classes),
                str(root / "scripts/migration/ReleaseSmokePreferences.java")], check=True)
jar = work / "preferences.jar"
subprocess.run([str(jdk / "bin/jar"), "--create", "--file", str(jar), "-C", str(classes), "."], check=True)
text = cfg.read_text()
assert "app.mainclass=org.tool.kit.MainKt" in text
assert "PreferencesFactory" not in text
original.write_text(text)
cfg.write_text(text + f"\njava-options=-Xbootclasspath/a:{jar}\n"
    "java-options=-Djava.util.prefs.PreferencesFactory=migration.smoke.ReleaseSmokePreferences\n"
    f"java-options=-Dmigration.output={output}\n"
    "java-options=-Duser.language=zh\njava-options=-Duser.country=CN\n")
print(bundle)
