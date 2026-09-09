# Phase 4B — 签名信息与 APK/KeyStore 校验

状态：**完成本阶段实现及本机 G0–G5 验证**。基于 Phase 4A `b585dac7`，能力提取提交 `5fcd76f9`，页面切换提交 `ca73c00b`。本阶段只迁移签名信息；Phase 4C 尚未开始。

## 能力整合

- 对照旧 MainViewModel 的实际行为，将 APK/KeyStore 校验接入 Domain `SignatureRepository`、`VerifySignatureUseCase` 和不可变结果。删除未接线的重复 Repository，JVM 只保留一份执行实现。
- 先让旧 VM 调用用例并通过两模块编译，再切页面拥有者。提取时保存旧结果页的明暗截图，供切换后逐像素对照。
- 证书日期、Subject、RSA algorithm/modulus、签名算法和三种指纹格式沿用原映射；EC 证书继续保留原先两个 RSA 专属字段为空的行为。
- APK 保留 v1/v2/v3/v3.1/v4 顺序、X.509 过滤、`isVerified=false` 但存在证书仍返回可展示结果的规则。错误集合仍只处理 `JAR_SIG_UNPROTECTED_ZIP_ENTRY`，保留尾部换行；无匹配错误时请求原 fallback，异常中的非 null 消息（包括空字符串）保持原样。
- KeyStore 在 IO 中加载，文件流通过 `use` 关闭；取消透传，不转为失败通知。密码不再进入原校验日志。

## 页面拥有者与效果

- 新 `SignatureInformationViewModel` 在窗口 owner 创建，唯一持有 phase、结果、输入文件、密码弹窗会话、密码、aliases、selected alias 和 copyMode。
- Entry 进入 Route；Route 管理 FileKit、过滤后的拖拽首项、主题与 composition 生命周期。Screen 及 Lottie/Box/List/Dialog/Top/Center/Bottom 只接状态切片、意图或 lambda，保留原组件、尺寸和动画。
- 复用 Phase 2 的 `KeyAliasesValidation`，从 legacy 文件移到公共 validation 位置；异步结果通过请求版本和取消状态校验。打开另一份 KeyStore 会重置 aliases；已打开弹窗中的密码仍沿用原 remember 范围。关闭弹窗或移除 Route 清空会话，窗口持有的校验结果保留。
- 验证请求立即置 busy；新请求淘汰旧请求。清理窗口后，迟到的校验或剪贴板返回均不能发结果或通知。
- 四种指纹复制格式由纯 mapper 保留，普通证书字段原样复制。`ClipboardWriter` 在 JVM 主调度器完成系统剪贴板写入后，才发送原成功通知；同文案使用独立 effectId，可重复显示。
- 根 busy 与 Cleaner 按钮的 verifier success 依赖改为新 VM 的只读投影。旧 MainViewModel 的 verifier 状态、执行方法、签名信息别名桥及 copyMode 投影/写入口已删除。
- 删除 `copy(value, copyMode, MainViewModel)`；APK 信息页仍使用的通用 `copy(value, MainViewModel)` 留到 Phase 4C。未迁移 APK 签名与 ApkTool 的异步凭据服务继续保留。

## 验证结果

最终两模块编译、全部 JVM 测试及常规 baseline classpath：**82 项通过，0 失败/错误/跳过**，46 秒。相对 Phase 4A 新增 16 项；详细用例见 [tests.json](evidence/phase-04b/tests.json)。

- 状态机覆盖快速 A→B 文件/密码输入、迟到 aliases、选中别名、关闭弹窗、凭据快照、重复确认、空 aliases、错误/重试、同文案通知、四模式精确字符、剪贴板完成确认与失败、窗口清理后非协作返回。
- 真实 JKS/PKCS12 覆盖全部证书字段、三种 digest、错误密码、缺失 alias、空 store；EC 证书验证原 RSA 字段回退。
- 真实 APK 覆盖有效、未签名、损坏、多签名证书、V4 sidecar、V3.1 密钥轮换。修改已签名 ZIP 的时间戳以保留 V2 签名块，实际得到校验失败但两份 V2 证书可读的结果，确认原展示规则。
- 多文件拖拽先对整个列表执行存在性过滤，再只处理第一项；第一项类型不支持时不跳到后续 APK。原区分大小写规则保持。
- 实际 Compose 页面测试点击指纹卡片、填写密码、显示首个 alias、确认 KeyStore 校验；移除/恢复页面 composition 后，密码会话清空、结果保留。
- **10 张静态软件截图**与 Phase 0、**4 张签名结果页软件截图**与本阶段迁移前结果均为零像素差异，无裁切、遮罩或容差。原 22 个受控视觉资源 SHA-256 一致。

## 官方 MCP 与原生验收

- 使用 **JetBrains JDK 21.0.6** 编译启动，连接官方 Compose Hot Reload 1.2.0 MCP。冻结脚本完成 18 次明暗导航、10 对静态重复图零差异、草稿回访、设置连续输入、默认目录联动及无关设置不覆盖自选目录；无 UI exception。
- 沿用 Phase 4A 的采集等待（最短 2 秒），没有调整零差异断言。完整协议响应与原始 PNG 在 `shared/build/migration/hot-mcp/session-z336vnjw`；提交报告、10 张静态图和交互证据。
- MCP 对 Phase 0 的跨阶段原图比较：亮色各 108 像素（设置 110），暗色各 113 像素不同，全部位于 y≥555、x≤17 或 x≥782 的底部圆角。逐行坐标见 [比较记录](evidence/phase-04b/mcp-static-comparison.json)。没有消除这些差异，也不声称 MCP 跨阶段全窗口零差异。
- 使用最终常规 classpath 打包隔离原生 `.app`，FileKit 实际选择含中文和空格路径的 KeyStore/APK。取消选择后保留已有 KeyStore 结果。
- 原生密码框通过键盘输入错误密码，确认后显示原错误通知且弹窗保留；更正密码后显示 `second`、`first` 两个真实 alias，选择 `first` 并得到对应证书。
- 四个原生复制菜单项分别复制 MD5，实际以 **Cmd+A、Cmd+V** 粘贴到隔离的 APK 签名前缀字段，逐字符核对大小写和冒号，全部正确；重复复制观察到原成功通知。
- 原生选择已签名 APK 后，滚动依次看到 V1、V2、V3 结果。验收窗口已关闭。所有操作使用合成 fixture 和内存偏好，没有提交私钥或 APK 二进制。

原生工具曾出现菜单坐标点击未选中、快速全选/粘贴未覆盖旧文本及窗口状态过期提示；刷新状态、使用已观察到的菜单项索引并分步全选/粘贴后重新核对通过。这些是验收操作重试，没有据此更改生产行为。原生步骤与精确复制值见 [native/report.json](evidence/phase-04b/native/report.json)。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译、82 项 JVM 测试通过 |
| G1 行为 | 真实 APK/KeyStore、字段与顺序、失败标志、原错误集合通过 |
| G2 UDF | 单一窗口拥有者，Screen 无 VM/DI/Repository/Flow 收集；旧入口清除 |
| G3 视觉 | 14 张软件截图零差异；MCP 10 对静态重复图零差异，跨阶段圆角差异记录 |
| G4 交互 | MCP 回放、原生 FileKit/取消/密码/aliases/四模式复制粘贴/APK 列表通过 |
| G5 异步 | 快照、乱序、迟到返回、重复效果、剪贴板确认、窗口清理通过 |

沿用已确认的验收范围：不补录固定动画帧、真实 OS 拖拽、录像或性能基线；不声称完成 Windows/Linux 验收。本阶段不要求 G6，隔离原生包不是 ProGuard 发行包。

## 保留缺陷与回滚

**V3.1 原缺陷单独保留**：当 V3.1 signer 列表非空时，原代码遍历 V3 列表生成 V3.1 行。真实轮换 APK 测试证明 V3 与 V3.1 证书本身不同，但当前展示继续重复 V3 指纹。此次迁移不改变该证书数量/内容，后续修复应独立提交并重新验收。生产校验也没有新增自动寻找 V4 `.idsig` 的行为。

回滚页面切换时，应同时恢复旧 Entry、根 busy 和 Cleaner 投影，避免出现两个拥有者。能力提取提交可独立保留；不得恢复重复 Repository 同时生产使用，也不得将未迁移页面的凭据读取改回 UI 同步 IO。

## 回放

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
# JBR21_HOME 为本机 JetBrains JDK 21 的 Contents/Home。
./gradlew --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true :shared:baselineHotRun --console=plain
```

应用启动后，复用 [Phase 4A 的 MCP 回放命令](phase-04a.md#回放与回滚)。MCP 完成后恢复常规 `:shared:baselineClasspath`，以 `MIGRATION_JAVA_HOME="$JBR21_HOME" python3 scripts/migration/package_baseline.py` 打包原生测试入口。原生 fixture 的生成源码在 [Phase4BFixtures.java](evidence/phase-04b/native/Phase4BFixtures.java)，运行 classpath 为 `shared/build/migration/classpath.txt`；参数为临时输出目录与仓库 `composeApp/resources/common/apktool.apk`。

完整证据见 [evidence/phase-04b](evidence/phase-04b)。
