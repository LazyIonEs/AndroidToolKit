#!/usr/bin/env python3
"""Source boundary checks for the desktop architecture (standard library only).

This is a lexical guard, not an AST/type or call-graph proof. Import aliases and
fully qualified calls are checked; indirection through arbitrary user-defined
helpers still requires review. compileDomainBoundary separately compiles the
entire Domain closure without Compose, Koin, platform tools, or the app jar.
"""

import argparse
import json
from pathlib import Path
import re
import sys

PRODUCTION_ROOTS = (
    "shared/src/commonMain/kotlin",
    "shared/src/jvmMain/kotlin",
    "composeApp/src/commonMain/kotlin",
    "composeApp/src/jvmMain/kotlin",
)
LEGACY_ALLOWLIST = ()
EXCLUSIONS = {
    "shared/src/jvmTest": "Isolated fakes, mutation fixtures and frozen legacy comparison oracles; never packaged as production.",
    "shared/src/commonTest": "Test-only dependencies and sources.",
    "shared/build/generated": "Generated resource/build-config/UniFFI code; UniFFI is wired only into jvmMain.",
}
DOMAIN_COMPANIONS = {"model/UserData.kt", "model/IconFactoryData.kt"}
RESOURCE_READS = {
    "feature/settings/SettingsRoute.kt": "Res.readBytes inside the suspend produceLibraries loader for packaged license metadata.",
    "utils/LottieAnimation.kt": "Res.readBytes inside suspend rememberLottieComposition; original packaged animation loader.",
}
JVM_ADAPTERS = (
    "data/", "platform/", "di/", "feature/ui/", "navigation/DesktopNavKeySerializer.kt",
    "utils/DesktopFiles.kt", "utils/DesktopFormatting.kt", "utils/DesktopImageRequests.kt",
)
LEGACY = r"\bMainViewModel\b|\bUIState\b|org\.tool\.kit\.utils\.update\b|\bcollectOutputPath\b|\b(?:PendingDeletionFile|IconFactoryInfo|JunkCodeInfo)\b"
PLATFORM = (r"\b(?:java|javax|uniffi)\.|\b(?:com\.android|com\.google\.(?:common|devrel)|brut|org\.objectweb|org\.apache)\."
            r"|\bio\.ktor\.client\.engine\.|\bkotlin\.io\.path\b|\bClass\.forName\b|::class\.java\b"
            r"|(?<![\w.])System\.(?:getProperty|currentTimeMillis|nanoTime|exit)\b")
IO_CALLS = r"\b(?:File|ProcessBuilder)\s*\(|\bRuntime\.getRuntime\s*\(|\buniffi\.|\b(?:readBytes|writeBytes|deleteRecursively|listRoots)\s*\("
MUTABLE = r"\bvar\b|\b(?:MutableList|MutableSet|MutableMap|ArrayList|SnapshotStateList|MutableState)\s*<"
SCREEN_FORBIDDEN = (r"\b\w*ViewModel\b|\borg\.koin\b|\bkoin(?:Inject|ViewModel)\b|\b\w*Repository\b|\borg\.tool\.kit\.data\."
                    r"|\bcollect(?:AsState(?:WithLifecycle)?|Latest)?\s*(?:\(|\{)|\b(?:StateFlow|SharedFlow|Flow|MutableState)\s*<")


def code_only(source):
    # Preserve positions so failures point to the original line. Strings/comments
    # are ignored; expression bodies in string templates still need human review.
    pattern = r'//[^\n]*|/\*[\s\S]*?\*/|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\''
    return re.sub(pattern, lambda m: re.sub(r"[^\n]", " ", m.group()), source)


def constructor_spans(code):
    for match in re.finditer(r"\bdata\s+class\s+\w+(?:\s*<[^\n{]*?>)?\s*\(", code):
        start = match.end()
        depth = 1
        for end in range(start, len(code)):
            depth += (code[end] == "(") - (code[end] == ")")
            if depth == 0:
                yield start, end
                break


def inspect_source(path, source):
    code = code_only(source)
    relative = path.split("/org/tool/kit/", 1)[-1]
    common = "/commonMain/" in path
    domain = relative.startswith("domain/") or (common and relative in DOMAIN_COMPANIONS)
    screen = relative.startswith("feature/") and (relative.endswith("Screen.kt") or relative in {
        "feature/ui/UI.kt", "feature/ui/FileButton.kt", "feature/ui/UpdateDialog.kt"})
    findings = []

    def reject(rule, pattern, text=code, offset=0):
        for match in re.finditer(pattern, text):
            findings.append({"file": path, "line": code.count("\n", 0, offset + match.start()) + 1,
                             "rule": rule, "match": match.group().strip()})

    reject("legacy-symbol", LEGACY)
    reject("legacy-model", r"\bclass\s+Sign\b|\borg\.tool\.kit\.model\.Sign\b")
    if domain:
        reject("domain-dependency", r"\bandroidx\.|\borg\.(?:jetbrains\.compose|koin)\b|\borg\.tool\.kit\.(?:feature|data|di|platform|utils)\b|" + PLATFORM)
        if not common:
            findings.append({"file": path, "line": 1, "rule": "domain-source-set", "match": "Domain must remain commonMain"})
    if common:
        reject("common-platform-dependency", PLATFORM)
    if screen:
        reject("screen-ownership", SCREEN_FORBIDDEN)
        reject("screen-state-writeback", r"\(\s*\w+(?:UiState|Form|State)\s*\)\s*->\s*Unit")
    if relative.startswith("feature/") and relative.endswith("ViewModel.kt"):
        reject("viewmodel-platform-dependency", r"\borg\.tool\.kit\.data\.|" + PLATFORM)
    if not ("/jvmMain/" in path and relative.startswith(JVM_ADAPTERS)):
        capability_code = code
        if relative in RESOURCE_READS:
            # Exempt only this exact portable resource API, never arbitrary file reads.
            capability_code = re.sub(r"\bRes\.readBytes(?=\s*\()", lambda m: " " * len(m.group()), code)
        reject("platform-capability-location", IO_CALLS, capability_code)
    # Constructor properties form published value models. Private implementation
    # accumulators and Compose-local menu/animation state may remain mutable.
    for start, end in constructor_spans(code):
        reject("published-model-mutability", MUTABLE, code[start:end], start)
    if domain or relative.startswith("model/") or (relative.startswith("feature/") and relative.endswith(("State.kt", "Contract.kt"))):
        reject("published-collection-mutability", r"\b(?:MutableList|MutableSet|MutableMap|ArrayList|SnapshotStateList|MutableState)\s*<")
    return findings


def self_test():
    common = "shared/src/commonMain/kotlin/org/tool/kit/"
    cases = [
        (common + "domain/Bad.kt", "import androidx.compose.runtime.State", "domain-dependency"),
        (common + "domain/Bad.kt", "import java.io.File as Disk", "domain-dependency"),
        (common + "feature/x/XScreen.kt", "import org.koin.compose.koinInject as resolve", "screen-ownership"),
        (common + "feature/x/XScreen.kt", "fun X(vm: OtherViewModel) {}", "screen-ownership"),
        (common + "feature/x/XScreen.kt", "fun X(onChange: (ToolUiState) -> Unit) {}", "screen-state-writeback"),
        (common + "feature/x/XScreen.kt", "fun X() { source.collectAsStateWithLifecycle() }", "screen-ownership"),
        (common + "feature/x/XViewModel.kt", "import org.tool.kit.data.source.Engine", "viewmodel-platform-dependency"),
        (common + "feature/x/XState.kt", "data class X(val items: List<String> = listOf(), var busy: Boolean)", "published-model-mutability"),
        (common + "domain/Bad.kt", "data class X(val items: MutableList<String>)", "published-model-mutability"),
        (common + "feature/x/XRoute.kt", 'fun x() = java.io.File("x").readBytes()', "platform-capability-location"),
        (common + "di/Bad.kt", "viewModel { MainViewModel() }", "legacy-symbol"),
        (common + "model/Sign.kt", "open class Sign {}", "legacy-model"),
    ]
    for path, source, rule in cases:
        assert any(f["rule"] == rule for f in inspect_source(path, source)), (rule, source)
    good = "data class Good(val items: List<String>, val busy: Boolean)\nfun reduce() { var count = 0; count++ }"
    assert not inspect_source(common + "domain/Good.kt", good)
    assert not inspect_source(common + "feature/x/XScreen.kt", '// File("x")\nval label = "MainViewModel"')
    assert not inspect_source("shared/src/jvmMain/kotlin/org/tool/kit/data/source/Engine.kt", 'import java.io.File\nfun x() = File("fixture").readBytes()')
    assert not inspect_source(common + "feature/x/XScreen.kt", "private fun Sign(state: ApkToolUiState) {}\nSign(state)")
    return len(cases) + 4


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    files = sorted(p for folder in PRODUCTION_ROOTS for p in (args.root / folder).rglob("*.kt"))
    findings = [f for path in files for f in inspect_source(path.relative_to(args.root).as_posix(), path.read_text())]
    if not (args.root / PRODUCTION_ROOTS[0] / "org/tool/kit/domain").is_dir():
        findings.append({"rule": "missing-domain", "file": PRODUCTION_ROOTS[0]})
    report = {"passed": not findings, "production_files": len(files), "self_tests": self_test(),
              "legacy_allowlist": LEGACY_ALLOWLIST, "excluded_sources": EXCLUSIONS,
              "domain_companion_files": sorted(DOMAIN_COMPANIONS), "resource_read_exceptions": RESOURCE_READS, "violations": findings,
              "limits": "Lexical guard only; review aliases through helpers, template expressions and cross-function I/O. Domain compilation is a separate Gradle gate."}
    output = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(output)
    print(output, end="")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
