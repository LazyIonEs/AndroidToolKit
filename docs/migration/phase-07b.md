# Phase 7B — 垃圾代码 / AAR 生成

状态：**完成**。按已确认范围通过 G0–G5；本阶段不要求 G6。

基于 Phase 7A `4c7fe53d`。生成服务提取：`bb7d65fd`；页面状态切换：`18bba1c1`。

## 生成管线与目录所有权

- `GenerateJunkCodeUseCase` 接收不可变输出路径及 Single/Multi 配置；JVM DataSource 包装原 `AndroidJunkGenerator` / `MultiAarGenerator`。Domain 不依赖 Compose、File、ASM 或旧表单 DTO。
- Single 保留 `packageName + "." + suffix`、字符串 `toIntOrNull() ?: 0`、资源前缀与原实际 AAR 命名。表单名只将基础包名中的点改为下划线；生成器将完整包名（包括 suffix 中的点）转换，两者原有差别保留。
- Multi 保留重建 `outputPath/outputDir` 的规则、原 lowercase 字符池与随机调用顺序、包名/资源前缀各自去重、数量随机含上限、上下限相等或反转时取 lower。注入的 Random 默认仍为 `Random.Default`，没有重新实现随机分布。
- 外层并发仍为 `availableProcessors().coerceIn(2, 6)`，内部仍使用同一 common-pool 的 `IntStream.parallel()`。ASM、资源生成函数、随机概率、预测器公式与依赖版本均未修改。
- 不同服务实例共享输出 Mutex，覆盖批量目录重建、生成和大小测量。每次操作拥有临时 workspace，批量中的每个生成器另有独立根目录，防止 `com.abc.defg` / `com.abcd.efg` 去点后碰撞。
- 同步生成和结构化协程的所有批量任务退出后，finally 才清理本操作 workspace。三个原并行循环只调整异常边界：记录首个异常，等待其他 worker 完成，再重新抛出；并发度与每个 worker 的算法体不变。避免 parallel stream 提前抛错时仍有线程写入已清理目录。
- 取消等待队列不会启动生成或重建输出；运行取消不报告失败，等待同步生成退出后清理。已完成或部分输出仍保留，不回滚用户产物，也不删除非本任务 workspace。
- 成功大小仍按单文件 `length()`，或目录 `walkBottomUp().filter { isFile }.sumOf { length() }`。原完成消息、scale=2 大小格式、Short 时长、Jump 动作路径保留，Single 的动作仍收到实际 AAR 文件路径。

## 页面状态与兼容规则

- 窗口级 `JunkCodeViewModel` 唯一持有 mode、两组不可变原字符串草稿、输出、预计大小、路径校验和 busy。切换模式、导航、主题时保留两组草稿。
- 纯 Screen/SingleUi/MultiUi/JunkMode/PackageName/Generate 只接收只读数据与明确字段事件；Route 适配原目录选择器。生成按钮发送 Submit，随机按钮发送随机事件，不在 reducer/update lambda 中生成随机值。
- 包名和 suffix 事件在 reducer 中派生原 AAR 显示名；随机 suffix 调原 token 函数 3–8 位，随机资源前缀调用 2–6 位后添加 `_`。每次点击只调用一次注入生成器。
- 预计大小移出组合阶段，继续调用原 `JunkSizePredictor`，显示 scale=1 与 `最小 ~ 最大`。批量对相同预测值的循环求和改为数学等价的 Long 乘法（包含原溢出语义、负数/空 count 为零次），避免极大原字符串计数让 UI 执行同比例循环；没有更改预测公式。
- 保留原提交校验顺序：输出路径错误或尚未完成校验时 `check_error`；随后同时检查 Single/Multi 草稿，即使其中一组被隐藏。两个活动数量字段仍用 `isEmpty`，其余字段用原 `isBlank`，没有增加数值上下限校验。
- `JunkOutputValidation` 接管旧校验槽：相同路径去重、显式页面刷新、乱序结果淘汰。读取异常收敛为内联错误并结束 pending，可刷新重试。Submit 同步读取校验拥有者，防止刚编辑的路径尚未送达 UI Flow 就启动生成。
- Submit 在第一次挂起前捕获完整请求并置 busy；重复提交忽略。关闭窗口取消任务、废弃迟到效果、恢复 busy。默认输出目录只订阅真实版本变化，外部模式偏好继续更新 mode。
- Entry、默认目录订阅、root Loading 在同一提交切换；删除 MainViewModel 的旧 Junk 状态/生成/设置桥，以及生产 `JunkCodeInfo`、`LegacyPathChecks`。冻结旧表单仅保留在测试 source set，用于独立行为比对。导航入口可见性仍由原 AppUiState 设置控制。

## 自动验证

- 两模块 JVM 编译通过；全量 **185 项测试，0 失败、错误或跳过**，用时 **1 分 20 秒**。
- 固定随机源覆盖随机上下限、重复包名和资源前缀重试、去点碰撞；UseCase 检查请求映射、nullable/空错误消息与取消传播。
- 真实小规模 single + 2 个 batch AAR 检查 manifest 包名、实际文件名、classes.jar 中 ASM 可读的类和路径、R.txt、布局与资源前缀、原大小测量规则。仅在 TemporaryFolder 测试 batch 目录重建，原 sibling、旧 single 和无关 workspace 均保留。
- 非协作生成、排队取消、不同服务实例互斥、并行 worker 异常等待、批量首错后其他任务退出、失败后私有目录回收均通过。
- reducer/预计大小与冻结旧表单逐项比较，覆盖空字符串、空格、前导零、越界整数、suffix 内的点、隐藏草稿校验、极大计数和 Long 等价累计。
- VM 验证两模式完整快照、重复提交、模式和随机事件、原消息与 Jump 路径、错误/重试、同步路径 pending、读取失败恢复、窗口关闭和迟到结果。旧路径乱序测试已切换到新拥有者；五表单默认目录联动和稍后创建 Junk VM 的初始化通过。
- 实际 App/Compose 回放验证字段输入、模式切换、返回页面草稿、根 Loading 和生成完成后的 Jump。**12 张明暗表单图**与本阶段迁移前、**10 张静态页面图**与 Phase 7A（已对齐 Phase 0），均零像素差异。无裁切、遮罩或容差；22 项视觉资源哈希一致。

提取测试初次因两个同名 CancellationException 导入发生编译冲突，修正测试 import 后通过。状态测试初次两项断言早于异步本地化资源读取完成；改为等待真实 busy 收尾后断言，专项及随后全量回归通过。初次日志保留，产品逻辑未为测试改变完成时序。

## 官方 MCP

回放在 JetBrains JDK 21、官方 Compose Hot Reload 1.2.0 和隔离内存偏好环境运行。真实页面生成通过 6 项检查：两组草稿保留、显示名与实际命名差别、1 个单 AAR 与 2 个 batch AAR 的结构、精确大小文案、仅重建 fixture 子目录、保留旧 single 和无关 sibling。所有生成只写入新建 fixture 子目录，报告与表单/完成图在 `evidence/phase-07b/mcp/junk/`。

首次完整回放通过 18 次导航、10 组静态重复截图零差异和 8 项交互检查，但跨阶段对比在各页面右缘出现约 9,800–14,700 个变化像素，不能计为跨阶段视觉通过。保留仅含本应用的原图及差异坐标。尝试关闭开发工具后发现 MCP 无法重启；改用仅隐藏工具窗口的 `-Pcompose.reload.devToolsHeadless=true`，控制通道正常，重连后额外等待 8 秒让应用窗口完成初始化。随后一轮发现其他窗口遮挡，已删除该轮全部 12 个 PNG payload；只保留失败日志和清理记录。取得用户“已就绪”回复后最终无遮挡复查的 10 组静态重复截图全部零差异。相对 Phase 0 原始 MCP 基线，每张保留 **106–113** 个窗口边缘差异像素：底角 y≥555，以及四张亮色图的右侧最边缘单点 `(799,33)`（RGB 的 R/G 各差 1）。全部差异坐标保存在 `mcp-static-comparison.json`；没有裁切、遮罩、设置容差或替换基线。10 张软件静态图和 12 张前后表单图依然严格零差异，未调整产品窗口参数、配色、布局或字体。最终回放的 18 次导航及全部 8 项交互检查通过，无 UI 异常。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

MCP 启动命令：

```sh
./gradlew --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true -Pcompose.reload.devToolsHeadless=true :shared:baselineHotRun --console=plain
python3 docs/migration/evidence/phase-07b/mcp/reproduce.py "$JBR21_HOME"
```

`reproduce.py` 调用 `hot_mcp_client.py --phase7b-smoke`，保留最短 2 秒等待和重连后的 8 秒窗口初始化等待。运行时保持验收窗口无遮挡。最终复查只重放静态页面与原交互，真实 single/batch 结果来自已完成的首次完整场景。

本阶段方案要求 G0–G5，未追加 G6 发行包里程碑。沿用已确认范围：不补录真实 OS 拖拽、固定动画帧、录像或性能基线；不声称完成 Windows/Linux 运行验收。

回滚页面需同时恢复 Entry、root busy、默认目录订阅及旧状态拥有者。输出互斥和私有 workspace 服务可独立保留；代码回滚不能恢复已由原批量规则清空的目录，也不能删除或重新生成用户已有 AAR 输出。
