# Phase 6 — ApkTool 空包构建

状态：**完成本阶段实现及本机 G0–G6 验证**。Phase 7 尚未开始。

基于 Phase 5 `0c35593d`。管线提取 `8d0eca6e`，工作目录迁移 `eeb1ead7`，页面拥有者切换 `52187dd9`。

## 构建管线与工作目录

- `BuildApkUseCase` 接收一次提交的不可变请求，依次执行 decode、Manifest/package/sdk、strings app_name、可选 icon 复制、apktool.yml、build、可选共享 `SignApkUseCase`。Domain 不依赖 Compose、File 或 Apktool 类型；JVM session 在 IO 执行原库与 XML 算法。
- 保留 bundled `apktool.apk` 生成自定义空包的产品范围、`Config(versionName)` 与 analysis/forced/debuggable 配置。五个 mipmap 密度原样复制同一源图；没有缩放。
- 提取提交先对旧固定工作目录串行保护，且拒绝清理不属于本次操作的已有目录。独立提交再改为系统临时缓存下的随机任务目录，decoded 与 framework 都位于其中。安装目录仅供读取。
- 同一进程内的构建与后续签名由共享 Mutex 串行化，包括不同窗口容器，避免同一输出并发冲突。工作目录分配、使用和 finally 清理处于同一 IO 上下文；取消仍清理当前操作拥有的整个目录，保留缓存根、其他任务文件和输出。
- 同步 Apktool/签名库调用开始后可能继续运行；等待该调用返回后再清理工作目录。取消不转为失败通知，不发布迟到结果，也不自动回滚已写出的 APK。

## 页面契约与兼容行为

- 窗口级 `ApkToolViewModel` 唯一持有不可变 `ApkToolForm`、组合的 `SigningCredentialsUi`、异步校验与 busy。原页面没有持久结果面板，仍以原 Snackbar 显示完成结果。
- Screen、SDK/版本输入、签名区域、别名和按钮仅收只读状态及字段事件。Route 适配原 FileKit 和拖拽；布局、文本、动画、全局 Loading 与局部展开状态寿命保持。
- SDK 和 versionCode 保留空字符串或原数字正则；超大数字仍在构建前执行原 `toInt()` 并映射原异常。所有字符串保持空白和原值，不新增自动修整或包名规则。
- 开关签名均执行原“清空 key path”规则；如果 key path 本来就是空字符串，不额外清空其余凭据。改变实际 key path 清两个密码、aliases 和索引；相同路径保留。改变 alias 清 alias 密码，相同索引保留；store password 改动仍保留旧 alias 密码。
- 拖拽经平台对整个列表做存在性过滤后，仅处理第一项：image 或 key。保留原大小写/扩展名接受规则，不改成扫描后续匹配文件。
- 默认输出目录由新拥有者订阅同一偏好版本；真实默认值变化覆盖自选目录，无关设置与重复默认值不覆盖。原五个表单联动回归继续通过。
- Submit 同步捕获全部表单和签名偏好，忙碌时忽略重复 Submit。路径、alias 和密码的旧结果受版本保护；关闭窗口使 operation 失效，旧 finally 不发布结果。
- Entry、根 busy、默认目录订阅同时切换；删除旧 MainViewModel 的 ApkTool 状态/执行入口、Phase 5 签名桥、最后的旧密钥校验桥及生产 `Sign`/`ApkToolInfo` 继承模型。旧 setter 仅作为测试 oracle 保留。

以下旧产品行为明确保留，未作为重构顺带修复：

- 输出仍为 `$appName.apk`，可选签名默认 V3。完成消息中的大小和“跳转”目标基于未签名原包。
- 可选签名失败、签名输出已存在或返回的签名产物不存在，均作为独立内部 `SignApkOutcome` 返回；默认 UI 仍显示原构建完成消息。
- Generate 原校验先检查 output/icon 错误或未完成校验；签名开启时还等待 key/凭据校验。已完成的密钥路径/密码错误保持原内联显示，但原 Generate 不据此拒绝构建，空凭据也仍进入可选签名失败分支。

## 自动验证

- 两模块 JVM 编译成功，全量 **144 项测试，0 失败/错误/跳过**，耗时 **1 分 15 秒**。
- 用例测试固定完整执行顺序、无 icon/禁用签名分支、共享签名请求、每阶段失败截断与清理、版本溢出、可选签名结果及取消传播。
- 工作目录测试验证不同服务实例的串行约束、唯一目录、等待队列取消、运行取消、失败清理，以及原输出和非本任务目录内容不变。
- 真实 bundled 模板对照冻结的旧算法：自定义包名、SDK、版本、中文/空格应用名的 aapt2 badging 与完整 Manifest 相同；五个编译后 icon payload 相同，均保留源图 **17×13** 尺寸与像素。默认 icon 的生成也通过。
- 真实未签名包由 ApkVerifier 确认为未签名；V3 输出独立验证 V1/V2/V3 全部有效。重复构建同一输出、错误签名密码及实际只读安装目录测试通过，结束后没有操作缓存残留。
- VM 测试覆盖完整字段/偏好快照、重复提交、原消息/校验优先级、失败重试、首项拖拽规则、迟到路径/alias/密码、切换签名开关与窗口清理。
- 实际 App/Compose 测试覆盖展开签名区、alias 下拉、切页草稿、错误表单与修正后按钮提交，确认请求使用 V3、选中 alias 和原文件名。
- 10 张静态软件图与 Phase 5（已与 Phase 0 一致）、6 张 ApkTool 表单图与本阶段切换前，均零像素差异。无遮罩、裁切或容差；22 项视觉资源哈希一致。

新增 UI 回放最初有两项超时：错误 Snackbar 尚在时就重试点击。测试改为等待原通知消失后继续，并隔离构建工作目录依赖；两项针对性回归及随后全量测试通过。真实模板核验最初因测试 aapt2 路径错误失败，修正测试资源定位后通过。保留初次失败日志。

## 官方 MCP 与 G6

官方 Compose Hot Reload 1.2.0 MCP 使用 JetBrains JDK 21.0.6，本轮完整通过：18 次明暗导航无 UI exception，10 对静态重复 PNG 零差异；签名/密钥草稿、设置连续输入、默认路径联动及自选目录保留通过。

与 Phase 0 MCP 图的跨阶段比较：亮色四个静态页各 109 像素、设置 110，暗色五页各 113 像素差异，全部位于两侧底部圆角 y≥554。原图与逐点坐标均保留，没有裁切、遮罩或设容差，不声称 MCP 跨阶段全窗口零差异。此次原始协议位于 `shared/build/migration/hot-mcp/session-cs4pche5`。

真实 macOS arm64 发行包构建成功，用时 **5 分 35 秒**。ProGuard 7.9.1 保持 optimize/obfuscate 开启，没有新增 keep 规则。仅在可丢弃的 migrationPackage bundle 中注入内存偏好工厂，应用代码仍为实际混淆 MainKt。

- bundled `apktool.apk` 与原资源哈希一致，Apktool 内嵌构建工具与 aapt2 均存在；225 项许可记录和开源许可窗口正常。只有一份 Rust 动态库，14 个 Mach-O 段内容与 Cargo 输出一致。
- 实际 FileKit 选择中文/空格图标路径与合成 JKS，取消图标选择保留原值；原生 Cmd+A 替换输出路径，键盘填写 SDK/版本，粘贴中文应用名。签名开关清路径、重新加载 `phase6` alias 及密钥密码通过。
- 实际 UI 分别生成 `Phase6 空包.apk`、`Phase6 签名.apk` 与可选输出 `Phase6 签名-sign.apk`。两个原包由 ApkVerifier 确认为未签名，签名产物的 V1/V2/V3 全部有效，V4 为 false。
- aapt2 检查三份产物的 package `org.fixture.phase6`、minSdk 23、targetSdk 32、code 12、version 2.3 和对应中文应用名。五种密度的编译图标全部保留源图 **37×29** 尺寸与像素。
- 原全局 Lottie Loading、3.56 MB 完成提示保留。签名完成后真实点击“跳转”，Finder 路径定位为 `Phase6 签名.apk`，仍指向未签名原包。
- 九页导航、暗色主题与返回页面保留输出路径、全部构建参数和签名草稿。真实打包 launcher/runtime/混淆 JAR 的 UniFFI/JNA 调用生成 **48×48 PNG**；随后恢复 MainKt 并重新启动确认，测试窗口已正常关闭。

独立验证器首次直接执行 aapt2 时遇到资源文件无 executable 位；改为在 fixture 内复制字节完全相同的工具并赋执行位，未修改发行包内容。中文模拟逐键输入不完整，提交前通过原生粘贴纠正。首次未签名通知的“跳转”在点击前消失，后续签名构建在检测到通知时立即点击并验证。一次 Computer Use 管道暂时断开后重新读取状态继续，未改产品实现。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译，144 项测试通过 |
| G1 行为 | setter、管线顺序、真实模板与签名产物、五密度原图复制通过 |
| G2 UDF | 窗口唯一拥有者、不可变请求、纯 Screen、旧入口与桥接删除 |
| G3 视觉 | 16 张软件图零差异；MCP 10 对重复图零差异，跨阶段圆角差异保留 |
| G4 交互 | MCP、FileKit/取消、键盘、开关、alias、真实生成/跳转、导航/主题通过 |
| G5 异步 | 乱序、提交快照、重复提交、取消、窗口清理及串行私有目录通过 |
| G6 发行包 | 混淆应用真实构建、模板/工具/许可、独立产物校验和 Rust 调用通过 |

完整记录见 [证据目录](evidence/phase-06)，文件哈希见 [manifest.json](evidence/phase-06/manifest.json)。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

官方 MCP 沿用 Phase 4A 的冻结回放及最短 2 秒采集等待；G6 沿用 Phase 1 的 migrationPackage 隔离打包和内存偏好脚本。

沿用已确认范围：不补录固定动画帧、真实 OS 拖拽、录像或性能基线，不声称完成 Windows/Linux 验收。测试私钥与 APK 仅保存在 build/临时合成 fixture，不提交。

页面回滚须同时恢复 Entry、根 busy 与旧唯一状态拥有者，共享 SignApkUseCase 保留；工作目录提交可独立回滚。代码回滚不撤销已经生成或覆盖的 APK，不能清理非当前 operation 拥有的文件。
