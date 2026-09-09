#!/usr/bin/env python3
"""Package the test-only desktop entry so native macOS automation can identify it."""
import os
import platform
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / "shared/build/migration"


def main():
    if platform.system() != "Darwin":
        raise SystemExit("This baseline packaging helper is macOS-only; other platforms need their own baseline.")
    classpath = (WORK / "classpath.txt").read_text().split(os.pathsep)
    # Use a fresh directory; never overwrite an existing baseline application.
    import tempfile
    stage = Path(tempfile.mkdtemp(prefix="baseline-bundle-", dir=WORK))
    inputs = stage / "input"
    inputs.mkdir()
    jars, directories = [], []
    for index, name in enumerate(classpath):
        source = Path(name)
        if source.is_file():
            target = f"{index}-{source.name}"
            shutil.copy2(source, inputs / target)
            jars.append(target)
        elif source.is_dir():
            directories.append(source)
    manifest_line = "Class-Path: " + " ".join(jars)
    folded = manifest_line[:70]
    manifest_line = manifest_line[70:]
    while manifest_line:
        folded += "\r\n " + manifest_line[:69]
        manifest_line = manifest_line[69:]
    with zipfile.ZipFile(inputs / "baseline.jar", "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n" + folded + "\r\n\r\n")
        seen = {"META-INF/MANIFEST.MF"}
        for directory in directories:
            for path in sorted(directory.rglob("*")):
                relative = path.relative_to(directory).as_posix()
                if path.is_file() and relative not in seen:
                    archive.write(path, relative)
                    seen.add(relative)
    resources = stage / "resources"
    resources.mkdir()
    for source in (ROOT / "composeApp/resources/common",
                   ROOT / f"composeApp/resources/macos-{'arm64' if platform.machine() == 'arm64' else 'x64'}"):
        shutil.copytree(source, resources, dirs_exist_ok=True)
    java_home = Path(subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip())
    command = [str(java_home / "bin/jpackage"), "--type", "app-image", "--input", str(inputs),
               "--dest", str(stage / "app"), "--name", "AndroidToolKitBaseline",
               "--main-jar", "baseline.jar", "--main-class", "org.tool.kit.migration.BaselineDesktopKt",
               "--mac-package-identifier", "org.tool.kit.migration.baseline", "--runtime-image", str(java_home),
               "--icon", str(ROOT / "composeApp/launcher/icon.icns")]
    properties = {
        "java.util.prefs.PreferencesFactory": "org.tool.kit.migration.IsolatedPreferencesFactory",
        "migration.fixtureRoot": str(WORK / "fixtures"),
        "migration.theme": os.environ.get("MIGRATION_THEME", "LIGHT"),
        "compose.application.resources.dir": str(resources),
        "app.log.dir": str(WORK / "logs"),
        "user.language": "zh", "user.country": "CN",
    }
    for key, value in properties.items():
        # jpackage's launcher parser accepts quoted property values containing spaces.
        command += ["--java-options", f'-D{key}="{value}"']
    subprocess.run(command, check=True)
    result = stage / "app/AndroidToolKitBaseline.app"
    (WORK / "baseline-app-path.txt").write_text(str(result) + "\n")
    print(result)


if __name__ == "__main__":
    main()
