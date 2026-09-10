# Phase 10 — 性能、像素及发布验收

2026-09-10，基于 Phase 9 `294f6e40`，验收工具提交 `461f1892`。**已完成本次授权的 macOS arm64 本机验收；尚不能签署原方案的六平台、历史性能阈值和全部原生交互矩阵通过。** 延续此前确认的真实 OS 拖拽、固定动画帧、录像与 Phase 0 性能基线豁免。完整更新弹窗到打开安装器的点击流程本轮未执行，单独记为未验证。

本阶段未修改生产 Kotlin、算法、主题或动画。两模块新增默认关闭的 `migrationCompilerReports` 开关；新增发布包审计、混淆导航/更新契约和可选 EDT 观察脚本。工具只用于 `build/migration` 中的隔离产物，不进入发行包。现有架构约束扫描 **178** 个生产文件、**19** 项规则自测通过，legacy allowlist 仍为 `[]`，Domain 独立编译通过。

## 自动测试与像素

最终强制重新编译并运行 `:shared:check :composeApp:check`，**203 项测试，0 失败、错误或跳过**。55 份 XML 及完整构建日志位于 [verification](evidence/phase-10/verification)。T01–T18 对应的 reducer、校验代次、任务快照/互斥、取消收尾、偏好联动、效果消费、Koin 生命周期和真实工具 fixture 测试继续通过；真实最小化/恢复另外观察，不以测试替代 OS 行为。

与 Phase 9 比较，**68 个固定数据软件截图场景严格 RGBA 零差异**。保存全部 78 张原图，其中 76 张全图一致；另两张明暗 Cleaner 首页随真实磁盘容量变化，各 1,511 像素不同，不属于固定数据门禁。固定容量 Cleaner 场景通过，没有裁切、遮罩、容差或覆盖旧基线。[逐图结果](evidence/phase-10/software-comparison.json)。22 项字体、Lottie 等视觉资源哈希与 Phase 0 一致。

原生记录共 42 张 JPEG：41 张 800×600，APK 图片窗口 450×450；均能完整解码。它们用于观察真实窗口，不能作为无损像素 golden。本轮没有重新运行 Hot Reload MCP，Phase 9 的 MCP 记录仍独立保留。

## 编译器与运行性能证据

环境为 Apple M1 Pro / MacBookPro18,1、10 核、16 GiB、macOS 26.6.2、JetBrains JDK 21.0.6+9-b895.109。实际发布 runtime 版本经线程转储确认；该裁剪 runtime 不含 JFR，因此使用 Java agent 的 ThreadMXBean 每 50 ms 采样 EDT，并保持最多一个待执行 EventQueue 探针。[环境](evidence/phase-10/environment.json)。

Compose 编译器报告包含 375 个类，其中 effectively stable 242 个；524 个 composable 中 368 个可跳过、515 个可重启。单列 25 个状态/表单模型和 22 个根、Route、Screen 的推断结果。`List`、部分请求对象导致的 runtime/unstable 状态如实保留，StrongSkipping 已启用；没有添加全局稳定性配置或虚假注解。[报告摘要](evidence/phase-10/compiler/summary.json)。这些是编译期能力，**不是实际重组次数或性能提升数据**。归档时将报告文件名中的冒号转为下划线，映射表保留原名，便于 Windows checkout。

真实优化发布应用记录 **30,343 个 EDT 样本、30,284 个队列探针**，覆盖输入、明暗导航、FileKit、APK 构建/解析/签名、五密度图标及重复生成、小 AAR、120 项 Cleaner 扫描/选择/取消/删除和辅助窗口。非 startup 场景的队列 p95 为 0.469–1.666 ms，但包含大量等待和自动化操作间隔，不能换算为帧 p95 或输入到显示延迟。[运行摘要](evidence/phase-10/performance/ui-profile.json) 与原始 gzip 数据一并保存。

三个命中文件读取模式的栈均为 RandomAccessFile → ZipFile → BuiltinClassLoader 首次加载应用类；采样未观察到业务磁盘操作或 Rust FFI 在 EDT 执行。[栈审查](evidence/phase-10/performance/stack-review.json)。采样会漏掉短调用，不能证明零执行。最长非 startup 探针 340.600 ms 附近 EDT 位于系统 `LWCToolkit.isCapsLockOn`；其余长尾涉及首次类加载、Snackbar 和 LazyList 布局。[长尾原栈](evidence/phase-10/performance/queue-tail-review.json)。这些相关栈不构成严格因果定位。

观察器本身改变调度、提前启动 EventQueue，也有记录开销；startup 标签不是冷启动计时。没有 Phase 0 同配置性能基线、实际帧/输入延迟、RSS/吞吐量差值、运行重组计数或分配剖析，**不能判定“无超过 5% 的退化”或宣称性能提升**。堆样本未强制 GC，五次图标重复输出一致也不能证明无内存泄漏。没有为了数字调整产品动画或参数。

## 原生矩阵状态

“本轮观察”表示所述操作实际完成，不表示该编号下所有分支、像素或时序都已完整签署。历史 fixture/交互测试随本轮 203 项测试复跑；旧阶段原生记录不冒充本轮再次执行。

| 编号 | 本轮证据及边界 |
| --- | --- |
| V01 | 实际发布应用与 DMG 复制应用均启动，默认页/窗口可见；严格冷/热首帧计时未测。 |
| V02 | 九页明暗导航通过；Tooltip 全位置未逐项测量。 |
| V03 | 导航可往返，600 ms 参数未改；固定转场帧豁免。 |
| V04 | 键盘输入、路径替换、密码遮蔽、草稿保留有观察；完整焦点/快捷键组合未穷举。 |
| V05 | 文件和目录 FileKit 实选、取消及回到应用通过；其他 OS 未测。 |
| V06 | 首项过滤/顺序由现有真实事件测试覆盖；真实 OS 拖拽豁免。 |
| V07 | 事件顺序/边界测试继续通过；真实 OS 拖拽与 400 ms 固定帧豁免。 |
| V08 | 原资源哈希及调用参数保持；真实固定 Lottie 帧豁免。 |
| V09 | 签名页空态/主题本轮观察，成功/错误/复制特征测试复跑；完整原生证书分支沿用 Phase 4B。 |
| V10 | 本轮构建的 APK 实际解析，label/package/version/sdk 与独立 aapt2 输出相符。 |
| V11 | 实际打开 450×450 图片窗口，显示图标后关闭；完整缩放手势未穷举。 |
| V12 | 使用测试 JKS 选 alias 执行 V2 签名，独立验证 V1/V2 有效；其余策略由 fixture 测试及 Phase 5 记录覆盖。 |
| V13 | 字段明暗布局和目录联动观察；生成、校验和失败测试复跑，本轮未通过该页生成新密钥。 |
| V14 | 实际生成 Phase10Fixture APK、查看成功消息；可选签名分支由测试/Phase 6 覆盖。 |
| V15 | 实际生成极小单 AAR 并检查 ZIP；多 AAR/命名边界由测试覆盖。 |
| V16 | FileKit 选透明 PNG，五图顺序/预览和输出尺寸通过；OS 拖入豁免。 |
| V17 | 图标设置 Sheet 可操作；滑块提交语义由真实 Compose 指针测试覆盖，固定帧豁免。 |
| V18 | 空态与明暗主题观察，固定容量截图和字体/Lottie 哈希通过。 |
| V19 | 实扫自建 120 个 build 目录，列表/默认选择可见；深度/排序竞态由测试覆盖。 |
| V20 | 全选切换、只选一项、确认框/取消实际通过；固定底栏截图通过。 |
| V21 | 取消保留 120 项；确认仅删选中的 project102，剩 119 项和 keep.txt 保留；失败映射由测试覆盖。 |
| V22 | 明暗主题、默认目录输入及跨页同步通过；设置固定截图通过。 |
| V23 | 许可窗口与详情实际打开/关闭；225 项许可数据完整，新增依赖数据明确披露。 |
| V24 | 原更新 UI/VM 测试复跑，发布包解析本机完整 release/asset JSON 通过；真实更新弹窗 Markdown/动画未逐项点击。 |
| V25 | 实际仓库完成已知/未知长度下载、截断清理与完整 DMG 下载；取消/重试/安装顺序由既有测试覆盖；弹窗点击至打开安装器全链路未执行。 |
| V26 | APK/图标/AAR/签名/删除过程的遮罩和完成通知有观察，效果替换/消费测试通过；录像与固定帧豁免。 |
| V27 | 窗口最小化/恢复观察、辅助窗口关闭、两份应用正常关闭通过；其他 OS 和完整跨页窗口组合未穷举。 |

[原生检查索引](evidence/phase-10/native/checks.json) 汇总 22 项操作；DMG 复制应用另外记录于 [安装检查](evidence/phase-10/release/dmg-install.json)。输入、密钥和清理目录仅使用受控测试 fixture。

## 发布产物与业务输出

使用现有 ProGuard 和 jpackage 生成实际 app-image/DMG。最终应用 JAR 为 108,024,086 bytes，包内类与 ProGuard 输出逐字节一致。Rust 库在 jpackage ad-hoc 重签后全部 14 个有内容的 Mach-O 节仍与 Cargo 原库一致，JNA、aapt2、apktool 模板、七个预置 APK、字体/Lottie 均存在并核对。[包审计](evidence/phase-10/release/package.json)。

优化后的实际 launcher/runtime/JAR 通过三个独立测试入口：九个导航 key 反射序列化往返；UniFFI/JNA 生成 48×48 PNG；从 EDT 调用真实 JvmUpdateRepository 完成 release/asset 解析、已知与未知长度下载、截断响应失败清理及完整 156 MB DMG 的 SHA-256 校验。进度 callback 线程检查通过。该导航检查证明当前混淆产物能往返，不证明任意旧混淆版本的类名跨版本兼容。[契约结果](evidence/phase-10/release/result.json)。

更新测试首次直接反射内部 transport 时遭遇 ProGuard 将 Continuation 参数具体化，测试入口也曾在异常后因 AWT 存活而超时。最终通过实际仓库的公共接口调用，并让测试 main 失败时明确退出；无需修改产品或 keep 规则。初次日志和线程转储保留。最终脚本运行前清除旧 result/complete 标记，避免残留成功记录；结束时恢复隔离 launcher 配置。

本轮 UI 产物另外独立验证：APK 的 package 为 `org.phase10.fixture`；签名产物 V1/V2 有效、未签名参考包无有效签名；单 AAR 为 4,366 bytes，ZIP 结构正确；图标密度为 48/72/96/144/192，另五次重复生成各项 hash 相同；Cleaner 仅释放所选目录的 10,464 bytes，符合原包含目录 length 的统计规则。[业务记录](evidence/phase-10/release)。

DMG 为 **156,222,812 bytes**，SHA-256 `e06f843e52c3a5c7c5b01bade11bc21b3b19a93d8959fc46eb815f6302856fea`。只读挂载后复制至受控安装目录，应用 JAR hash 一致；隔离偏好后实际启动 Main、取消 FileKit、正常关闭通过。挂载已卸载，两份测试应用已关闭，观察 agent 已移除。没有覆盖系统 Applications 或用户已有安装，没有发布 GitHub Release。

本机环境与 workflow 的 Zulu 21 配置并非完全相同；远端 workflow 未触发。macOS x64、Windows x64/arm64、Linux x64/arm64 均无相应实机结果，状态为 `not-run`。[六平台矩阵](evidence/phase-10/platform-matrix.json)。macOS arm64 的局部通过不能代替这些平台的构建/运行验收。

## 警告、缺陷与剩余门禁

本轮无新增产品编译警告。既有 Skiko 版本提示、UniFFI 未使用表达式、ModalBottomSheet 弃用、冗余安全调用和测试非空断言仍列于日志；ProGuard 沿用的 294 个重复类、103 个未解析类、1 个 library→program 引用及重复资源提示与此前发布验收一致。

许可清单为 225 项；与 Phase 1 归档的原 217 项相比新增 8 项及版本仍匹配。原始 Phase 0 生成文件不在 Git 中，因此用 Phase 1 差异清单核对；没有删许可来宣称所有动态文案零差异。

独立已知行为继续保留：Phase 4B 的 V3.1 行重复 V3 指纹；原 IMAGE picker 与 webp 后端接受范围差异；Phase 5 批量 V4 共享 idsig 名称；Phase 6 可选签名失败仍提示构建完成；Phase 3 GitHub header builder 未附着请求、架构名大小写筛选与未知长度 0/0 回调；Phase 7A 合成 PNG 在原默认最低质量下可能返回 QUALITY_TOO_LOW。本轮没有夹带这些修复。aapt2 原资源无 executable 位，应用原 Locator 在 IO 中补执行位后可用；此次未证明非拥有者的只读安装下该补权限路径成功。

G0–G5 本机固定数据/行为/架构检查及 G6 本机发布核心 smoke 已完成。**原方案的全平台 G6、历史性能阈值、完整 V01–V27 全分支仍有上述缺口**；豁免项保持豁免，未测项保持未测，不将它们写成通过。

## 复跑

`JBR21_HOME` 指向 JetBrains JDK 21 的 `Contents/Home`。先构建未隔离的 DMG，再为可丢弃 app-image 加入测试偏好与 observer；不得在用户安装副本上操作。

```sh
./gradlew --offline --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -Pkotlin.incremental=false -PmigrationPackage=true -PmigrationCompilerReports=true :shared:check :composeApp:check :shared:baselineClasspath :composeApp:packageReleaseDistributionForCurrentOS --console=plain
./gradlew --offline --no-daemon --no-build-cache --rerun-tasks -Dorg.gradle.java.home="$JBR21_HOME" -Pkotlin.incremental=false -PmigrationCompilerReports=true :shared:check :composeApp:check :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
python3 scripts/migration/audit_release_package.py shared/build/migration/phase10-package.json
python3 scripts/migration/prepare_release_smoke.py "$JBR21_HOME"
python3 scripts/migration/prepare_release_profile.py "$JBR21_HOME"
```

第二条命令强制生成报告，避免编译来自缓存时报告目录为空。关闭旧测试应用后启动隔离 app，按上表使用新建 fixture 操作；在 `shared/build/migration/release-profile/recording/scenario.txt` 写场景名作标记，写入 `stop` 后等 `complete.txt`，关闭应用。恢复 `AndroidToolKit.cfg.before-profile` 为同目录的 `AndroidToolKit.cfg`，保留偏好隔离，然后执行：

```sh
python3 scripts/migration/summarize_release_profile.py shared/build/migration/release-profile/recording shared/build/migration/ui-profile.json
python3 scripts/migration/run_release_contract_smoke.py "$JBR21_HOME"
python3 scripts/migration/summarize_release_profile.py shared/build/migration/release-contracts/update-recording shared/build/migration/update-profile.json
```

契约脚本仅监听 loopback，自动恢复 launcher；它需要前一步编译好的 observer.jar。测试 JKS、APK、DMG 及输出目录留在 build 中，不纳入 Git。归档的纯文本报告仅移除行尾空白和多余末尾空行，原始图片及压缩采样不变；证据文件的字节数与 SHA-256 见 [manifest](evidence/phase-10/manifest.json)。回滚仅需撤本阶段报告开关/验收工具提交，应用逻辑不受影响；原有未跟踪 `composeApp/output/` 和 `docs/ARCHITECTURE_MIGRATION.md` 不纳入提交。
