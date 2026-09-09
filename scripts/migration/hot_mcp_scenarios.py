"""Replay non-destructive Phase 0 scenarios through official MCP tools.

Only use against :shared:baselineHotRun, whose preferences and output are isolated.
Requires Pillow for exact decoded pixel comparison; never rewrites a baseline.
"""
import json
from pathlib import Path
import shutil
import time

from PIL import Image, ImageChops


def capture_baseline(tool, output):
    def data(name, arguments=None):
        return json.loads(next(x["text"] for x in tool(name, arguments) if x["type"] == "text"))

    def tree():
        def flatten(value, parent_id=None):
            if isinstance(value, list):
                for child in value:
                    yield from flatten(child, parent_id)
            else:
                yield {**{k: v for k, v in value.items() if k != "children"}, "parentId": parent_id}
                for child in value.get("children", []):
                    yield from flatten(child, value["id"])
        return list(flatten(data("get_semantic_tree")))

    def node(label, role=None):
        matches = [n for n in tree() if n.get("text") == label and (role is None or n.get("role") == role)]
        assert len(matches) == 1, (label, matches)
        return matches[0]

    def click(label, role=None):
        target = node(label, role)
        assert "onClick" in target.get("actions", []), target
        assert data("click", {"nodeId": target["id"]})["success"]
        time.sleep(0.8)  # Let the original 600ms transition finish.

    def navigate(label):
        click(label, "Tab")
        assert node(label, "Tab")["selected"]

    def enter(label, value):
        assert data("type_text", {"nodeId": node(label)["id"], "text": value})["success"]
        assert node(label).get("editableText", "") == value

    def screenshot(path):
        assert data("status")["connected"]
        source = Path(next(x["saved_path"] for x in tool("take_screenshot") if x["type"] == "image"))
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, path)

    for _ in range(20):
        if data("status")["connected"]:
            break
        time.sleep(0.25)  # MCP discovers the app's PID/port asynchronously.
    else:
        raise AssertionError("Start :shared:baselineHotRun first")
    restarted = data("restart", {"timeout_seconds": 30})
    assert restarted.get("reconnected"), restarted
    time.sleep(2)
    assert data("get_ui_error")["hasError"] is False
    report = {"windows": data("list_windows"), "captures": [], "checks": []}
    pages = [
        ("signature-information", "签名信息"), ("apk-information", "APK信息"),
        ("apk-signature", "APK签名"), ("keystore-generation", "签名生成"),
        ("apk-tool", "APK生成"), ("junk-code", "垃圾代码"),
        ("icon-factory", "图标生成"), ("cleaner", "缓存清理"), ("settings", "设置")
    ]
    static_pages = {"apk-signature", "keystore-generation", "apk-tool", "junk-code", "settings"}
    for theme in ["light", "dark"]:
        navigate("设置")
        click("亮色模式" if theme == "light" else "暗色模式")
        for name, label in pages:
            navigate(label)
            first = output / theme / f"{name}.png"
            second = output / theme / f"{name}-repeat.png"
            screenshot(first)
            time.sleep(0.25)
            screenshot(second)
            nodes = tree()
            first.with_suffix(".json").write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + "\n")
            with Image.open(first) as a, Image.open(second) as b:
                assert a.size == b.size
                difference = ImageChops.difference(a.convert("RGB"), b.convert("RGB"))
                changed = sum(pixel != (0, 0, 0) for pixel in difference.getdata())
                report["captures"].append(dict(theme=theme, page=name, size=a.size,
                    repeated_changed_pixels=changed, bounds=difference.getbbox()))
                if name in static_pages:
                    assert changed == 0, f"Unstable static capture {theme}/{name}: {changed} changed pixels"
            assert data("get_ui_error")["hasError"] is False
            print(f"{theme}/{name}: repeated capture changed {changed} pixels", flush=True)

    navigate("APK签名")
    enter("输出文件前缀(选填)", "phase0-mcp-draft")
    navigate("签名生成")
    enter("密钥文件名称", "phase0-mcp-test.jks")
    navigate("APK签名")
    assert node("输出文件前缀(选填)")["editableText"] == "phase0-mcp-draft"
    screenshot(output / "interactions/signing-returned.png")
    navigate("签名生成")
    assert node("密钥文件名称")["editableText"] == "phase0-mcp-test.jks"
    screenshot(output / "interactions/keystore-returned.png")
    report["checks"].extend(["18 navigation selections", "18 captures without UI exceptions", "10 static pairs have zero changed pixels",
                             "signing draft retained", "keystore filename retained"])
    report["final_status"] = data("status")
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(f"PASS: {report['checks']}. Evidence: {output}", flush=True)
