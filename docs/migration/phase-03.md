# Phase 3 — 设置、窗口外壳与更新

状态：实现已接线，正在完成本机门禁。基于 Phase 2 `bfcc241c`，不进入 Phase 4A。沿用此前确认的基线范围。

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

提取提交暂保留旧启动门控的初始 false，避免 IO bootstrap 在手动更新迁移时顺带启用网络。随后用独立提交切换为等待 ready 后读取 `start_check_update`，每个窗口根会话只静默检查一次。该修复会让此前被初始 false 竞态跳过的启动检查开始运行，是明确记录的行为修复。

## 兼容发现

旧 `checkUpdate` 导入的是 `io.ktor.http.headers`，其中的 GitHub Accept/API-Version builder 构造了未附加到请求的 Headers。实测请求的 Accept 为 ContentNegotiation 添加的 `application/json`，没有 X-GitHub-Api-Version。本阶段冻结实测行为，不在提取中修复请求头；本机 HTTP fixture 记录这一特征。旧大写架构名不匹配等资产筛选细节也保持原样。

完整门禁、截图比较和原生窗口结果将在本阶段验证结束后补齐。回滚时一起恢复设置/更新 Entry、根消费者和旧拥有者；不把生产偏好备份覆盖回用户的新设置。
