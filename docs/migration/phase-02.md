# Phase 2 — 基础边界与重组期问题

状态：**完成本阶段实现及本机验证**。基于 Phase 1 `2b53a9f2`，未进入 Phase 3。沿用用户更新后的基线范围，不补做真实拖拽录像、固定动画帧或性能基线。

## 本阶段改动

1. **全局 UI**：LoadingAnimate 只接 `visible/useDarkTheme`，主题由 App 已订阅的状态计算后传入；暗色仍使用 `lottie_loading_light.json`，亮色仍使用 `lottie_loading_dark.json`。原覆盖范围、点击拦截与动画参数不变。启动日志放入 `LaunchedEffect(Unit)`。复制增加 `onCopied` 边界和纯格式化函数，成功写入剪贴板后才回调；两个旧 VM overload 保留至 Phase 4B/4C。
2. **容量与校验**：新增 Domain 的 StorageRepository/KeyStoreRepository 接口，JVM 实现通过注入的 IO dispatcher 查询容量、文件元数据及读取密钥。容量快照只由旧 VM 发布，Cleaner 进入、扫描/删除完成时刷新；普通重组不查询磁盘。首个快照返回前使用零容量，并防止除零，不增加加载指示或改变已加载值的格式。
3. **异步结果归属**：LatestRequest 同时取消旧 Job 并核对递增 revision；A→B→A 也不能接受第一次 A 的结果。签名与 ApkTool 分别持有校验状态，密码弹窗有独立的别名请求状态；关闭弹窗使旧请求失效。路径或密码变化立即作废旧校验，切回页面显式重查，相关输入未变的普通表单更新不重读密钥。
4. **只读错误状态**：设置输出路径、APK 签名的三个路径、KeyStore/Junk 输出目录、ApkTool 输出/图标/密钥路径接异步校验。原空值、APK“全部”例外、别名为空/失败与索引规则保留；密码晚返回不会写回另一份表单。等待校验时不新增视觉组件，提交沿原错误提示入口阻止使用未完成的结果。原同步 `verifyAlisa/verifyAlisaPassword` 实现已由这些调用方完整切到服务。
5. **控件平台桥**：公共 FileInput/FolderInput 只收值、picker 请求与输入回调；FileKit launcher 放 jvmMain，通过 expect/actual 接现有桌面页面。文件选择返回值先按原 checkFile 规则在 IO 接受，再交付原字符串；没有新增扩展名或把 webp/大写扩展名提前写进字段。Directory picker 的原返回路径和取消语义保留。
6. **拖拽**：native target 的实例保持稳定，`rememberUpdatedState` 提供最新 dragging/onFinish 回调。同步回调仍按 FilesList 类型立即返回 Boolean，拖入/离开/结束/落下的 dragging 顺序不变；URI 解码与 NOFOLLOW_LINKS 存在性过滤在 IO 完成，再按原列表顺序回传，页面仍在完整过滤后选首项或首个匹配项。
7. **设置草稿**：旧 VM 的私有 SettingsInputDraft StateFlow 成为输出目录/签名后缀唯一输入拥有者。字段事件立即更新草稿，并按原逐次输入时机保存；保存时合并最新持久化值，遗留开关传入的旧 DTO 不得覆盖较新的两项草稿。设置页不再创建局部 signerSuffix/outputPath 状态。原根组件的五页默认目录桥仍保留，Phase 3 再整体切换拥有者。

没有更改主题、布局参数、文案资源、持久化 key/serializer、原签名生成算法或文件筛选规则。密钥服务保留默认 KeyStore 类型探测、null/空 aliases 区分及 `getKey != null` 判定；异常日志只记录异常类，不输出密码或完整请求。

## 验证

| 检查 | 结果 |
| --- | --- |
| G0：两个模块 JVM 编译 + 全部 jvmTest | **33 项通过，0 失败/错误**；原 22 项继续通过 |
| 新增 11 项测试 | A→B→A 旧结果、弹窗关闭、慢别名密码/更换文件、路径查询去重与显式刷新、写入前草稿、字段互不覆盖、两种真实密钥格式、picker/拖拽规则、四种复制格式、设置重组/主题/导航、容量刷新次数、稳定 target 与最新回调 |
| 密钥 fixture | JKS 与 PKCS12；正确/错误 store 密码、正确/错误 alias 密码、空/不存在 alias、文件不存在 |
| 拖拽桥 fixture | 同步 true/false 返回、dragging 顺序、异步完成前回调换代、不存在文件在首位、空格 URI、重复项顺序、断链 symlink 的 NOFOLLOW_LINKS 语义 |
| G3：静态软件图像 | 五页 × 明暗主题，10 张与 Phase 0 **逐像素零差异**，无遮罩/裁剪/容差 |
| JBR 21 + 官方 MCP | 最终构建完成 18 次明暗导航，无 UI 异常；设置两项草稿跨主题/切页保留；正确→错误→正确密钥密码分别产生 alias→空→alias |
| 原生 FileKit | 用最终测试类构建 JBR 21 隔离 `.app`，实际选择测试密钥后路径正确回填；目录选择正确回填；取消保持原值，窗口正常关闭 |
| 资源冻结 | 22 项资源哈希全部与原基线一致 |

MCP 最终原始截图相比 Phase 0 每张还有 **80–82 个底部圆角边缘像素**差异，未记作全窗口零差异。复核中曾出现两测试窗口同时运行时的额外色值/光标差异；关闭原生测试窗口并重新启动 MCP 进程后，内部差异消失，期间未改应用代码或重写图像。保留前后结果于 [capture-investigation](evidence/phase-02/capture-investigation)。Cleaner 的容量数字随本机磁盘变化，保存为观察截图，不作为静态像素断言。

拖拽回归使用进程内构造的原生事件/文件 fixture，不冒称补录了真实 OS 拖拽。此次 `.app` 是隔离测试入口包，不是新的 ProGuard 发行包；Phase 1 发布验证的结论不冒用到本阶段。Phase 2 按方案执行 G0/G2 及受影响组件的 G3/G4。

## 复现与证据

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

新测试位于 `Phase2ValidationTest`、`Phase2PlatformTest`、`Phase2UiTest`。非合作 fake 在取消后仍可完成，用于验证 revision 过滤；Compose 测试显式销毁真实 Desktop owner，清理 ViewModelStore 后再关闭隔离 Koin 容器。

MCP 使用 [既有 JBR 21 启动方式](phase-00-mcp.md)，在新进程 `:shared:baselineHotRun` 就绪后运行：

```sh
mkdir -p shared/build/migration/fixtures/phase2-output
"$JBR21_HOME/bin/keytool" -genkeypair -keystore shared/build/migration/fixtures/phase2-mcp.keystore -storetype JKS -alias phase2 -keyalg RSA -keysize 1024 -validity 1 -dname CN=Phase2,OU=Test,O=AndroidToolKit,L=Test,S=Test,C=CN -storepass fixture-only -keypass fixture-only -noprompt
python3 scripts/migration/hot_mcp_client.py "$JBR21_HOME" --phase2-smoke
```

该密钥只用于本地测试，已存在时复用，不重复生成。MCP 仅操作 BaselineDesktop 的内存偏好和 build/migration fixture。截图时单独运行该窗口。

原生 FileKit 测试另行执行 `MIGRATION_JAVA_HOME="$JBR21_HOME" python3 scripts/migration/package_baseline.py`，用 Computer Use 打开输出的新 `.app`，从 APK 签名的密钥路径按钮选择 fixture，再在设置页选择/取消 fixture 目录。打包脚本保留原默认 JDK 选择，只增加显式 runtime 覆盖；不覆盖用户发行目录。

完整证据见 [phase-02](evidence/phase-02)：33 项测试汇总与构建日志、MCP 导航/输入报告、原生 picker 返回值、软件/MCP 比较和原始 PNG/JPEG、文件哈希清单。文本日志只去除行尾空白。

## 边界与回滚

本阶段只迁移上述组件与服务。设置持久化的 blockingSettings、图标预览文件检查、About 的日志定位、Cleaner 文件项/扫描/删除、旧表单 setter 中的文件命名逻辑仍按方案留待后续阶段；没有宣称全部 UI/Domain 已完成迁移。expect 桥暂沿用旧桌面的 File/Path 返回类型，不冒称 commonMain 已可编译到 Native。

回滚时一起恢复 VM 构造、DI、控件调用方与旧实现即可；不恢复真实用户偏好备份。下一阶段为 Phase 3 的 PreferencesRepository、SettingsViewModel、外壳/更新状态及唯一效果宿主切换。
