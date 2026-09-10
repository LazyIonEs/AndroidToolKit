"""Phase 7B: real tiny AAR generation in the isolated official MCP application."""
import io
import json
from pathlib import Path
import re
import shutil
import tempfile
import time
import zipfile
from decimal import Decimal, ROUND_DOWN, ROUND_HALF_UP

from hot_mcp_phase3 import smoke_phase3


def smoke_phase7b(tool, output, root):
    smoke_phase3(tool, output, root)

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

    def node(label):
        found = [n for n in tree() if n.get("text") == label]
        assert len(found) == 1, (label, found)
        return found[0]

    def click(label, wait=True):
        assert data("click", {"nodeId": node(label)["id"]})["success"]
        if wait:
            time.sleep(.8)

    def enter(label, value):
        assert data("type_text", {"nodeId": node(label)["id"], "text": value})["success"]
        assert node(label).get("editableText") == value

    def capture(name):
        assert not data("get_ui_error")["hasError"]
        source = Path(next(x["saved_path"] for x in tool("take_screenshot") if x["type"] == "image"))
        path = output / "junk" / (name + ".png")
        path.parent.mkdir(exist_ok=True)
        shutil.copyfile(source, path)
        path.with_suffix(".json").write_text(json.dumps(tree(), ensure_ascii=False, indent=2) + "\n")

    def completed(name):
        for _ in range(30):
            texts = [n.get("text", "") for n in tree()]
            messages = [t for t in texts if t.startswith("构建结束：成功，文件大小：")]
            if messages:
                capture(name)
                return messages[0]
            time.sleep(.1)
        raise AssertionError("No build completion notification")

    def measured_text(size):
        # Frozen macOS formatFileSize(scale=2): decimal divisor, first DOWN then HALF_UP.
        value = Decimal(size) / Decimal(1000)
        value = value.quantize(Decimal('.01'), rounding=ROUND_DOWN)
        if value < 1:
            return f"{value}B"
        for unit in ['KB', 'MB', 'GB']:
            next_value = (value / Decimal(1000)).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
            if next_value < 1:
                return f"{value}{unit}"
            value = next_value
        return f"{value}TB"

    def inspect_archive(path, expected_package=None):
        with zipfile.ZipFile(path) as aar:
            assert aar.testzip() is None
            manifest = aar.read('AndroidManifest.xml').decode('utf-8')
            package = re.search(r'package="([^"]+)"', manifest).group(1)
            if expected_package:
                assert package == expected_package
            assert path.name == 'junk_' + package.replace('.', '_') + '_TT2.2.0.aar'
            assert 'R.txt' in aar.namelist()
            assert any(n.startswith('res/layout/') and n.endswith('.xml') for n in aar.namelist())
            with zipfile.ZipFile(io.BytesIO(aar.read('classes.jar'))) as jar:
                classes = [n for n in jar.namelist() if n.endswith('.class')]
                assert classes and jar.testzip() is None
                assert all(n.startswith(package.replace('.', '/') + '/') for n in classes)
                assert all(jar.read(n).startswith(b'\xca\xfe\xba\xbe') for n in classes)
        return dict(path=str(path), package=package, bytes=path.stat().st_size, classes=len(classes))

    fixture = Path(tempfile.mkdtemp(prefix='phase7b-mcp-', dir=root / 'shared/build/migration/fixtures'))
    sibling = fixture / 'unrelated.txt'
    sibling.write_text('keep')
    batch_dir = fixture / ' batch 中文 '
    batch_dir.mkdir()
    (batch_dir / 'stale-fixture.txt').write_text('owned stale fixture')
    click('垃圾代码')
    enter('AAR输出路径', str(fixture))
    for label, value in [('包名', 'org.fixture.phase7b'), ('后缀', 'part.one'), ('资源前缀', 'fixture_'),
                         ('包的数量', '1'), ('每个包里 activity 的数量', '1')]:
        enter(label, value)
    assert node('AAR 名称')['editableText'] == 'junk_org_fixture_phase7b_part.one_TT2.2.0.aar'
    capture('single-form')
    click('多AAR模式')
    for label, value in [('多AAR输出文件夹名称', ' batch 中文 '), ('需要生成的AAR包数量', '2'), ('包数量（最小）', '1'),
                         ('包数量（最大）', '1'), ('每个包里 activity 的数量（最小）', '1'), ('每个包里 activity 的数量（最大）', '1')]:
        enter(label, value)
    capture('multi-form')
    click('单AAR模式')
    click('APK信息')
    click('垃圾代码')
    assert node('后缀')['editableText'] == 'part.one'
    click('开始生成', wait=False)
    single_message = completed('single-complete')
    single_file = fixture / 'junk_org_fixture_phase7b_part_one_TT2.2.0.aar'
    single = inspect_archive(single_file, 'org.fixture.phase7b.part.one')
    assert measured_text(single['bytes']) in single_message
    time.sleep(5)  # Let the unchanged short notification finish before the next operation.
    click('多AAR模式')
    assert node('多AAR输出文件夹名称')['editableText'] == ' batch 中文 '
    assert node('需要生成的AAR包数量')['editableText'] == '2'
    click('开始生成', wait=False)
    batch_message = completed('multi-complete')
    archives = sorted(batch_dir.glob('*.aar'))
    assert len(archives) == 2 and not (batch_dir / 'stale-fixture.txt').exists()
    batch = [inspect_archive(p) for p in archives]
    assert len({p['package'] for p in batch}) == 2
    assert measured_text(sum(p['bytes'] for p in batch)) in batch_message
    assert sibling.read_text() == 'keep' and single_file.is_file()
    record = dict(fixture=str(fixture), single=single, batch=batch, singleMessage=single_message, batchMessage=batch_message,
                  checks=['both mode drafts retained across modes and navigation', 'single display name versus real filename preserved',
                          'real single and two batch AARs structurally valid', 'exact measured size text matches output bytes',
                          'batch rebuild removes only its fixture subdirectory', 'original sibling and single output preserved'])
    (output / 'junk/report.json').write_text(json.dumps(record, ensure_ascii=False, indent=2) + '\n')
    print('PASS: Phase 7B real generation ' + str(record['checks']), flush=True)
