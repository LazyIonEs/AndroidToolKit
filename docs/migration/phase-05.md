# Phase 5 — APK 签名与共享用例

状态：**完成本阶段实现及本机 G0–G6 验证**。Phase 6 尚未开始。
基于 Phase 4C `82dbc4e1`；签名引擎提取 `1d097aeb`，页面切换 `813bda40`。

## 签名引擎与兼容入口

- `SignApkRequest` 冻结路径、前后缀、覆盖、用户/Huawei 对齐设置、策略、V4 名称和凭据。Domain 无 Compose、JVM File 或页面依赖；凭据及密码 Intent 的字符串表示隐藏密码。
- `JvmApkSignerDataSource` 在注入的 IO 调度器中执行原 JKS/KeystoreHelper/apksig 流程。保留五种策略矩阵、CERT signer、Huawei 公式、alignmentPreserved 反值及 V4 error reporting。
- 名称继续由原 File.nameWithoutExtension 生成；非空白前缀不 trim，空白前缀不参与名称。已存在文件按原开关拒绝或删除，错误文案在 Presentation 映射。
- `SignApkUseCase` 真正挂起，不另启 ViewModelScope。批量仍并行并按输入顺序返回；All 由业务预置列表展开为 Oppo/Vivo/Huawei/Xiaomi/QQ/Honor，不送入 File 或路径检查。
- 保留单包在引擎正常返回时的成功消息；批量任一失败或缺少输出使用原通用失败消息，全部成功只发一次通知并定位最后一个输出。
- `generateApktool` 仅通过 `legacySignForApkTool` 委托同一用例，签名失败兼容 null 且不发签名页消息。原 ApkTool 未检查这个 null、最终提示仍基于未签名包的行为留待 Phase 6；没有新增跨 VM 调用。
- 批量 V4 继续共用当前表单的 idsig 名称，现有并发覆盖风险保留并登记；本阶段未改名。VM 忙碌期间忽略重复 Submit，避免再次叠加同一批任务。
- 取消透传。排队中的 IO 取消不会删除已有输出；同步签名库运行中的写盘可能继续完成，但取消后不发布结果或失败提示，也不自动回滚产物。

## 页面与字段规则

- `ApkSigningViewModel` 为窗口级唯一拥有者，发布不可变 `ApkSignatureForm`、组合的 `SigningCredentialsUi`、校验和 busy。提交时只读一次偏好与表单；后续编辑不影响已提交任务。
- Screen/子组件只收状态、字段事件及回调。Route 管理 FileKit 和拖拽适配；手动输入、选择器、预置菜单与拖拽共享字段规则。旧 MainViewModel 签名状态、方法、默认目录分支与路径槽已删除，根 busy/Entry 同步接线。
- 旧 setter 原样冻结为测试 oracle。路径/前缀派生 V4 名称、空路径及空白路径的特殊名称、不存在路径保留名称、相同路径/前缀不重新派生均逐项对照；文件名元数据由 JVM 存储边界提供。
- 改变 key path 清空两个密码、aliases 和索引；相同 key path 保留。改变 alias 清空 alias 密码，相同索引保留；改 store password 仍保留旧 alias 密码并触发异步检查。
- 共享凭据校验使用不可变组合；ApkTool 暂经 LegacySignValidation 薄适配器接入。路径、名称、aliases 和 alias 密码的迟到结果均受请求版本保护。
- 拖拽仍在平台过滤完整列表后选首个小写扩展名 APK 与首个小写 jks/keystore；未扩大旧扩展名规则。V2 Only 每次点击仍显示原提示，V4 文件名仍只读。

## 自动验证

- 两模块编译、全量 **123 项测试，0 失败/错误/跳过**，54 秒。
- 真实 APK 的五种策略通过独立 ApkVerifier 验证；六包并行产物也验证 V1/V2 方案。覆盖中文/空格路径、前后缀、Huawei 对齐、V4 idsig、重复输出、覆盖及错误密码。
- 测试覆盖完整策略布尔矩阵、名称与凭据 setter 特征、不可变提交快照、重复提交、批量逆序完成/部分失败、原错误映射、连续密码/路径输入、迟到返回、窗口取消以及旧 finally 不发布结果。
- 实际 Compose 页面检查 alias 菜单、导航草稿保留、错误表单拒绝与修正后按钮提交；默认目录联动继续覆盖五个表单拥有者。
- **10 张静态软件图与 Phase 0、6 张签名表单软件图与页面切换前，均零像素差异**，无遮罩、裁切或容差。原 22 项视觉资源哈希一致。

## 官方 MCP

使用 JetBrains JDK 21.0.6 与官方 Compose Hot Reload 1.2.0，冻结的 Phase 3 回放最终完整通过：18 次明暗导航，无 UI exception；10 对静态重复图零差异；签名前缀/密钥文件名草稿、连续设置输入、默认目录联动及自选目录保留均通过。

首轮被无关浏览器窗口遮挡，已删除该次图像载荷。后续两轮分别在亮色签名页左下圆角波动 13 像素、暗色设置页两侧底部圆角波动 83 像素；失败图对和坐标均保留。第四轮仍使用相同最短 2 秒等待和零差异断言，全部通过。

最终 MCP 图与 Phase 0 的跨阶段差异：亮色前四个静态页各 112 像素、设置 113；暗色五页各 113，全部在底部圆角。未裁切、遮罩、设容差或消除这些差异，不声称 MCP 跨阶段全窗口零差异。[完整结果](evidence/phase-05/mcp/report.json)、[逐点比较](evidence/phase-05/mcp/static-comparison.json)。原始协议响应留在 `shared/build/migration/hot-mcp/session-co29w46w`。

## G6 发行包与原生验收

优化与混淆均开启，ProGuard 7.9.1 发行包构建成功，耗时 **5 分 56 秒**。未新增 keep 规则或放宽现有配置。构建仍有原有重复资源及 unresolved references 警告；本次本机验收不代表这些历史警告已全部消除。

使用该包的真实 MainKt、JAR、资源和 runtime，偏好通过纯 Java 内存工厂隔离：

- 九页在导航过渡结束后采集；字体、Lottie 与许可窗口正常，包内仍有 225 项许可数据。
- FileKit 选择中文/空格路径 APK，取消再选保持原值；真实选择测试 JKS。使用原生键盘填写前缀，以 Cmd+A 替换输出目录。
- 错误 store password 显示原红色校验状态，修正后实际加载 `phase5` alias。V4 名称显示 `phase5-中文 unsigned.apk.idsig`。
- 页面执行 V4 单包签名，观察原加载动画与成功通知。独立 ApkVerifier 确认产物 V1/V2/V3/V4 全部有效。
- 页面选择 All 并以 V2 批量签名，六个实际 bundled APK 的输出全部通过 V1/V2 验证。只显示一次完成通知；点击“跳转”后 Finder 路径栏定位到最后一个 Honor 输出。
- 切页和切换暗色主题后，自选输出目录、前缀及凭据草稿保持。
- 同一包的启动器、runtime 和混淆 JAR 通过 UniFFI/JNA 调用 Rust，生成 **48×48 PNG**。包内 Rust 库只有一份，ProGuard 中原始库与 Cargo 字节相同，jpackage 重签后 14 个有文件内容的 Mach-O 节仍逐字节相同。
- 原生测试结束后恢复 MainKt，再次启动验证并正常关窗。APK、idsig 和测试私钥仅在 build 的合成 fixture 中保存，未提交。

复用 Phase 1 验收脚本时发现测试隔离缺陷：旧 `ReleaseNativeSmoke.class` 被一并打进 bootstrap preferences.jar，使测试入口使用了无法读取应用类的 bootstrap loader。修正为独立 `preferences-classes` 目录后，JNI 测试通过；应用 JAR 哈希未变。初次失败日志、最终成功日志与修正后的启动证据均保留。

[发行包检查](evidence/phase-05/release/package.json)、[原生步骤](evidence/phase-05/release/report.json)、[七个 APK 的独立验证](evidence/phase-05/release/signature-verification.txt)。新增 `scripts/migration/VerifyApkSignature.java` 可用相同 apksig 9.4.0 独立检查 `POLICY APK [IDSIG]`。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译，123 项测试通过 |
| G1 行为 | setter、策略/对齐、命名/覆盖、真实单包和批量产物通过 |
| G2 UDF | 唯一窗口拥有者、不可变快照、纯 Screen、旧入口清除 |
| G3 视觉 | 16 张软件图零差异；MCP 10 对重复图零差异，跨阶段圆角差异如实保留 |
| G4 交互 | MCP 回放、原生 FileKit/取消/键盘/alias/签名/跳转、主题与导航保留通过 |
| G5 异步 | 乱序、重复提交、取消、快照、部分失败与窗口清理通过 |
| G6 发行包 | 真实混淆包 UI、签名产物、许可资源和 Rust 调用通过 |

沿用已确认范围：不补录固定动画帧、真实 OS 拖拽、录像或性能基线；不声称完成 Windows/Linux 验收。原生 JPEG 用于人工画面检查，不作为零像素 golden。批量 V4 的共享 idsig 冲突仍保留，原生 All 验收使用 V2。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

官方 MCP 沿用 Phase 4A 的冻结回放及最短 2 秒采集等待；G6 沿用 Phase 1 的 migrationPackage 隔离打包和内存偏好脚本。所有签名产物/私钥仅保存在 build 下的合成 fixture，不提交。

回滚页面时一起恢复 Entry、根 busy 和唯一旧状态拥有者。ApkTool 仍必须使用同一共享用例适配入口；不恢复跨 VM 调用，也不自动撤销已写出的 APK/idsig。
