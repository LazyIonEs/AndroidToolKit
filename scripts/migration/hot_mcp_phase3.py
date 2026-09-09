"""Phase 3 settings/path ownership checks after the frozen navigation capture."""
import json
from pathlib import Path
import shutil
import time
from hot_mcp_scenarios import capture_baseline


def smoke_phase3(tool, output, root):
    capture_baseline(tool, output)

    def data(name, arguments=None):
        return json.loads(next(x["text"] for x in tool(name, arguments) if x["type"] == "text"))

    def tree():
        def flat(value):
            if isinstance(value, list):
                for child in value:
                    yield from flat(child)
            else:
                yield {k: v for k, v in value.items() if k != "children"}
                for child in value.get("children", []):
                    yield from flat(child)
        return list(flat(data("get_semantic_tree")))

    def node(label, role=None):
        matches = [n for n in tree() if n.get("text") == label and (role is None or n.get("role") == role)]
        assert len(matches) == 1, (label, matches)
        return matches[0]

    def click(label, role=None):
        assert data("click", {"nodeId": node(label, role)["id"]})["success"]
        time.sleep(.8)

    def navigate(label):
        click(label, "Tab")
        assert node(label, "Tab")["selected"]

    def enter(label, value):
        assert data("type_text", {"nodeId": node(label)["id"], "text": value})["success"]
        assert node(label).get("editableText") == value

    def capture(name):
        assert not data("get_ui_error")["hasError"]
        source = Path(next(x["saved_path"] for x in tool("take_screenshot") if x["type"] == "image"))
        target = output / "interactions" / (name + ".png")
        shutil.copyfile(source, target)
        target.with_suffix(".json").write_text(json.dumps(tree(), ensure_ascii=False, indent=2) + "\n")

    fields = [("APK签名", "输出路径"), ("签名生成", "密钥输出路径"),
              ("APK生成", "APK输出路径"), ("垃圾代码", "AAR输出路径")]
    for index, (page, field) in enumerate(fields):
        navigate(page)
        enter(field, str(root / f"shared/build/migration/fixtures/phase3-custom-{index}"))

    navigate("设置")
    path = str(root / "shared/build/migration/fixtures/phase3-output")
    assert Path(path).is_dir()
    for suffix in ["-phase3-A", "-phase3-B", "-phase3-A", " -phase3-final "]:
        enter("签名后缀", suffix)
    enter("默认输出路径", path)
    click("亮色模式")
    capture("settings-final-inputs")
    for index, (page, field) in enumerate(fields):
        navigate(page)
        assert node(field)["editableText"] == path
        capture(f"default-path-{index}")
    navigate("APK签名")
    custom = str(root / "shared/build/migration/fixtures/phase3-custom-kept")
    enter("输出路径", custom)
    navigate("设置")
    enter("签名后缀", "-phase3-unrelated")
    click("暗色模式")
    assert node("默认输出路径")["editableText"] == path
    navigate("APK签名")
    assert node("输出路径")["editableText"] == custom
    capture("custom-path-retained")
    report_file = output / "report.json"
    report = json.loads(report_file.read_text())
    report["checks"].extend(["successive settings inputs retain original whitespace",
        "four visible legacy output fields receive the changed default (all five covered by VM tests)",
        "theme and suffix changes do not overwrite a custom signing folder"])
    report["final_status"] = data("status")
    report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print("PASS: " + str(report["checks"]), flush=True)
