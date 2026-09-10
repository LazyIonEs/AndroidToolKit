#!/usr/bin/env python3
"""Audit an actual macOS migration release without modifying the bundle or baselines."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("destination", type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
app = root / "composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app"
jar = next((app / "Contents/app").glob("composeApp-jvm-*.jar"))
proguard = root / "composeApp/build/compose/tmp/main-release/proguard" / jar.name
sha = lambda data: hashlib.sha256(data).hexdigest()


def sections(data):
    # jpackage ad-hoc signs native libraries; compare all file-backed Mach-O sections.
    assert struct.unpack_from("<I", data)[0] == 0xfeedfacf
    cursor = 32
    result = []
    for _ in range(struct.unpack_from("<I", data, 16)[0]):
        command, size = struct.unpack_from("<II", data, cursor)
        if command == 0x19:
            for index in range(struct.unpack_from("<I", data, cursor + 64)[0]):
                offset = cursor + 72 + index * 80
                name = data[offset:offset + 16].split(b"\0")[0].decode()
                segment = data[offset + 16:offset + 32].split(b"\0")[0].decode()
                count = struct.unpack_from("<Q", data, offset + 40)[0]
                start = struct.unpack_from("<I", data, offset + 48)[0]
                flags = struct.unpack_from("<I", data, offset + 64)[0] & 255
                if flags in (1, 12, 18) or not count:
                    continue
                payload = data[start:start + count]
                assert len(payload) == count
                result.append({"segment": segment, "section": name, "size": count, "sha256": sha(payload)})
        cursor += size
    return result


with zipfile.ZipFile(jar) as delivered, zipfile.ZipFile(proguard) as before_signing:
    native = [name for name in delivered.namelist() if name.endswith("libuniffi_toolkit.dylib")]
    assert len(native) == 1, native
    actual = delivered.read(native[0])
    original = (root / "rust/target/release/libtoolkit_rs.dylib").read_bytes()
    assert before_signing.read(native[0]) == original
    assert sections(actual) == sections(original)
    assert "com/sun/jna/Native.class" in delivered.namelist()
    embedded_tools = [name for name in delivered.namelist() if "aapt" in name and not name.endswith(".class")]
    assert embedded_tools
    assets = [name for name in delivered.namelist() if name.startswith("composeResources/")
              and ("lottie" in name or "/font/" in name or "aboutlibraries" in name)]
    assert any("/font/" in name for name in assets) and any("lottie" in name for name in assets)
    license_name = next(name for name in delivered.namelist() if name.endswith("/aboutlibraries.json"))
    licenses_bytes = delivered.read(license_name)
    license_source = "shared/src/commonMain/composeResources/files/aboutlibraries.json"
    assert licenses_bytes == (root / license_source).read_bytes()
    current_licenses = json.loads(licenses_bytes)
    current_names = {item["uniqueId"] for item in current_licenses["libraries"]}
    phase1_diff = json.loads((root / "docs/migration/evidence/phase-01/libraries-diff.json").read_text())
    current_versions = {item["uniqueId"]: item.get("artifactVersion") for item in current_licenses["libraries"]}
    for name, version in phase1_diff["added"].items():
        assert current_versions.get(name) == version, (name, current_versions.get(name), version)
    class_changes = [name for name in delivered.namelist() if name.endswith(".class")
                     and delivered.read(name) != before_signing.read(name)]
    assert not class_changes, class_changes

resource_root = app / "Contents/app/resources"
resources = {}
for name in ("oppo.apk", "vivo.apk", "huawei.apk", "xiaomi.apk", "qq.apk", "honor.apk", "apktool.apk", "aapt2"):
    paths = list(resource_root.rglob(name))
    assert len(paths) == 1, (name, paths)
    data = paths[0].read_bytes()
    resources[name] = {"path": str(paths[0].relative_to(app)), "sha256": sha(data), "bytes": len(data)}
    source = root / "composeApp/resources/common" / name
    if source.is_file():
        assert source.read_bytes() == data, name

result = {
    "platform": "macOS arm64", "jarSha256": sha(jar.read_bytes()), "jarBytes": jar.stat().st_size,
    "proguardJarSha256": sha(proguard.read_bytes()), "classPayloadsUnchangedByPackaging": True,
    "cargoNativeSha256": sha(original), "deliveredNativeSha256": sha(actual), "machOSections": sections(actual),
    "nativeSectionsEqual": True, "nativeResources": native, "jnaPresent": True,
    "composeAssets": assets, "applicationResources": resources, "apktoolEmbeddedTools": embedded_tools,
    "runtime": (app / "Contents/runtime/Contents/Home/release").read_text(),
    "licenses": {"matchesGeneratedSource": True, "count": len(current_names),
                 "phase1ArchivedDiff": phase1_diff, "allPhase1AdditionsRetainedAtSameVersions": True,
                 "comparisonLimit": "Original generated JSON was not tracked. Phase 0 comparison uses the archived Phase 1 diff; this is not a newly reconstructed complete baseline diff."},
    "dmg": [{"path": str(path.relative_to(root)), "sha256": sha(path.read_bytes()), "bytes": path.stat().st_size}
            for path in (app.parents[1] / "dmg").glob("*.dmg")],
}
assert result["dmg"], "The DMG task has not completed"
args.destination.parent.mkdir(parents=True, exist_ok=True)
args.destination.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
print("PASS: actual ProGuard JAR, native sections, JNA, resources, licenses and DMG", result["jarBytes"])
