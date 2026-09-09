# 软件渲染观察记录

状态：**无损 PNG 已采集，G3 尚未通过**。这些文件与上级目录中的原生 JPEG 是两套独立证据。

`ComposeBaselineTest` 直接运行未修改的 `App()`，使用隔离内存偏好；没有替换页面、主题、字体或业务 ViewModel。
Compose Desktop 1.12.0 的 `runDesktopComposeUiTest` 提供独立 owner 和测试场景。
窗口内容区为 800×572 px、density/fontScale 为 1，语言 zh_CN；不包含系统标题栏。
截图来自 `captureToImage()` 的像素，通过 ImageIO 直接写入 PNG，没有 JPEG 中间步骤、缩放或遮罩。

- `light/`、`dark/`：逐一点击九个导航入口并断言选中，保存画面和 semantics 文本。
- `interactions/`：输入 `phase0-draft`、`phase0-test.jks`，切页后断言原值保留，再保存画面。
- `manifest.json`：记录环境条件、PNG SHA-256 与尚未控制的因素。

运行器默认 `mainClock.autoAdvance=true`，无限动画会被测试策略取消；Compottie 资源还可能异步加载。
例如 Cleaner 截图中的动画未完整呈现，所以不能拿此图验收原 Lottie。
磁盘容量依旧由旧实现读取实机数据，光标相位未固定。未断言跨运行像素相等，也未设置容差。
这些图片可以核对内容区布局和定位后续差异；尚不能作为原生九页或固定动画帧的最终 golden。

回放：

```sh
./gradlew --offline --no-daemon :shared:jvmTest --tests 'org.tool.kit.migration.ComposeBaselineTest' --console=plain
```

新图片写入 `shared/build/migration/rendered/`，不会覆盖已提交的观察记录。测试图中的输出路径是当前 checkout 下的隔离 fixture 路径；跨 checkout 比较之前必须保持路径文字一致。
完整回归应省略 `--tests`，最近一次完整运行共 19 项通过，证据在 `../../evidence/phase0-ui-tests-success.txt`。

原生验收下一步仍需在隔离测试包中完成：真实键盘输入及 Tab/selection、文件选取与拖拽、辅助窗口和关闭、V01–V27 的动画与录像。
目前桌面工具只能返回 JPEG，原生键盘/剪贴板输入未成功，不能靠软件测试将这些项标记通过。
