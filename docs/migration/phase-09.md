# Phase 9 — 删除旧架构与完成平台边界

状态：**完成**。按已确认范围通过 G0–G5、干净构建及运行验收。

基线：Phase 8 `c0f3ee69`。旧架构删除：`9a469fec`；平台迁移与自动门禁：`6354fb79`；模型成员可变性规则补充：`b5902b61`。

## 所有权核对与旧对象退场

所有生产 Entry 均接收 Root 从同一窗口 ViewModelStore 获取的专用 VM；没有 MainViewModel fallback、镜像状态或双写桥。Koin 按 VM 类型/key 保持窗口内唯一实例。完整路径台账见 `evidence/phase-09/ownership.json`。

| 页面/拥有者 | 唯一 VM | 契约文件 | Route / 纯 Screen |
| --- | --- | --- | --- |
| 根外壳 | AppViewModel | 同文件的 AppUiState | App 根编排 |
| 设置 | SettingsViewModel | SettingsContract | SettingsRoute / SettingsScreen |
| 更新 | UpdateViewModel | UpdateContract | UpdateRoute / UpdateDialog |
| 密钥生成 | KeyStoreGenerationViewModel | KeyStoreGenerationContract | KeyStoreGenerationRoute / KeyStoreGenerationScreen |
| 签名信息 | SignatureInformationViewModel | SignatureInformationContract | SignatureInformationRoute / SignatureInformationScreen |
| APK 信息 | ApkInformationViewModel | ApkInformationContract | ApkInformationRoute / ApkInformationScreen |
| APK 签名 | ApkSigningViewModel | ApkSigningState | ApkSigningRoute / ApkSigningScreen |
| APK 生成 | ApkToolViewModel | ApkToolState | ApkToolRoute / ApkToolScreen |
| 图标生成 | IconFactoryViewModel | IconFactoryState | IconFactoryRoute / IconFactoryScreen |
| 垃圾代码 | JunkCodeViewModel | JunkCodeState | JunkCodeRoute / JunkCodeScreen |
| 缓存清理 | CleanerViewModel | CleanerState | CleanerRoute / CleanerScreen |

`State.kt` 与 `Contract.kt` 都承载各自的不可变状态和明确事件；UpdateDialog 是更新功能的纯 Screen。App 是根编排入口，不套用叶子 Screen 禁止注入/订阅的规则。原有窗口 owner、切页状态保留、密码 dialog / 图标 Sheet 的会话重置、任务取消和效果消费边界延续各阶段契约。

本阶段删除 `MainViewModel.kt`、旧 `UIState.Success(Any)`、旧 DI binding、Compose `MutableState.update` 扩展、零调用的 AWT Copy 重载，以及已经无人调用的 FileInputWithPicker、FolderInputWithPicker、带业务选择器的 FileButton / DirectoryButton 桥。剪贴板格式化纯函数继续使用现有四种规则，真实剪贴板仍由 JvmClipboardWriter 实现。

旧 Sign / setter 模型、PendingDeletionFile、IconFactoryInfo、JunkCodeInfo、collectOutputPath、MainViewModel loading helper 和旧 SignatureRepository 已在此前阶段退场，本阶段再次核对零生产引用。新的 Domain SignatureRepository 与 JvmSignatureRepository 是实际使用的接口/实现，继续保留。测试中的冻结 legacy oracle 只用于行为对照，不会进入生产产物；生产 legacy allowlist 为空。

## 逐族平台迁移

- **生成器**：AndroidJunkGenerator、AndroidJunkBytecodeInject、MultiAarGenerator、ParallelJunkWork 与依赖其概率常量的 JunkSizePredictor 移至 `jvmMain/data/generator`，ASM 依赖同步移动。Domain 仍通过 JunkCodeRepository / JunkSizeEstimator 调用。五个文件去除 package/import/空白后正文完全一致。
- **文件系统与偏好**：文件、ZIP、目录打开、资源路径、日志文件及 SecureRandom 实现移至 JVM。文件后缀和扩展名规则仍在 commonMain。PreferencesStorage 保持公共接口，PreferencesDataSource 的物理读写移至 JVM；公共 dataModule 依赖接口，desktopDataModule 提供实际存储。原 key、serializer、读写 dispatcher、默认下载目录和迁移兼容规则不变。
- **APK 工具**：现有进程、签名、密钥、APK 构建适配器继续位于 JVM；ArscBlamer 及 Android 工具、apktool、Guava、codec、本地 JAR 依赖完成归位。ArscBlamer 正文一致。DesktopToolResources 负责真实模板路径，ConfigConstant 只保留公共 UI/图标值常量。
- **Rust**：UniFFI 生成源码、JNA 与所有实际 FFI 调用仅在 JVM。删除无 common 调用者的 resize/quantize/压缩 expect 桥，保留现有 JVM 包装函数、默认参数、异常映射和 Domain ImageProcessor 接口。
- **HTTP**：Apache5、Ktor transport 及依赖移至 JVM。GitHub / Download transport DTO 同步移动，assets 发布为只读 List；公共 UpdateRepository、UpdateRelease / UpdateAsset 不暴露 transport DTO。
- **原生 UI 适配**：拖拽适配器完成全列表存在性过滤后，向 Route 传递相同顺序的绝对路径 String，File / Path 不再跨越 common 边界。Coil File/Skia 转换在 JVM，原 ORIGINAL 尺寸、禁用缓存和 fetcher 配置保持一致。Cleaner 的日期、BigDecimal 大小/百分比格式只做函数提取，算法和文案没有变化。
- **导航与日志**：JVM 反射 serializer 从 NavigationState 提出，旧二进制类名、descriptor 和 payload 格式不变；九个旧导航 JSON 均可回读并逐字重写。App 启动日志通过平台函数保留原 logger 名称和文本。明确使用 `jvmMain api(logging)` 供 composeApp 的直接 KotlinLogging import 使用，SLF4J/Logback 和 IntelliJ JVM 依赖同步归位。
- **Domain 值对象**：UserData 与 DarkThemeConfig 分文件，持久化/Domain 所需值对象不再连带主题 StringResource 依赖。commonMain 中不再有 java/javax、Path、Class.forName 或 UniFFI 引用。

各族分别编译后再继续。生成器首次编译指出估算器仍依赖生成器常量，补齐同族移动后通过。未改内容的 DownloadResult 跨 source set 移动触发一次增量解析失效；非增量编译和之后的干净构建均通过。两项测试 oracle 的 import 随实现归位修正。所有初次与复跑日志保留。

## 自动架构约束

`checkArchitecture` 接入 `:shared:check`，扫描 178 个生产 Kotlin 文件，19 个规则正反例自测通过：

- Domain 禁止 Compose、Koin、Data/Feature/平台实现和 JVM 工具依赖。
- 纯 Screen 禁止 VM、Koin、Repository、Flow 收集、MutableState 参数和整份状态回传；VM 禁止直接依赖 Data/JVM 实现。
- 发布 data class 的构造属性及类体成员禁止 var；公开集合禁止 MutableList / ArrayList / SnapshotStateList 等可变类型。方法体局部计数器不会误判为发布属性。
- JVM 能力只允许在已列出的 Data、平台、DI、原生输入、导航反射和展示格式适配位置出现。
- 禁止旧架构符号；legacy allowlist 为 `[]`。

例外明确列在报告中：jvmTest/commonTest 的 fake 与冻结 oracle、生成代码目录，以及两个原有的公共资源加载点：`produceLibraries` 中的许可证 JSON 和 `rememberLottieComposition` 中的动画资源。资源例外只放行对应文件中的 `Res.readBytes`，不放行普通文件读写。

此检查是词法约束，不能证明任意别名转发、字符串模板表达式或跨函数调用链没有 I/O，仍需语义审查。本次核对了 Route → 原生输入、VM → 接口 → JVM 实现、Coil 请求构建、偏好 bootstrap、Cleaner 展示格式和根效果链。首次过宽的旧 Sign 规则命中了纯展示辅助函数，已改为检查旧类型声明/导入，并增加合法函数的自测；没有为通过门禁修改产品界面。

`compileDomainBoundary` 直接调用项目现有 Kotlin 编译器，独立编译全部 Domain 及 UserData/IconFactoryData，目标类路径只有 Kotlin 标准库、协程、序列化及 annotations；不包含应用输出、Compose、Koin、FileKit、FFI 或 APK 工具，也不继承应用的 Compose 编译插件。类路径原文已归档。最初直接注册 KotlinCompile task 不适用于当前插件构造方式，改用该独立编译入口后通过。

**这证明当前 Domain 的依赖边界，项目仍只配置 JVM target，不声称整个 commonMain 已能在 Native 编译。**

## 构建、行为与视觉验证

- 两模块 `clean → build` 成功，用时 1 分 31 秒；48 项任务中 39 执行、4 来自 Gradle 构建缓存、5 已是最新状态。之后最终非增量编译、`:shared:check`、classpath 导出全部成功，用时 1 分 52 秒。
- 全量 **203 项 JVM 测试，0 失败、错误或跳过**。继承全部功能的成功、失败、取消、竞态、输入快照、偏好兼容和真实 fixture 验证。
- 新 Koin 图测试使用生产模块注册和 fake 平台依赖，创建全部 **11 个 VM**，核对同 owner/key 唯一、偏好 source 唯一、默认目录同步五个功能、bootstrap 容量种子、关闭时每个 owner 仅释放一次；构造不触发 FFI、网络、生成器或桌面动作。
- 新导航兼容测试逐项读写九个旧 JSON payload，类型名和输出保持原格式。既有真实拖拽事件测试验证完整存在性过滤、当前 callback 与原有顺序，只把期望输出类型更新为路径字符串。
- **68 个固定数据软件截图场景零像素差异**：58 张表单/结果/菜单/确认框及 10 张静态导航页。总计保留 78 对完整 PNG；另外两张 Cleaner 首页使用真实磁盘容量，clean 删除生成物后容量数字/进度改变，已与固定容量 Cleaner 场景区分记录，没有裁切或设置容差。其余原图亦一致。
- **22 项视觉资源哈希与 Phase 0 一致**。没有更换字体、颜色、主题、图标、Lottie、图片库或动画参数。

最终编译已修正本次新增的 Preferences API opt-in 警告。既有警告单列：Skiko 依赖版本提示、生成 UniFFI 两处未使用表达式、原 ModalBottomSheet API 弃用、SigningCredentialsValidation 的冗余安全调用、测试中的冗余非空断言。没有为消除旧警告更改 UI API 或算法。

## 原生与 MCP 验收

隔离测试 app-image 使用真实 App、FileKit、Coil、JVM 数据层和 UniFFI；**10 项原生检查通过**。选择自建透明 PNG，验证输入与五密度预览、真实 48/72/96/144/192 输出；随后通过 FileKit 选择 `phase9-native/output`，第二次产物与第一次逐字节一致。原成功通知可见，切页返回和取消文件选择均保留结果，窗口正常关闭。仅操作 `shared/build/migration/fixtures`，未使用生产偏好或输出目录。

Go To Folder 的路径补全需要等待后确认；使用目标 fixture 项实际暴露的 Open Finder item 动作完成选择。一次早期 clipboard paste 超时后改用字段设置与补全等待。未归档系统选择器中无关的目录列表。原生证据保留原 JPEG，不作为无损像素基线。该验收后生产代码仅补充了编译期 opt-in 注解，最终非增量测试及 MCP 使用最终版本。

官方 Compose Hot Reload 1.2.0 + JetBrains JDK 21 的最终 MCP 回放**全部通过**：18 次导航、10 组静态重复截图严格零差异、完整 8 项草稿/输入/默认路径联动检查，无 UI 异常。最终连接状态正常、reloadState=ok、lastError=null。

相对 Phase 0 原始 MCP 图，每张静态页面仅有 111–114 个系统窗口底部圆角像素不同，全部坐标已保留，圆角外差异为 0；没有裁切、遮罩、容差或基线替换，不声称跨阶段原生全图零差异。

首次回放的导航与静态检查通过，但后续停在缺少历史 `phase3-output` fixture 的脚本前置断言；现已让脚本自行创建受控目录。第二次在 dark/apk-tool 的重复截图中出现 81 个底部系统圆角变化像素，经查看两张原图确认界面内容一致。保留失败原图与全部坐标，将回放最短等待从 2 秒增至 3 秒（重连后仍等 8 秒）后，完整场景通过原有零差异断言。没有改动产品动画或放宽检查。

## 复跑与范围

```sh
./gradlew --offline --no-daemon :shared:clean :composeApp:clean :shared:build :composeApp:build :shared:baselineClasspath --console=plain
./gradlew --offline --no-daemon -Pkotlin.incremental=false :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:check --console=plain
python3 scripts/migration/check_visual_assets.py
./gradlew --offline --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true -Pcompose.reload.devToolsHeadless=true :shared:baselineHotRun --console=plain
python3 docs/migration/evidence/phase-09/mcp/reproduce.py "$JBR21_HOME"
```

Phase 9 范围为 G0–G5、干净构建及运行。沿用已确认的 macOS arm64 范围与真实 OS 拖拽、固定动画帧、录像、性能基线豁免。本阶段的测试 app-image 不作为 G6 发行包验收；Phase 10 的性能与发布工作尚未执行。

回滚时先撤架构规则补充，再按需撤平台/source-set 提交；旧架构删除是独立提交，不必恢复 MainViewModel 与全部新 VM 并存。git 回滚不撤销已经写出的 fixture，重放可在 Gradle clean 后自行重建。原有未跟踪的 `composeApp/output/` 与 `docs/ARCHITECTURE_MIGRATION.md` 未纳入提交。
