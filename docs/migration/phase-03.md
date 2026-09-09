# Phase 3 — 设置、窗口外壳与更新

状态：**完成本阶段实现及本机验证**。基于 Phase 2 `bfcc241c`，Phase 4A 尚未开始。沿用此前确认的基线范围，不补录真实 OS 拖拽、固定动画帧或性能基线。

## 所有权与存储

- PreferencesDataSource 只读写旧物理 key 与旧 serializer DTO，不再保存可写 StateFlow；读取、encode/decode 和写入都在注入的 IO dispatcher 上执行。
- PreferencesRepository 是唯一偏好状态源。字段事件即时发布原始输入，单一 worker 按顺序保存；`revision` 标记已接受修改，`persistedRevision` 只确认连续成功落盘的修改，`outputPathVersion` 只标记实际目录变化。旧写入完成不回填字段；失败字段保留在后续写入之前重试。
- 启动入口在创建 Window 前于 IO 完成偏好加载与容量预取。VM 构造只接内存快照；composition/EDT 不阻塞等待配置，也不新增加载页。Domain 的主题/复制/垃圾代码枚举不带 UI 资源，存储 name 与原值一致；UserData/IconFactoryData 的 DTO 与 enum ordinal 保持不变。
- SettingsViewModel 接收字段 intent，唯一发布设置页的即时输入和异步目录验证。Conventional、APK 签名设置、KeyStore、开发者选项、About 接参数；目录 picker 与原生许可窗口由 Route 接线。About 的窗口开关仍在原 LazyColumn item 的 composition 内，离开该作用域后重置。
- MainViewModel 的偏好访问仅留未迁移功能使用的只读投影，设置草稿及设置写入口已删除。一个有版本的兼容订阅更新 APK 签名、密钥生成、垃圾代码、图标生成、ApkTool 五个表单；根 `collectOutputPath` 已删除。无关偏好、落盘确认和相同目录不会再次覆盖自选目录。
- AppViewModel 只发布主题、标签、垃圾代码入口等窗口投影。图标编辑器仍在原 `onValueChangeFinished` 时保存，Phase 8 再迁移其表单和 slider 草稿。

## 更新与窗口效果

- UpdateRepository 接口返回无 Compose 的 release/asset/错误类型。JVM transport 保留原两种 HTTP client 配置、代理、重试、版本比较和资产顺序；下载前目录创建/文件删除也在 IO。
- UpdateViewModel 独占检查、显示、选中资产、START/DOWNLOADING/FINISH、进度、字节数、下载路径与安装请求。Job 留 private；取消传播，旧进度核对代次，后续下载等旧流和 finally 释放互斥锁后再复用路径。未知总长度保持原不定长显示和 transport 的 `0/0` 回调。
- UpdateDialog 保留原布局、动画、说明和按钮，仅接状态/intent。下载路径不会因重组丢失；安装请求按 ID 确认，先关闭弹窗，再由 Desktop.open 打开，成功后才关闭会话并退出。未打开成功不退出，普通重组不重放操作。
- 所有旧 Snackbar 出口与新 VM 同时切到唯一窗口 AppEffectSink/AppEffectHost，旧 StateFlow 消费器删除。邮箱容量 64，发送结果可见；相同文案具有不同 ID，新通知替换当前通知，已经触发的目录动作不被下一条通知取消。六处旧成功动作改为完成时捕获的路径值。
- 会话最终关闭时关闭偏好 worker 和效果邮箱；普通导航不关闭它们。不承诺进程强退后仍完成未落盘写入。

## 启动更新策略的独立提交

提取提交 `b6b3aa2a` 暂保留旧启动门控的初始 false，避免 IO bootstrap 在手动更新迁移时顺带启用网络。独立修复提交 `8a069675` 已通过两个真实 App 根组件测试（开启/关闭及后续重组），切换为等待 ready 后读取 `start_check_update`，每个窗口根会话只静默检查一次。该修复会让此前被初始 false 竞态跳过的启动检查开始运行，是明确记录的行为修复。

## 兼容发现

旧 `checkUpdate` 导入的是 `io.ktor.http.headers`，其中的 GitHub Accept/API-Version builder 构造了未附加到请求的 Headers。实测请求的 Accept 为 ContentNegotiation 添加的 `application/json`，没有 X-GitHub-Api-Version。本阶段冻结实测行为，不在提取中修复请求头；本机 HTTP fixture 记录这一特征。旧大写架构名不匹配等资产筛选细节也保持原样。

## 验证与证据

| 门禁 | 结果 |
| --- | --- |
| G0：两个模块 JVM 编译、全部 jvmTest | **52 项通过，0 失败/错误/跳过** |
| G1/G5：偏好与更新 | 旧物理 key/serializer fixture、IO 线程、加载中输入、连续输入与阻塞写入、失败字段重试、五页目录联动、手动/静默检查、重复事件、未知长度、失败、取消后重试及旧进度过滤通过 |
| G2：状态边界 | SettingsScreen/UpdateDialog 不接 VM、DI、MutableState 或任意全量状态回传；根只有一个 Snackbar 消费点，旧设置/更新拥有者和路径回传已移除 |
| G3：软件静态图 | 五页 × 两主题共 10 张，与 Phase 0 **逐像素零差异**，无裁切、遮罩或容差 |
| G3/G4：官方 MCP | JBR 21，18 次明暗导航无 UI 异常；10 对静态重复截图零差异；原两份表单草稿、设置空白字符和默认目录联动保留 |
| G4：原生窗口 | 实际 FileKit 选择目录后回填正确，取消保持原值；原 800×600 Open Source Licenses 窗口打开/关闭正常；版本行双击启用拓展并显示原 Snackbar |
| G4/G5：效果与安装 | 同文案替换、动作一次执行且不被下一条通知取消；完成后多次重组仍可安装；平台打开成功才退出，失败不退出/不重放 |
| 图标设置提交时机 | 对原 Compression 组件实际按下并拖动鼠标，偏好保持原值；松手后才发布并保存 |
| 冻结资源 | 22 项 SHA-256 与原基线一致；VersionInfo 的 2 次点击/1000ms 实现字节未变 |

19 项新增测试位于 `Phase3PreferencesTest`、`Phase3UpdateTest`、`Phase3UiTest`、`Phase3UpdateTransportTest`、`Phase3StartupTest`、`Phase3IconCommitTest`。旧 33 项继续通过，仅将被移除接口的测试接线迁到新拥有者。

下载集成测试使用本机临时 HTTP server 和临时文件：已知长度、chunked 未知长度、截断响应、真实取消后重试，均核对实际输出字节和失败清理。安装交互通过注入的 DesktopActionHandler 记录成功/失败与退出顺序；没有运行下载的安装程序，也不把该测试描述为真实安装验收。

MCP 的 10 张原始静态 PNG 与 Phase 0 比较，亮色各 **108**、暗色各 **112** 个像素不同，全部在底部两侧圆角（y≥554）；没有改写图像或掩盖差异，不记作全窗口零差异。软件图像十张均为 0。动态 Lottie/容量只作观察，未宣称固定帧对比。

原生 `.app` 使用最终测试类与 JBR 21 打包，偏好和输出隔离在 fixture；它不是新的 ProGuard 发行包。本阶段不要求 G6，也未借用 Phase 1 的发行验证结论。Computer Use 只保存选择器关闭后的截图/AX，以及许可窗口/开发者提示，没有保存系统目录列表。

```sh
./gradlew :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
# 按 phase-00-mcp.md 先启动 JBR 21 baselineHotRun
mkdir -p shared/build/migration/fixtures/phase3-output
python3 scripts/migration/hot_mcp_client.py "$JBR21_HOME" --phase3-smoke
MIGRATION_JAVA_HOME="$JBR21_HOME" python3 scripts/migration/package_baseline.py
```

完整证据在 [phase-03](evidence/phase-03)：构建与测试汇总、官方 MCP 报告、软件/MCP 原始 PNG 和比较、原生 JPEG/AX、哈希清单。日志仅去除行尾空白。MCP 回放四个可直接访问的表单目录；图标在内的五个表单及后来创建的拥有者由 VM 测试一并覆盖。

## 回滚与后续

先撤 `8a069675` 可独立撤回启动策略修复；撤结构迁移时一起恢复设置/更新 Entry、根消费者和旧拥有者，不同时启用两个 Snackbar 消费器，也不把生产偏好备份覆盖回用户的新设置。

五表单兼容订阅、图标/复制/垃圾代码偏好写入桥按 Phase 4A/4B/5/6/7/8 的各自接线顺序移除。Cleaner 项目扫描/删除、图标文件读取等未迁移业务仍归旧 MainViewModel；本阶段未声称整个工程已达到最终架构。下一阶段为 Phase 4A 的 KeyStore 生成页面试点。
