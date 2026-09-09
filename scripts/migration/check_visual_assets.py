#!/usr/bin/env python3
"""Check frozen visual assets against the audited commit (never rewrites the baseline)."""
import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs/migration/visual-assets.json"


def visual_files():
    tracked = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT).decode().split("\0")
    return sorted(path for path in tracked if path and (
        path.startswith(("shared/src/commonMain/composeResources/",
                         "shared/src/commonMain/kotlin/org/tool/kit/theme/",
                         "composeApp/launcher/"))
        or path.endswith("navigation/DefaultTransitionSpec.kt")
    ))


def main():
    baseline = json.loads(MANIFEST.read_text())
    actual = {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest()
              for path in visual_files() if (ROOT / path).is_file()}
    expected = baseline["sha256"]
    differences = [path for path in sorted(actual.keys() | expected.keys())
                   if actual.get(path) != expected.get(path)]
    if differences:
        raise SystemExit("Frozen visual assets changed:\n" + "\n".join(differences))
    print(f"PASS: {len(expected)} visual assets match {baseline['commit']}")


if __name__ == "__main__":
    main()
