# Phase 1 — Koin 接入

状态：**完成本阶段实现及 macOS arm64 验证**。按用户 2026-09-09 的更新指令进入本阶段，不再补做 Phase 0 的真实键盘/拖拽、固定动画帧、录像和性能基线。尚未开始 Phase 2。

## 实现范围

- shared 引入 Koin BOM/core/compose/viewmodel，composeApp 单独声明 BOM/core；新增 Core、Data、Domain、ViewModel 四组模块。Domain 模块暂空，只注册现有 MainViewModel。
- FlowSettings 和 PreferencesDataSource 在容器内各为单例。原 `createFlowSettings()` 的 toolkit 节点、序列化格式和 MainViewModel 初始化/业务执行路径保持原样。
- main 在 `application` 外启动一次 Koin；App 根使用要求的 KoinContext，局部抑制其废弃警告。显式传入当前 Compose Koin 实例，使隔离测试不退回 JVM 全局容器。
- 在 NavDisplay 外取得窗口 ViewModelStoreOwner，并显式交给 koinViewModel；九个导航 entry 继续使用同一旧 VM，原导航栈及 decorators 保留。
- 新增 AppDispatchers，当前只注册，不全仓替换 Dispatchers。模块通过工厂函数创建，避免不同隔离容器复用有状态的 single 定义。
- AppSession 使用原子标记保证关闭入口只执行一次。正常关窗、finally 和原更新安装成功的 exitProcess 前调用同一入口；未增加等待、确认框或改变窗口退出策略。ViewModelStore 仍由窗口 owner 清理。

## 验证结果

| 检查 | 结果 |
| --- | --- |
| G0：shared/composeApp JVM 编译、jvmTest | 通过，22 项测试，0 失败/错误 |
| DI/生命周期新增 3 项测试 | 容器内唯一偏好源；同 owner/key 不重建；不同 key 独立；重组同实例；窗口 owner 清理一次；并发重复 shutdown 只关闭一次 |
| 原有行为与 fixture | 19 项测试全部继续通过，含两项草稿往返、九页明暗导航及五个真实 native 函数 |
| 资源冻结 | 22 项资源哈希与原基线一致 |
| JBR 21 + 官方 Hot Reload MCP | 新进程加载 Koin 后完成明暗 18 次页面导航，无 UI 异常，两项输入草稿往返保留 |
| 软件静态图比较 | 五个静态页 × 两主题，相比 Phase 0 共 10 张逐像素零差异；未裁剪、遮罩或设置容差 |
| MCP 静态图比较 | 同进程 10 对重复截图零差异；与 Phase 0 各有 84 个像素差异，均位于底部圆角边缘，保留完整比较记录，不记作全图零差异 |
| G6：ProGuard 发布包 | 优化/混淆保持开启，构建成功；真实 main 启动，九页导航、字体/Lottie、许可窗口、FileKit 打开/取消及正常关窗通过 |
| 发布包 native | 使用包内启动器/Java runtime 和混淆 JAR，实际 UniFFI/JNA 调用生成 48×48 PNG；包内 dylib 唯一；ProGuard 输出哈希与 Cargo 相同，jpackage 重签名后 14 个 Mach-O section 字节仍完全相同 |

软件图像测试没有覆盖原生窗口边缘或固定动画帧；MCP 圆角差异推测来自桌面背景的窗口边缘合成，不据此声称全窗口零像素变化。动态页面仅做本阶段快速导航 smoke。

生命周期测试显式使用 Desktop 的 `DefaultArchitectureComponentsOwner.setLifecycleState(DESTROYED)`。CMP 1.12.0 的软件测试 runner 只发送 ON_DESTROY，未清理 ViewModelStore；真实 Desktop 窗口的销毁方法会清理，因此测试模拟真实窗口 owner，而未修改应用生命周期行为。更新安装器未实际打开，避免触发安装；其关闭接线经过代码检查。

## 依赖与许可

Koin 全部为 **4.2.2**；CMP runtime/ui/foundation 仍为 **1.12.0**，Lifecycle 仍为 **2.11.0**。Kotlin stdlib **2.4.20**、coroutines **1.11.0**、serialization **1.11.0**、annotation **1.10.0** 保持不变；Koin 请求的较低版本由项目现有版本解析覆盖。

对比 Phase 0 打包留存的 AboutLibraries 元数据，本次从 217 项增至 225 项：新增 Koin BOM/core/core-viewmodel/compose/compose-viewmodel 五项，以及 Stately concurrency/concurrent-collections/strict 三项（2.1.0）。既有条目无删除、版本无变更。许可窗口已在混淆包中实际打开；新增许可数据保留，未为截图相同而删除条目。完整记录见 [libraries-diff.json](evidence/phase-01/libraries-diff.json)。

ProGuard 沿用现有规则，未新增 Koin 全包 keep 或关闭混淆。构建仍报告 unresolved references 和重复资源警告，沿用已有 `-ignorewarnings`；本机运行结果不代表这些历史警告已全部消除，也不代表其他 OS/架构通过。

## 复现

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest --console=plain
./gradlew --offline --no-daemon :shared:dependencyInsight --configuration jvmRuntimeClasspath --dependency io.insert-koin --console=plain
./gradlew --offline --no-daemon :shared:dependencyInsight --configuration jvmRuntimeClasspath --dependency lifecycle --single-path --console=plain
./gradlew --offline --no-daemon :composeApp:dependencyInsight --configuration jvmCompileClasspath --dependency koin-core --console=plain
python3 scripts/migration/check_visual_assets.py
```

JBR 21 与 MCP 启动方式见 [Phase 0 MCP 记录](phase-00-mcp.md)。在新进程运行 `:shared:baselineHotRun` 后使用 `hot_mcp_client.py "$JBR21_HOME" --smoke`，该进程的偏好与输出仍隔离。保留的 Phase 1 图片是本次产物，不覆盖 Phase 0 基线。

```sh
./gradlew --offline --no-daemon "-Dorg.gradle.java.home=$JBR21_HOME" -PmigrationPackage=true :composeApp:createReleaseDistributable --console=plain
python3 scripts/migration/prepare_release_smoke.py "$JBR21_HOME"
# 用 Computer Use 打开 composeApp/build/migration/distribution/main-release/app/AndroidToolKit.app
# 完成九页、FileKit、许可窗口 smoke，正常关闭该窗口后运行：
python3 scripts/migration/run_release_native_smoke.py "$JBR21_HOME"
```

`migrationPackage=true` 只把本次输出重定向到 build/migration，默认发行目录不变。prepare 脚本只调整该临时包的启动配置，添加纯 Java 内存 PreferencesFactory；应用 JAR、main、资源和 runtime 都来自真实 ProGuard 构建。native 脚本临时把同一启动器 main 切到 fixture，结束后在 finally 恢复。重新 smoke 前重建临时包；不要对用户已有发行包执行这些隔离操作。

证据目录：[phase-01](evidence/phase-01)。其中保存编译/依赖/发布日志、22 项测试汇总、10 张软件静态图、10 张 MCP 静态图及两项草稿、发布包九页和许可窗口截图、native 哈希与运行结果。截图格式按来源保存（MCP/软件 PNG，Computer Use JPEG）。文本日志仅去除行尾空白。

## 回滚与下一阶段

本阶段未改动持久化数据格式。可撤销本阶段提交恢复旧 VM 构造和入口；测试/DI 注册与该构造需一起回滚。Phase 2 将按方案建立平台边界并修复已证实的重组期问题，本次不提前迁移业务状态。
