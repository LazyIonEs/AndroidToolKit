#!/usr/bin/env python3
"""Run a native fixture using the disposable release bundle's runtime and JAR."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
jdk = Path(sys.argv[1]).resolve()
app = root / "composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app"
classes = root / "shared/build/migration/release-smoke/classes"
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([str(jdk / "bin/javac"), "--release", "21", "-d", str(classes),
               str(root / "scripts/migration/ReleaseNativeSmoke.java")], check=True)
cfg = app / "Contents/app/AndroidToolKit.cfg"
original = cfg.read_text()
assert "app.mainclass=org.tool.kit.MainKt" in original
# Run after closing the isolated release UI. jpackage's runtime omits bin/java,
# so temporarily select the Java fixture with the same packaged launcher.
try:
    cfg.write_text(original.replace("app.mainclass=org.tool.kit.MainKt",
        "app.mainclass=migration.smoke.ReleaseNativeSmoke").replace(
        "app.classpath=$APPDIR/composeApp-jvm-1.6.11.jar",
        f"app.classpath=$APPDIR/composeApp-jvm-1.6.11.jar:{classes}"))
    subprocess.run([str(app / "Contents/MacOS/AndroidToolKit"),
                    str(classes.parent / "output")], check=True, timeout=30)
finally:
    cfg.write_text(original)
