# Phase 10 补充 — 真实 GitHub 更新流程

2026-09-10，按用户要求将 `gradle.properties` 中 `kitVersion` 从 1.6.11 改为 **1.6.10**，保留该修改。重新构建实际 ProGuard 发布应用，`createReleaseDistributable` 用时 5 分 53 秒成功；生成的 BuildConfig 和设置页均显示 1.6.10。更新业务实现、请求地址和安装处理器没有修改。

本次使用 macOS arm64 的真实窗口、GitHub API 和 Release 下载，未用 loopback 或 fake 更新仓库。测试偏好使用既有内存隔离工厂，下载目录为 `shared/build/migration/live-update-downloads`，避免覆盖用户输出和偏好。

| 步骤 | 实际结果 |
| --- | --- |
| 检查更新 | 设置页点击“检查更新”，请求生产地址 `https://api.github.com/repos/LazyIonEs/AndroidToolKit/releases/latest`。 |
| 识别版本 | 弹窗显示 `AndroidToolKit-v1.6.11`，Release 为正式发布、非 draft/prerelease；真实更新说明 Markdown 可见。 |
| 选择平台 | 自动选中 `AndroidToolKit-macos-arm64.dmg`。 |
| 下载 | 点击“更新”，观察“连接中”、12% / 26% / 73% 及实际字节进度；下载中点击弹窗外部后仍继续。 |
| 文件完整性 | 最终文件 160,295,298 bytes，与 GitHub asset 大小、SHA-256 均一致。 |
| 完成状态 | 显示“下载成功”“可以开始安装了”，按钮为“退出并安装”；等待及重新读取界面后仍能点击。 |
| 打开安装器 | 点击“退出并安装”，旧应用进程消失；Finder 实际打开 `/Volumes/AndroidToolKit`，出现应用与 Applications 拖放入口。 |
| 下载版本 | 挂载包 Info.plist 的 CFBundleShortVersionString 和 CFBundleVersion 均为 1.6.11。 |

GitHub [v1.6.11 Release](https://github.com/LazyIonEs/AndroidToolKit/releases/tag/v1.6.11) 的 macOS arm64 asset 摘要为 `d0469c283dca300523c04ad0ce6fb91f6f6cb4967906f2b802ba8c7dfc89e3c0`，实际下载文件相同。[文件校验](evidence/phase-10-live-update/download-verification.json)、[原生截图与步骤](evidence/phase-10-live-update)、[挂载版本](evidence/phase-10-live-update/mounted-version.json)。

此次补齐 Phase 10 原记录中“更新弹窗 → 真实下载 → 点击打开安装器 → 旧应用退出”的本机缺口。Finder 安装界面保留打开，未拖放覆盖 Applications，未启动 GitHub 下载的旧架构应用；不能把打开 DMG 描述为已经完成覆盖安装。其他平台、历史性能基线和全部原生矩阵未测分支的状态不变；本轮未测试真实下载取消/重试。

观察到完成态最后一个采样进度为 `0.999306`，界面已正确进入“下载成功”；transport 沿用末次进度回调，未额外强制归一到 1.0。本轮不夹带进度表现修改。构建警告沿用 Phase 10 已记录项。纯文本归档只清除行尾空白，截图保留原 JPEG；未归档可能包含临时签名 URL 的整份 HTTP 日志。
