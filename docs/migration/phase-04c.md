# Phase 4C — APK 信息与进程边界

状态：**完成本阶段实现及本机 G0–G5 验证**。基于 Phase 4B `49b013ae`；进程执行器 `15eb5a75`，能力提取 `447cc361`，页面切换 `94d216bb`。本阶段只迁移 APK 信息，Phase 5 尚未开始。

## 进程和数据边界

- 新增 `ProcessRunner`，使用参数列表直接启动进程，保留工作目录、stdin、默认 60 秒超时和非零退出的原上层错误映射。stdout/stderr 并行读取；stdin 写完关闭；成功、超时和取消均收尾进程与三个管道线程。
- 每条输出流默认最多保留 8 MiB，超限后继续排空管道，返回 255 与截断标志。取消透传，不转换为业务失败。实际进程 fixture 验证含中文、空格和引号参数、stdin、非零退出、大量 stderr、输出上限、缩短配置的超时和取消。
- 超时/取消测试在本机验证父进程与子进程退出，以及管道线程回收；不据此宣称 Windows/Linux 或任意快速脱离父进程的进程树均已验证。
- `Aapt2Locator` 封装资源目录、Windows `.exe` 命名和可执行权限检查；`Aapt2DataSource` 保留 `dump badging <input>`、`dump xmltree <input> --file AndroidManifest.xml`。manifest 读取失败仍返回 null，不让整份 APK 信息失败。
- `JvmApkInformationRepository` 负责文件大小与 MD5，`FileInputStream.use` 关闭输入；图标 ZIP 和 resources.arsc 读取沿用原路径与算法，资源输入流也明确关闭。未更换 aapt2、Skia、Coil 或 XML/resource 解码器。
- 最后一个调用方迁走后删除旧 `ExternalCommand`，避免留下无人使用的另一套进程执行实现。

## Domain 与页面

- `ReadApkInformationUseCase` 组合 badging、manifest/channel、size/hash 与图标源。结果为不可变字段；权限保持原顺序、重复值及 nullable List 的显示语义。图标源在边界复制编码字节，不暴露可写数组；Domain 无 Compose、Skia、JVM 文件或 UI 依赖。
- 保留 CRLF/CR 转 LF，以及旧 StringUtil 分行时不修剪非空行的行为；测试直接对照旧库。名称、包名、版本号、版本四字段全部为空才失败，异常中的非 null 文案（包括空字符串）继续保留。
- 按原 stdout 顺序读取图标：`application-icon-640` 总是更新当前图标，即使失败得到 null；`application` 仅在当前图标为空且路径不是 XML 时回退。XML 图标仍按 manifest resourceId 与原资源表最后一个匹配密度定位。UMENG_CHANNEL 的匹配规则未改。
- JVM 数据层先用原 Skia 解码器验证候选图标，保持损坏图片时的回退位置；Presentation 在 IO 上转换为原 ImageBitmap。重组、滚动和切页不重新执行 Presentation 解码。
- `ApkInformationViewModel` 是窗口级唯一拥有者，发布 Idle/Loading/Result、输入路径、类型化结果及只读 busy。新请求淘汰旧请求；窗口清理后，迟到的结果或复制完成不能再发布状态或通知。
- Route 管理 FileKit、过滤后的拖拽首项及主题；Screen 和详情组件只接状态、意图和 lambda。失败回到原空态与原 Snackbar。复制通过 ClipboardWriter，系统写入成功后才发送原通知。
- 根 busy 和导航接到新 VM，同步删除 MainViewModel 的 APK 状态/方法、旧可变结果模型、Utils 的 APK 专用解析函数，以及最后一个 `copy(value, MainViewModel)` overload。
- 图片窗口保留 **450×450dp、`Zoom Image`、alwaysOnTop、AppTheme、CoilZoomAsyncImage**。打开开关仍位于图标存在分支的局部 `remember`，不存入窗口级 VM。

## 验证结果

全量两模块编译、JVM 测试和常规 baseline classpath：**103 项通过，0 失败、错误或跳过，47 秒**。删除未引用的 ExternalCommand 后再次完成两模块编译和 baseline classpath，23 秒。详见 [测试清单](evidence/phase-04c/tests.json) 与 [最终清理编译](evidence/phase-04c/cleanup-compile.txt)。

新增 21 项测试：5 项真实进程、5 项用例/命令边界、5 项 ViewModel、4 项真实 APK Repository、2 项实际 Compose 页面测试。

- 固定字段、权限重复/顺序、无权限/无渠道、四字段空值、两种图标先后顺序与损坏回退通过。
- 真实 aapt2 读取含中文、空格和引号的 APK 路径；模板名称、包名、SDK、版本、固定大小和 MD5 一致。
- 直接 PNG 与 XML/resourceId/arsc 路径的图标，与冻结的旧实现逐像素相同。缺失/损坏 PNG 仍显示其它 APK 信息；损坏 APK 使用原命令失败映射。
- 快速 A→B、非协作迟到返回、busy 收尾、重复错误独立 effectId、复制精确字符/完成确认/失败、关闭窗口后迟到返回均通过。
- **10 张静态软件截图**与 Phase 0、**6 张 APK 结果软件截图**与切换前均零像素差异；无裁切、遮罩或容差。原 22 个受控视觉资源 SHA-256 一致。

## 官方 MCP 与原生验收

使用 **JetBrains JDK 21.0.6** 编译启动并连接官方 Compose Hot Reload 1.2.0 MCP，复用冻结的 Phase 3 回放和最短 2 秒采集等待。最终 18 次明暗导航、10 对静态重复图零差异、草稿回访、连续设置输入、默认目录联动及无关设置不覆盖自选目录全部通过，无 UI exception。

首次采集被无关桌面窗口遮挡，已弃用并移除该次图像载荷；第二次在亮色 APK 签名页底部圆角 `(793, 570)` 波动 1 像素，失败图对与坐标已保留。第三次使用相同等待与零差异断言通过。最终 MCP 图与 Phase 0 的跨阶段差异为亮色各 110、暗色各 114 像素，全部位于底部圆角；没有消除这些差异，也不宣称 MCP 跨阶段全窗口零差异。详见 [MCP 比较](evidence/phase-04c/mcp-static-comparison.json)。完整最终协议响应留在 `shared/build/migration/hot-mcp/session-2dfrsx43`。

原生验收使用迁移前与最终常规 classpath 的隔离 `.app`，全部为测试偏好和合成 fixture：

- FileKit 选择中文和空格路径 APK，实际显示原字段、MD5 和图标；取消文件选择保留已有结果。
- 迁移前后均采集明暗结果与图片窗口；预览实际截图为 450×450，标题、图标布局和主题一致，关闭预览保留结果。native JPEG 为人工画面/尺寸证据，不作为零像素 golden；图标像素等价由真实 JVM 测试验证。
- 通过实际 Cmd+V 将复制的 MD5 粘贴到隔离测试字段，逐字符验证 `e4211c3ac04c1e42b8fc3447951b1bc5`；观察到原复制成功通知。
- 切页及切换主题后结果保留；无图标 APK 正常展示字段且无图标入口。
- 损坏 APK 显示“执行命令出现错误”并返回原 Lottie 空态；重新选择有效 APK 后恢复字段与图标。
- 选择器与窗口过渡期间刷新辅助功能状态、分步确认路径并短暂等待后重试成功；未为工具操作重试修改产品行为。迁移前与最终原生窗口均已关闭。

原生截图与逐项结果见 [native/report.json](evidence/phase-04c/native/report.json)。未提交 APK、私钥或文件选择器中的无关目录内容。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译、103 项测试及清理后编译通过 |
| G1 行为 | 字段、顺序、空值、真实 APK/资源图标、原错误映射通过 |
| G2 UDF | 单一窗口拥有者、纯 Screen、Domain 边界及旧入口清除 |
| G3 视觉 | 16 张软件图零差异；MCP 10 对静态图零差异；原生明暗辅助窗口核对 |
| G4 交互 | MCP 回放、FileKit/取消、切页保留、预览、复制粘贴、失败重试通过 |
| G5 异步 | 真实进程超时/取消/管道回收、乱序结果、窗口清理、效果确认通过 |

沿用已确认的验收范围：不补录固定动画帧、真实 OS 拖拽、录像或性能基线；不声称完成 Windows/Linux 验收。本阶段不要求 G6，原生测试包不是 ProGuard 发行包。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
```

官方 MCP 启动与回放沿用 [Phase 4A 命令](phase-04a.md#回放与回滚)。原生 fixture 是仓库 apktool.apk 的副本、移除 PNG 的 ZIP 变体和普通文本损坏文件，哈希见 [fixtures.json](evidence/phase-04c/native/fixtures.json)。真实 JVM 图标/损坏样本的生成过程在 `JvmApkInformationRepositoryTest`。

回滚页面切换必须一起恢复旧 Entry、根 busy 和旧唯一状态拥有者。ProcessRunner 的资源修复保持独立提交边界；不要恢复两份执行器或新旧 VM 同时处理同一事件。未迁移的 APK 签名、ApkTool 等功能继续使用各自现有流程。
