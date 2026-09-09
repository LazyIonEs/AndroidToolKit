# Phase 0 — 基线与门禁执行记录

状态：**未完成，禁止进入 Phase 1**。用户于 2026-09-09 明确选择“严格按方案逐阶段验收”。

本记录依据 [架构迁移方案](../ARCHITECTURE_MIGRATION.md)；基线提交为 `b32698ff2f1f2acce56eb3b472a00b6f2c895923`。
Phase 0B 提交：`783544ec`。基线测试与观察记录独立提交，未进入 Phase 1。

应用生产 Kotlin 源码、布局、主题、导航、资源、默认偏好、窗口参数均未改动。

## 已落地内容

1. 复现并修复 clean 后丢失 UniFFI 绑定的问题，独立记录于 [Phase 0B](phase-00b.md)。
2. 固定 22 个受版本控制的主题、字体、strings、Lottie、图标和导航转场文件的 SHA-256，提供只验证、不重写基线的脚本。
3. 接入方案现有版本目录中的 commonTest/jvmTest 依赖，首次联网补齐缺失缓存后可离线运行。
4. 建立仅测试 JVM 可见的内存 PreferencesFactory；原 `create("toolkit")` 工厂与原 Composable 不改，测试不读写生产偏好。
5. 添加旧表单、真实物理偏好键、密钥生成、Rust 图像处理、Cleaner 目录、APK 签名和 AAR fixture 的 16 项测试。
6. 提供隔离桌面启动及 macOS jpackage 测试包脚本。资源复制到测试目录，输出路径位于 `shared/build/migration/fixtures/output`。
7. 采集九页面 LIGHT/DARK 共 18 张 JPEG 与对应 AX 文本；验证九导航入口、主题切换和目录 picker 打开/取消。图片仅是观察记录。

测试结果见 [tests.json](evidence/tests.json)，成功日志见 [phase0-tests-success.txt](evidence/phase0-tests-success.txt)。

环境与原生库/既有七个 bundled APK fixture 的哈希见 [environment.json](environment.json)。

## 门禁状态

| 门禁/事项 | 状态 | 证据或缺口 |
| --- | --- | --- |
| G0 两模块增量与干净编译 | 通过 | Phase 0B 日志；禁用 build cache 的 clean 编译实际执行 |
| G1 已加入的特征测试 | 通过（局部） | 16 项测试；不是九业务全部成功/失败/取消矩阵 |
| 视觉资源冻结 | 通过 | `python3 scripts/migration/check_visual_assets.py` |
| 九页明暗观察图 | 已采集 | `baseline/macos-arm64-{light,dark}/`，每图附 AX 文本 |
| G3 无损逐像素基线 | **未通过** | 桌面采集工具实际返回 800×600 JPEG；DPI 未独立测量，磁盘容量未冻结，Lottie 是实时帧 |
| G4 导航与主题 | 局部通过 | 原顺序九入口均可切换，明暗切换有效 |
| G4 FileKit | 局部通过 | 原生目录对话框实际打开；Cancel 后默认目录未变。完整选取/过滤/拖拽待验 |
| G4 输入与切页保留 | **未通过** | 自动化键盘输入未写入字段，不能据此判断应用丢状态；剪贴板操作返回错误 -10005（等待应用读取剪贴板超时） |
| G4 原生交互录像 | **未完成** | 尚无符合矩阵的录像；截图不能替代 |
| 动画固定关键帧 | **未完成** | 未建立固定时钟/相同资源帧的截图比较 |
| 性能基线 | **未完成** | 未采集同 release 配置的 JFR/输入帧 p95/RSS/句柄/长任务数据 |
| 完整业务 fixture 输出 | **未完成** | 多证书/损坏 APK、更新下载、进程取消等仍需补齐；已验证未签名/五策略 APK 与小规模单/多 AAR |
| Windows / Linux 基线 | 待采集 | 本机 macOS 证据不能替代 |

不会为了推进 DI 将 JPEG 转码为 PNG 后声称无损，也不会放宽差异阈值。

## 已验证的 16 项测试

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| LegacyFormCharacterizationTest | 5 | 同/异密钥路径重置范围；V4 路径/前缀/手工名；清空路径的旧 leading-hyphen 特例；Junk 命名和范围文案 |
| LegacyPreferencesCharacterizationTest | 4 | 原实际物理键；默认值；未知独立枚举回退；图标偏好全部参数回读 |
| KeyStoreFixtureTest | 1 | JKS、PKCS12 × 1024、2048 四组合，真实私钥/证书/alias 可读取 |
| NativeFixtureTest | 1 | 五个同步 native free function 装载，PNG/JPEG 尺寸和解码，无损 PNG 的可见像素一致 |
| CleanerFixtureTest | 2 | 目录 metadata 长度规则，depth 9/10/11、build.foo、嵌套 build 边界 |
| ApkSigningFixtureTest | 1 | 临时未签名 APK + 五策略签名，用独立 ApkVerifier 验证 scheme（V1 限 API21–23，V2Only 从 API24） |
| JunkArchiveFixtureTest | 2 | 极小单/多 AAR 的 manifest/classes/res、workspace 清理、仅重建测试 batch 目录且保留同级文件 |

真实 aapt2 对 bundled apktool.apk 的 badging 输出保存在 `shared/src/jvmTest/resources/migration/apktool-badging.txt`，供后续解析迁移对照。

这些是旧实现的特征测试，不冒充未来新 Repository/ViewModel 的单元测试。
输入文件均为测试生成或仓库原有 fixture，密钥密码仅为公开测试常量。临时目录由 JUnit 管理。

## 实际存储格式核验修正

方案中的“enum name 不变”不足以描述旧存储：

- 独立偏好 `theme_config`、`signature_copy_mode`、`junk_mode` 写字符串名称。
- `encodeValue(UserData.serializer(), "user_data", ...)` 写分字段物理键；`destStoreType`、`destStoreSize` 写 **Int ordinal**。
- `icon_factory_data.pngTypIdx`、`jpegTypIdx` 同样写 **Int ordinal**。

非默认样本：`user_data.destStoreType=1` 代表 PKCS12、`user_data.destStoreSize=0` 代表 1024；图标 Mitchell/Hamming 分别是 2/1。
后续迁移必须保留旧 serializer 和 **枚举顺序**，不能改为 JSON blob 或名称字符串，也不能重排枚举。

oxipng 的原算法会优化全透明像素下不可见的 RGB；测试分别核对 alpha 和非透明像素，没有改变原算法或放宽可见像素相等要求。

## 回放命令

```sh
python3 scripts/migration/check_visual_assets.py
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest --console=plain

# 原 UI + 隔离内存偏好；关窗后测试偏好自然丢弃。
./gradlew --offline --no-daemon :shared:baselineDesktop --console=plain
# 暗色启动：追加 -PmigrationTheme=DARK

# macOS 原生包，仅用于让桌面工具识别独立的测试窗口。
./gradlew --offline --no-daemon :shared:baselineClasspath --console=plain
python3 scripts/migration/package_baseline.py
# 应用包路径写入 shared/build/migration/baseline-app-path.txt。
# 每次创建独立目录；不会覆盖 composeApp/output/。
```

可在以上隔离测试包中补采无损截图和原生操作录像。使用同一测试输出根、语言、窗口尺寸、主题、显示器/DPI，并按方案 V01–V27 记录；不要将真实用户文件带入截图或删除测试。

## 窗口与资源锁定

主窗口保留原 `Window` 默认尺寸策略、标题 AndroidToolKit、`WindowIcon()` 和关闭退出。
APK 图片窗口仍为 450×450dp、Zoom Image、alwaysOnTop；许可证窗口仍为 800×600dp、Open Source Licenses、alwaysOnTop。
这些是源码核验，辅助窗口的实际交互验收仍未完成。导航转场仍为原 600ms 配置，所有页面继续使用窗口原 MainViewModel。

## 后续顺序与回滚

先补齐 Phase 0 的无损视觉、原生输入/拖拽/录像、性能和 fixture 缺口；门禁通过后再执行 Phase 1 Koin。
没有建立新旧状态桥，没有切换页面，没有迁移或清理真实存储；测试/基线提交可独立回滚，Phase 0B 构建修复另有提交边界。
