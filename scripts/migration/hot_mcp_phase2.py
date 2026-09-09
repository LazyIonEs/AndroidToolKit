"""Phase 2 UI smoke against the isolated JBR 21 baselineHotRun process only."""
import json
from pathlib import Path
import shutil
import time


def smoke_phase2(tool, output, root):
    def data(name, arguments=None):
        return json.loads(next(x["text"] for x in tool(name, arguments) if x["type"] == "text"))

    def tree():
        def flatten(value, parent=None):
            if isinstance(value, list):
                for child in value:
                    yield from flatten(child, parent)
            else:
                yield {**{k: v for k, v in value.items() if k != "children"}, "parentId": parent}
                for child in value.get("children", []):
                    yield from flatten(child, value["id"])
        return list(flatten(data("get_semantic_tree")))

    def node(label, role=None):
        matches = [n for n in tree() if n.get("text") == label and (role is None or n.get("role") == role)]
        assert len(matches) == 1, (label, matches)
        return matches[0]

    def click(label, role=None):
        assert data("click", {"nodeId": node(label, role)["id"]})["success"]
        time.sleep(0.8)

    def navigate(label):
        click(label, "Tab")
        assert node(label, "Tab")["selected"]

    def enter(label, value):
        assert data("type_text", {"nodeId": node(label)["id"], "text": value})["success"]

    def wait_for(predicate):
        for _ in range(40):
            if predicate():
                return
            time.sleep(0.1)
        raise AssertionError("UI condition did not become true")

    def capture(relative):
        assert not data("get_ui_error")["hasError"]
        src = Path(next(x["saved_path"] for x in tool("take_screenshot") if x["type"] == "image"))
        dest = output / relative
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dest)
        dest.with_suffix(".json").write_text(json.dumps(tree(), ensure_ascii=False, indent=2) + "\n")

    def scroll_list_containing(label, index):
        nodes = {n["id"]: n for n in tree()}
        parent = nodes[node(label)["parentId"]]
        # Input semantics are immediate children of the original LazyColumn.
        assert data("scroll_to_index", {"nodeId": parent["id"], "index": index})["success"]
        time.sleep(0.4)

    wait_for(lambda: data("status")["connected"])
    assert data("restart", {"timeout_seconds": 30})["reconnected"]
    time.sleep(2)
    pages = [("signature-information", "签名信息"), ("apk-information", "APK信息"),
             ("apk-signature", "APK签名"), ("keystore-generation", "签名生成"),
             ("apk-tool", "APK生成"), ("junk-code", "垃圾代码"),
             ("icon-factory", "图标生成"), ("cleaner", "缓存清理"), ("settings", "设置")]
    for theme, label in [("light", "亮色模式"), ("dark", "暗色模式")]:
        navigate("设置")
        click(label)
        for slug, label in pages:
            navigate(label)
            capture(f"{theme}/{slug}.png")
            print(f"PASS: {theme}/{slug}", flush=True)

    navigate("设置")
    draft_output = str(root / "shared/build/migration/fixtures/phase2-output")
    assert Path(draft_output).is_dir()
    enter("默认输出路径", draft_output)
    enter("签名后缀", "-phase2-draft")
    click("亮色模式")
    assert node("默认输出路径")["editableText"] == draft_output
    assert node("签名后缀")["editableText"] == "-phase2-draft"
    navigate("APK签名")
    navigate("设置")
    assert node("默认输出路径")["editableText"] == draft_output
    assert node("签名后缀")["editableText"] == "-phase2-draft"
    capture("interactions/settings-returned.png")
    print("PASS: settings drafts survive theme and navigation", flush=True)

    navigate("APK签名")
    fixture = root / "shared/build/migration/fixtures/phase2-mcp.keystore"
    assert fixture.is_file(), "Prepare the fixture-only keystore first"
    enter("密钥文件路径", str(fixture))
    enter("密钥库密码", "fixture-only")
    wait_for(lambda: node("密钥别名").get("editableText") == "phase2")
    enter("密钥库密码", "wrong-fixture-password")
    wait_for(lambda: not node("密钥别名").get("editableText"))
    capture("interactions/signing-invalid-password.png")
    enter("密钥库密码", "fixture-only")
    wait_for(lambda: node("密钥别名").get("editableText") == "phase2")
    scroll_list_containing("密钥库密码", 5)
    enter("密钥密码", "fixture-only")
    time.sleep(0.4)
    capture("interactions/signing-valid-password.png")
    report = {"checks": ["18 navigation/theme selections without UI exceptions",
        "settings output and suffix survive theme change and navigation",
        "valid store password loads original alias", "wrong store password clears aliases",
        "new valid password restores aliases", "alias password field accepts input"],
        "status": data("status"), "windows": data("list_windows")}
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("PASS: " + str(report["checks"]), flush=True)
