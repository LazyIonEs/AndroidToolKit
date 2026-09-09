# Phase 0 — JetBrains JDK 21 / Hot Reload MCP 补充验收

状态：**接通并完成局部验收；Phase 0 总门禁仍未完成**。

用户于 2026-09-09 建议使用 Compose Hot Reload MCP，并指定 JetBrains JDK 21 编译启动。
本次在 `shared` 中增加默认关闭的 `-PmigrationHotReload=true` 开关；只在启用时应用 Hot Reload 1.2.0 插件、选择 JetBrains 编译工具链并提供 `baselineHotRun`。
测试入口继续使用 `BaselineDesktopKt` 的内存 PreferencesFactory 和 fixture 输出路径，直接展示原 `App()`。
Kotlin、Compose、Rust、UDL 与生产页面源码没有变更。

## 实测结果

| 检查 | 结果 |
| --- | --- |
| 编译/启动 JDK | JetBrains 21.0.6+9-b895.109；通过已确认的测试 PID 的 JVM 属性再次核实 |
| MCP | 官方 `compose-hot-reload` 1.2.0 stdio 服务；initialize、tools/list、status 成功 |
| 重启重连 | MCP restart 返回 reconnected=true |
| 九页 × 明暗主题 | 18 个入口选中状态正确，均无运行时 UI exception |
| 实机内容区截图 | 38 张 PNG：18 张页面、18 张重复图、2 张草稿回访图 |
| 静态稳定性 | APK 签名、密钥生成、APK 生成、垃圾代码、设置，两主题共 10 对图；解码 RGB 后逐像素比较，全部 0 差异 |
| 草稿状态 | MCP type_text 写入签名前缀和密钥文件名，切页返回后值保留 |
| 常规回归 | 关闭开发开关后两模块编译成功；显式 `jvmTest --rerun` 的 19 项测试全部通过，19s |
| 视觉资源 | 原 22 个受控文件 SHA-256 一致 |

[采集报告](baseline/hot-mcp-jbr21-macos-arm64/report.json)、[JVM 属性](baseline/hot-mcp-jbr21-macos-arm64/runtime.json)、[截图清单](baseline/hot-mcp-jbr21-macos-arm64/manifest.json)。
日志：[启动](evidence/phase0-jbr21-launch.txt)、[MCP 回放](evidence/phase0-hot-mcp-success.txt)、[常规测试重跑](evidence/phase0-after-hot-tests-success.txt)。

窗口报告是 800×600；MCP 返回的内容区 PNG 为 800×572，而 semantics 根坐标为 1600×1144。
保留服务原始 PNG 字节，没有重新编码、转换 JPEG、裁切或遮罩。
零差异是同一进程内间隔 250ms 的静态重复采集，不代表跨 JDK/OS 或新旧架构已经逐像素相等。

四个动态页面（签名信息、APK 信息、图标生成、Cleaner）的两帧存在非零差异，原始两帧和差异像素数全部保留。
Lottie 在真实窗口正常运行；这补足了软件测试取消无限动画造成的画面缺失，但尚未固定动画时钟。
磁盘容量仍是实机数据，原生系统装饰也不在 MCP 截图范围内。

`type_text` 调用 Compose 语义写入，不等于 OS 键盘事件。真实 Tab/selection/快捷键、拖拽、FileKit 选取/过滤/焦点、原生窗口录像与性能基线仍需补齐。

## 回放

设置 `JBR21_HOME` 为本机 JetBrains JDK 21 的 `Contents/Home`，先运行：

```sh
./gradlew --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true :shared:baselineHotRun --console=plain
```

该任务编译与运行均限定 JetBrains 21；不使用全局 JavaExec 覆盖来改变正式打包。
自动重载默认关闭，避免采集时后台构建干扰画面。

然后连接官方 MCP（stdio，必须让 stdin 保持打开）：

```sh
./gradlew --no-daemon --quiet --console=plain -Dorg.gradle.java.home="$JBR21_HOME" -PmigrationHotReload=true :shared:hotMcpServerJvm
```

已提供可独立回放的 MCP 客户端；需要 Python 3 和 Pillow，仅连接上述隔离实例：

```sh
python3 scripts/migration/hot_mcp_client.py "$JBR21_HOME" --smoke
```

每次在 `shared/build/migration/hot-mcp/session-*` 创建新目录，保留 MCP 响应、截图和 report，不覆盖提交过的基线。
回放会重启测试应用以清空内存偏好，再进行导航、主题切换和两个测试字段输入；不会触发签名、生成、删除或下载按钮。
省略 `--smoke` 可逐行输入 MCP 调用，例如 `{"name":"status","arguments":{}}`。

MCP 客户端配置的 args 需要包含 `-PmigrationHotReload=true`；`--quiet` 必须是两个 ASCII 连字符，不能写成 `—quiet`。
本次会话使用客户端直接执行官方 stdio 服务，已真实完成协议连接及测试。

已修正本机 Codex 现有 `compose-hot-reload` 条目的这两个 args 问题，保留其命令和其他配置。
已按修正后的实际配置完成 initialize，并等待 PID 发现后确认 connected=true；见 [握手记录](evidence/hot-mcp-config-handshake.json)。
桌面客户端可在 MCP 设置中重启该服务来加载新配置；连接配置结构见 [官方 MCP 文档](https://learn.chatgpt.com/docs/extend/mcp?surface=cli)。

接入依据：[JetBrains Hot Reload 1.2.0 官方说明](https://github.com/JetBrains/compose-hot-reload/blob/v1.2.0/README.md#mcp-server-for-ai-agents)。
