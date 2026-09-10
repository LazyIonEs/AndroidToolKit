# Phase 8 — 缓存清理与根外壳联动

状态：**完成**。按已确认范围通过 G0–G5；本阶段不要求 G6。

基于 Phase 7B `99364467`。扫描/删除数据层：`f2020b3e`；页面与根状态切换：`cdc00528`。

## 扫描与删除规则

- `ScanBuildCachesUseCase` / `DeleteBuildCachesUseCase` 通过 `BuildCachesRepository` 调用 JVM DataSource。Domain 只发布不可变路径与 metadata，不依赖 Compose、Koin、File 或平台库。
- 扫描保留 `walk().maxDepth(10)`、`nameWithoutExtension == "build"`、父目录名命中 build 时停止下探的原规则。`build.foo`、隐藏目录、符号目录链接仍按原 Kotlin 文件遍历处理，不增加过滤或规范化路径。
- 大小继续累计所有文件及目录自身的 `length()`，缓存内部测量不套用扫描的深度上限。IO 遍历中增加协程取消检查；每个发现项单独送回 VM，逐项可见并按当时的排序方式排序，未改为扫完一次发布。
- 完整绝对路径作为稳定 ID；同名不同目录保持独立项。相同快照中计算选中数量、选中大小和全选状态，排序保留原六种方向与稳定排序规则。
- ConfirmDelete 只接受当前扫描会话的可见确认状态，从此时最新选中项创建不可变删除快照。重复确认、过期确认、取消确认不启动第二次删除；删除期间忽略会改变选择/扫描的事件。
- IO 依次调用原 `deleteRecursively()`，成功逐项移除，失败项保持选中并标红；失败后更新存在/目录 metadata。清理大小仍来自扫描快照，即使文件在扫描后改变，也不重新计价。不存在的路径沿用原递归删除返回值。
- 一项失败不撤销已经删除的项目；取消或窗口关闭不开始后续删除，不发布迟到结果，也不将取消当作删除失败。异常经过 finally 恢复 Idle，剩余失败项可重试。物理删除无法回滚，未引入新的取消按钮。

## 页面与根界面

- 窗口级 `CleanerViewModel` 唯一持有 root、Idle/Scanning/Deleting、不可变列表、sort、capacity 和确认框状态。新扫描或 CloseSelection 使旧扫描失效，旧结果和旧 finally 均不能覆盖新会话。
- CleanerRoute 适配原 FileKit 目录选择与打开目录动作；Screen、列表行、底栏及确认框只接收只读值和明确事件。行内文件系统查询已移除，原相对路径展示、字体、日期、大小格式、颜色及 Lottie scale=1.7 保留。
- Root、CleanerRoute 和根 ClearBuildBottom 使用同一个 window owner 的 VM。`cleanerShellVisibility` 保留三个原谓词：列表非空隐藏 Rail；当前 Cleaner 页且 Idle、有列表时显示根底栏；删除显示全局 Loading，扫描只显示页面内进度。其他功能的 busy 继续按原 OR 规则聚合。
- 签名校验页的 hasResult 继续控制目录按钮 expanded；底栏仍在原 Scaffold 位置，排序菜单 offset `(64.dp, 24.dp)`、按钮顺序和进入/退出动画未改。
- 容量首帧使用 bootstrap 预取值，进入/返回页面、窗口重新获得焦点、扫描或删除结束时在 IO 刷新；重组和主题改变不会探测容量。失败保留最近一次有效值，旧查询不能覆盖新查询。
- MainViewModel 的旧 Cleaner 状态、扫描/排序/选择/删除/容量方法已全部移除；删除 `PendingDeletionFile`。MainViewModel 仅剩未被 UI 使用的偏好投影，其类、UIState 和旧 DI binding 按方案留到 Phase 9 统一删除。

## 自动验证

- 两模块 JVM 编译通过；全量 **201 项测试，0 失败、错误或跳过**，用时 **1 分 24 秒**。专项复跑 **19 项全部通过**。
- 真文件系统覆盖深度 9/10/11、build.foo、嵌套 build、同名不同路径、隐藏目录、符号链接，以及原目录 metadata 大小累计。真实删除验证未选中 sibling 保留、扫描后内容增加、目录被替换成文件、已不存在路径和权限失败/恢复后重试。
- VM 验证逐项扫描、六种排序、混合选择/全选、同快照统计、旧非协作扫描、新排序、关闭选择、最新确认快照、重复事件、逐项删除、失败保留及异常恢复。关闭窗口时非协作删除返回不再发布或开始下一项。
- 容量测试覆盖首个内存种子、显式刷新、乱序与异常读取；实际 Compose 测试覆盖窗口焦点恢复、离开再进入、主题重组不额外读取。
- 根谓词覆盖 phase × 是否有列表 × 是否当前 Cleaner × 其他功能 busy 的 **24 种组合**。实际 App 验证扫描局部进度、删除全局遮罩、渐进移除、失败标记、关闭选择后 Rail 恢复，并确认整个窗口只创建一次 Cleaner VM。
- 迁移前后的 **10 张明暗列表/混合选择/失败项/排序菜单/删除确认图**零像素差异；全量回归后的 **10 张静态页面图**与 Phase 7B 零像素差异。没有裁切、遮罩或容差，22 项视觉资源哈希一致。

新增 Compose 交互测试首次在最后一条断言遇到同名“设置”导航项和悬停提示，修正测试选择器为可点击导航项后通过；产品代码没有为此改变。初次日志与复跑结果均保留。

## 原生桌面验收

使用现有 `package_baseline.py` 将测试入口打包为可被 Computer Use 定位的隔离应用，运行真实产品 App、原 FileKit 和真实 JVM 数据层。所有清理内容位于新建的 `shared/build/migration/fixtures/phase8-native-8mhi42v6/`，不访问生产偏好或生产输出目录。

原生目录项的普通点击未正确定位；使用该 fixture 项已暴露的 `Open Finder item` 动作后，先核对扫描列表确实只有 `project a/build`、`工程乙/build.foo`、`protected/build` 三项，再继续删除验收。系统目录选择器列表不作为截图证据归档。

**10 项原生检查通过**：目录选择、扫描项及大小、混合选择、完整路径排序、取消确认保持全部文件、成功项移除/权限失败保留、未选中项及无关文件保留、恢复权限后仅重试失败项、原释放容量消息、全选/未选中提示及退出选择恢复导航。失败项的扫描大小为 1120 字节，重试成功文案仍为“清理完成，已为您清理1.12KB”。测试创建的只读权限已恢复；未选中的缓存和无关文件仍可检查。

原生截图保留 Computer Use 返回的 JPEG，不声称是无损像素基线。路径、文件状态、原 UI 文案及检查结果见 `evidence/phase-08/native/report.json`。

## 官方 MCP 与回放

官方 Compose Hot Reload 1.2.0、JetBrains JDK 21 和隔离内存偏好环境下，**18 次导航、10 组静态重复截图、全部 8 项交互检查通过**，无 UI 异常。

相对 Phase 0 原始 MCP 基线，每张静态图有 **160–178** 个变化像素：47–66 个位于顶缘的鼠标指针（x=285..297、y=0..6），其余 111–114 个位于系统窗口底角。已查看原图确认指针，所有差异坐标在 `mcp-static-comparison.json` 中保留；没有裁切、遮罩、设置容差或替换基线，也不声称跨阶段原生全图零差异。迁移前后表单及软件静态页面的 20 项对比仍为严格零差异。

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
./gradlew --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true -Pcompose.reload.devToolsHeadless=true :shared:baselineHotRun --console=plain
python3 docs/migration/evidence/phase-08/mcp/reproduce.py "$JBR21_HOME"
```

MCP 最短等待 2 秒，重连后额外等 8 秒让原生窗口初始化完成。运行时保持验收窗口无遮挡。原生 FileKit 清理场景使用另一隔离应用，其窗口在 MCP 采集前已关闭。

## 范围与回滚

本阶段要求 G0–G5，不要求 G6。测试入口打包仅用于原生 UI 定位，不作为发行包验收。沿用已确认范围：macOS arm64；不补录真实 OS 拖拽、固定动画帧、录像或性能基线，不声称完成 Windows/Linux 验收。

回滚 Cleaner owner、Route、根底栏、Rail、Loading 必须整组恢复，不能留下两个状态拥有者。已经完成的物理删除无法由代码回滚恢复；只可重新生成测试 fixture，不对真实用户文件做恢复或重建。
