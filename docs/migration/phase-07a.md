# Phase 7A — 图标工厂与 Rust FFI

状态：**Phase 7A 实现与 G0–G6 macOS arm64 验收完成**。尚未开始 Phase 7B。

基于 Phase 6 `498ef364`。引擎提取 `daf0f526`；页面状态与设置事件切换 `f11ef77d`。

## 图像管线

- `GenerateIconsUseCase` 接收一次提交的不可变输入路径、输出字段和图像偏好快照。Domain 不依赖 Compose、File、Rust 或存储 DTO；不会在五密度循环中读取实时偏好。
- `ImageProcessor` 的 JVM DataSource 是五个原 platform expect/actual 函数的唯一业务调用方，IO 包装仍使用原绝对路径、UInt/UByte 转换及异常映射。Rust crate、UDL、JNA/UniFFI、算法参数与依赖版本均未修改。
- 固定顺序 mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi，对应 48/72/96/144/192。PNG 仍是 resizePng→oxipng 或 quantize；JPG/JPEG 仍是 resizeFir→mozJpeg，无损模式 JPEG quality=100f。
- 保留 `outputPath/fileDir/iconDir-density/iconName.<扩展>`、`iconName_resize.<扩展>` 中间文件、原覆盖顺序、大小写扩展接受规则及字符串空白。结果按原密度顺序一次发布；首错停止，保留之前完成的文件与结果。
- 输出会话在 IO 上串行执行，跨服务实例共享 Mutex，覆盖整个请求及清理。finally 仅清理本密度已预留的 `_resize` 文件；不删父目录、其他路径或已完成图片。同步 FFI 返回后才执行清理，取消不误报失败。

## 页面状态与兼容行为

- 窗口级 `IconFactoryViewModel` 唯一持有表单、提交后的偏好、滑块草稿、nullable result、busy 和 Sheet 会话。Entry、根 busy、默认目录订阅一起切换；删除旧 MainViewModel 图标状态/处理/设置桥及可变 `IconFactoryInfo`。
- Screen/Preview/Result/Setting/Compression/Input/Slider 只接收只读切片与字段事件；Route 适配 FileKit、原首项拖拽规则和 Coil 请求。图片存在性检查移至异步依赖，渲染不调用 File.exists。
- Slider Changed 只改精确浮点草稿，Committed 才写原偏好键。保留 PNG target 最低 30、整数四舍五入、JPEG 质量取整及原速度映射：百分比两位小数、speed=11−round(10p)、preset=round(7p)−1。
- 通过编辑区实际挂载事件保持原 remember 生命周期，包括 LazyColumn 和有损区 AnimatedVisibility 重新挂载。Sheet 内外部偏好变化不覆盖正在拖动的草稿；关闭/离页丢弃未提交值，重开从已提交值初始化。
- 原 SheetState、partial/expanded、箭头旋转、动画、文本、字体、布局和通知保留。面板按原页面组合范围离开时关闭，输入与生成结果保留在窗口 VM。
- Submit 在挂起前捕获所有字段与偏好，忙碌时忽略重复提交。关闭窗口或更换输入后，旧结果与通知不能恢复旧图；原生调用可继续完成自己已捕获的输出，但不会覆盖新的 UI 状态。
- 原按钮只检查 outputPath/fileDir/iconName 为空，iconDir 为空仍只是内联错误。FileKit/拖拽仍接受小写 png/jpg/jpeg，拖拽仅看平台过滤后的第一项；重新选择同一输入也清结果。
- 默认目录真实版本变化覆盖本页自选输出；主题、其他偏好、重复值或磁盘确认不覆盖。原五表单联动测试及稍后创建的图标 VM 初始化通过。

本阶段修复的异常收尾有专门验收：旧不支持扩展分支留下 Loading，现在恢复 busy、保留原静默行为和 null result；目录准备/FFI 异常统一收尾；原失败后遗留的 `_resize` 文件现在清理。没有增加新的错误页面或进度布局。

## 自动验证

- 两模块 JVM 编译成功，全量 **168 项测试，0 失败/错误/跳过**，用时 **1 分 45 秒**。
- 23 组真实 native 配置、115 份密度输出与冻结 `498ef364` 旧算法逐项比较：编码字节、解码像素、尺寸和中文/空格路径命名全部一致。覆盖全部 4 种 PNG、6 种 JPEG 算法、两类压缩分支、jpg/jpeg 扩展与默认偏好。
- 用例测试逐项固定 5 密度参数、全部处理步骤的首错停止/部分结果、质量 100 分支、空消息及取消传播。真实损坏图和输出路径为普通文件时失败正常，未破坏阻挡文件，也没有中间文件残留。
- 输出会话测试验证不同实例的串行执行、排队取消、非协作原生调用返回前不清理，以及保留旧产物和非本任务文件。
- VM 测试验证提交快照、重复提交、异常/重试、原校验边界、输入替换和关闭窗口后的迟到结果、预览异步结果、目录版本与编辑区草稿寿命。
- 实际 Compose 指针回放覆盖 PNG 两个滑块手柄、JPEG 质量与压缩速度：拖动期间不改变偏好或版本，松手后写入原物理键。实际 App 回放覆盖生成失败后的空结果、重试后的五图预览与各 Sheet 状态。
- 14 张图标工厂软件图与迁移前、10 张静态页面图与 Phase 6（已与 Phase 0 一致），均零像素差异。22 项视觉资源哈希一致；无裁切、遮罩或容差。

生成按钮回放最初把指针移出了基线中的选中导航按钮，造成四张预览图仅在 x=12..67、y=370..401 的悬停颜色差异。恢复相同指针位置后全部归零；保留初次图与差异记录，没有修改基线或产品样式。

## 官方 MCP 与 G6

官方 MCP 首次亮色签名页重复截图出现 4,947 像素差异（x=596..718、y=288..329，V4 按钮区域）；保留本应用的原图和差异记录，不能据此认定产品回归。第二次采集被其他窗口遮挡，整轮图像 payload 已删除，没有把无关窗口内容纳入证据。取得用户“构建后直接运行”回复后，无遮挡完整回放已通过。

本次官方 Compose Hot Reload 1.2.0 MCP 使用 JetBrains JDK 21.0.6，18 次明暗导航无 UI exception，10 对静态重复 PNG 零差异。与 Phase 0 比较，亮色五页各 113 像素、暗色五页各 115 像素差异，全部位于底部两侧圆角（x≤17 或 x≥782，y≥554）；逐点坐标和原始 PNG 保留，未裁切、遮罩或设置容差，不记为 MCP 跨阶段全图零差异。签名与密钥草稿、连续设置输入保留空白、默认路径联动及自选目录保留全部通过，共 8 项行为检查。原始协议位于 `shared/build/migration/hot-mcp/session-dqji0u7_`，测试进程已结束。

真实 macOS arm64 发行包构建成功，用时 **5 分 48 秒**。ProGuard 7.9.1 保持 optimize/obfuscate 开启，无新增 keep 规则；仅在可丢弃 migrationPackage bundle 隔离内存偏好，实际应用仍为混淆 MainKt。

- 只有一份 Rust 动态库，14 个 Mach-O 段内容与 Cargo 输出一致；225 项许可记录、视觉资源、模板和内嵌工具齐全，实际开源许可窗口正常加载。
- 通过真实 FileKit 选择中文/空格路径的 73×57 合成 PNG/JPG；取消选择保留原图和已生成结果。设置 `fileDir=" res 中文 "`、`iconDir="drawable"`、`iconName="icon fixture"`，输出目录只使用 fixture。
- 实际 UI 执行 PNG 无损、PNG 有损、JPEG quality=85、JPEG 无损模式 quality=100 四组生成。**20 份输出**逐项与冻结旧管线参考文件比较，编码字节、解码像素、尺寸、名称与目录空格全部相同，无 `_resize` 残留；覆盖五个 FFI 函数。
- PNG 默认最低质量 70 对该合成图报原生 `QUALITY_TOO_LOW`，与独立旧管线参考运行一致；应用恢复可操作状态。实际操作范围滑块至最低 0，松手保存后重试成功。
- 实际部分展开/完全展开面板、算法目录选择、五图预览、九页导航、暗色主题及切页后的表单/结果保留通过。完成通知仍为原文本，立即点击“跳转”后 Finder 实际定位到 `jpg-lossless/ res 中文 ` 输出目录。
- 打包 launcher/runtime/混淆 JAR 的独立 JNI/UniFFI/JNA 检查生成 48×48 PNG；随后恢复 MainKt，重新启动验证并正常关闭。

自动化中部分 macOS 操作返回后尚未反映到 AX，重新读取状态后完成操作；输入法/剪贴板造成的未完成名称输入在提交前用逐键输入纠正。原生选择器尚未关闭时误采的图已删除，证据保存增加主窗口检查。AX 展示会省略边界空格，最终文件路径比较确认两侧空格均保留。初次未及时点击通知的跳转未记通过，以最后实际 Finder 路径为准。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译，全量 168 项测试通过 |
| G1 行为 | 五密度参数/顺序、23 组真实 native 配置与 115 份旧算法输出比对通过 |
| G2 UDF | 窗口唯一状态拥有者、不可变请求、纯 Screen、IO/FFI 隔离、旧桥接删除 |
| G3 视觉 | 24 张软件图零差异；MCP 10 对重复图零差异，跨阶段圆角差异保留 |
| G4 交互 | MCP、FileKit/取消、设置面板/滑块、真实生成/重试/跳转及导航/主题通过 |
| G5 异步 | 提交快照、重复提交、取消/迟到结果、清理及跨实例输出互斥通过 |
| G6 发行包 | 实际混淆应用、20 份真实图标一致、五个 FFI 函数、资源/许可/JNI 装载通过 |

完整记录见 [证据目录](evidence/phase-07a)，文件哈希见 [manifest.json](evidence/phase-07a/manifest.json)。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

官方 MCP 沿用冻结场景和最短 2 秒采集等待；G6 使用 migrationPackage 可丢弃的真实混淆产物和内存偏好隔离。

沿用已确认范围：不补录固定动画帧、真实 OS 拖拽、录像或性能基线，不声称完成 Windows/Linux 验收。生成图片和参考输出仅写入临时 fixture。

回滚页面须同时恢复 Entry、root busy 和旧唯一状态拥有者；Rust facade 可继续复用。代码回滚不恢复或重新生成用户已有图片，不能删除非本操作路径，也不能通过调整 Skiko/ZoomImage 掩盖视觉差异。
