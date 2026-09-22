<img src="/composeApp/launcher/icon.png" width="56" align="left" />

## AndroidToolKit

<p align="start">
<a href="https://opensource.org/license/mit"><img src="https://img.shields.io/github/license/LazyIonEs/AndroidToolKit?color=green"/></a>
<img alt="Static Badge" src="https://img.shields.io/badge/platform-%20macos%20%7C%20windows%20%7C%20linux%20-5776E0">
<a href="https://github.com/LazyIonEs/AndroidToolKit/actions"><img src="https://img.shields.io/github/actions/workflow/status/LazyIonEs/AndroidToolKit/build-release.yml"/></a>
<a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest"><img src="https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/total?color=orange"/></a>
<a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest"><img src="https://img.shields.io/github/v/release/LazyIonEs/AndroidToolKit"/></a>
<a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/kotlin-2.4.10-7a54f6"/></a>
<a href="https://www.rust-lang.org/"><img src="https://img.shields.io/badge/rust-1.95.0-black"/></a>
</p>


<!-- ![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/LazyIonEs/AndroidToolKit/build-release.yml)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/total)
![GitHub Release](https://img.shields.io/github/v/release/LazyIonEs/AndroidToolKit)
![GitHub License](https://img.shields.io/github/license/LazyIonEs/AndroidToolKit)
![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/latest/total) -->


简体中文 | [English](./README_EN.md)

面向 Android 开发者的跨平台桌面工具，集成签名查看、APK 分析与签名、证书和 APK 生成、图标制作及缓存清理，支持 Windows、macOS 和 Linux。

[下载最新版本](https://github.com/LazyIonEs/AndroidToolKit/releases/latest) · [界面预览](#界面预览) · [编译运行](#编译与运行) · [常见问题](FAQ.md)

> Linux 平台尚未经充分测试，遇到问题欢迎通过 [Issues](https://github.com/LazyIonEs/AndroidToolKit/issues) 反馈。

## 主要功能

| 功能 | 可以做什么 |
| --- | --- |
| 签名信息 | 导入 APK、JKS 或 Keystore，查看证书主体、有效期、签名算法及 MD5 / SHA-1 / SHA-256 指纹，检查 APK 签名验证结果 |
| APK 信息 | 分析包体组成，浏览文件、权限和组件，按 ABI 查看原生库及 ELF / ZIP 16 KB 对齐结果，读取应用版本、SDK 和文件校验值 |
| APK 签名 | 选择密钥库和别名，为 APK 签名；支持 V1、V2、V2 Only、V3、V4 策略及文件对齐 |
| 签名生成 | 创建密钥库，自定义别名、密码、有效期、证书信息、密钥类型和大小 |
| APK 生成 | 设置应用名称、包名、图标、版本和 SDK，生成 APK，并可选择生成后立即签名 |
| 图标生成 | 从 PNG / JPG / JPEG 生成五种 Android 密度图标，提供总览与大图预览，可配置输出目录、名称和压缩参数 |
| 缓存清理 | 按自定义规则扫描文件或文件夹，预览结果、勾选项目并确认删除 |

支持文件拖入、信息复制，以及跟随系统、亮色和暗色外观；可在设置中调整默认输出路径和更新检查。

## 下载 - [Releases](https://github.com/LazyIonEs/AndroidToolKit/releases/latest)

|   设备    |          芯片           |                                                                                                                                                                                                                  下载                                                                                                                                                                                                                   |
|:-------:|:---------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|  macOS  | Apple Silicon (arm64) |                                                                                                                 <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-macos-arm64.dmg"><img src="https://img.shields.io/badge/DMG-Apple%20Silicon-%23000000?logo=Apple" /></a>                                                                                                                 |
|  macOS  |      Intel (x64)      |                                                                                                                    <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-macos-x64.dmg"><img src="https://img.shields.io/badge/DMG-Intel%20x64-%2300A9E0?logo=Apple" /></a>                                                                                                                    |
| Windows |   x64 (Intel / AMD)   |     <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-x64.msi"><img src="https://img.shields.io/badge/MSI-x64-%232d7d9a?logo=writedotas&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-x64.exe"><img src="https://img.shields.io/badge/EXE-x64-%232d7d9a?logo=writedotas&logoColor=white" /></a>     |
| Windows |         ARM64         | <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-arm64.msi"><img src="https://img.shields.io/badge/MSI-arm64-%232d7d9a?logo=writedotas&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-arm64.exe"><img src="https://img.shields.io/badge/EXE-arm64-%232d7d9a?logo=writedotas&logoColor=white" /></a> |
|  Linux  | x64 (AMD64 / x86_64)  |        <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-amd64.deb"><img src="https://img.shields.io/badge/DEB-x64-%23FF9966?logo=debian&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-x86_64.rpm"><img src="https://img.shields.io/badge/RPM-x64-%23F1B42F?logo=redhat&logoColor=white" /></a>         |
|  Linux  |    ARM64 (aarch64)    |     <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-arm64.deb"><img src="https://img.shields.io/badge/DEB-arm64-%23FF9966?logo=debian&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-aarch64.rpm"><img src="https://img.shields.io/badge/RPM-aarch64-%23F1B42F?logo=redhat&logoColor=white" /></a>     |

Windows 安装时如遇权限问题，可尝试以管理员身份运行安装程序。macOS 首次打开提示及其他使用问题见 [FAQ](FAQ.md)。

## 快速上手

1. **查看文件**：将 APK 拖入「签名信息」或「APK 信息」；查看密钥库时，按提示输入密码并选择别名。
2. **签名或生成**：进入对应工具，填写文件、输出目录和必要参数；APK 签名完成后，可回到「签名信息」检查结果。
3. **制作图标**：选择图片，在「更多设置」中确认目录、名称和压缩方式，再点击「开始制作」。页面建议使用 1024 × 1024 px 源图。
4. **清理缓存**：先配置规则，再选择项目文件夹或其上层目录；扫描完成后检查路径和勾选项，确认后删除。

### 缓存清理规则

在「缓存清理 → 管理规则」中编辑规则，支持文件和文件夹两种目标：

- 名称、相对路径支持「等于」「开头」「结尾」「包含」；文件还支持按大小超过指定 KB / MB / GB 筛选。
- 同一规则内可选择满足全部或任意条件；任意一条已启用规则命中，即加入扫描结果。
- 规则可复制、排序、启停，并设置是否默认勾选命中项。
- 点击「保存」应用修改，或用「保存并试运行」选择目录进行扫描；「取消」放弃本次修改。

默认规则精确匹配名称为 `build` 的文件夹，区分大小写，`Build` 和 `build.foo` 不会命中。扫描与删除不跟随符号链接，删除前会重新核验候选路径。更多说明见 [FAQ](FAQ.md#缓存清理)。

## 界面预览

以下为新版中文界面截图，点击图片可查看原图。

| 签名信息 · 亮色 | 签名信息 · 暗色 |
|:---:|:---:|
| ![签名信息 · 亮色](screenshots/signature_info_light.webp) | ![签名信息 · 暗色](screenshots/signature_info_dark.webp) |

<details>
<summary>APK 信息：从包体总览到文件与原生库</summary>

| 导入 APK | 包体组成 |
|:---:|:---:|
| ![导入 APK](screenshots/apk_info_import.webp) | ![包体组成](screenshots/apk_info_overview_light.webp) |

| 权限声明 | 组件分类 |
|:---:|:---:|
| ![权限声明](screenshots/apk_info_permissions.webp) | ![组件分类](screenshots/apk_info_components.webp) |

| 原生库与对齐检查 | 应用档案 |
|:---:|:---:|
| ![原生库与对齐检查](screenshots/apk_info_native_libraries.webp) | ![应用档案](screenshots/apk_info_app_details.webp) |

![包体组成 · 暗色](screenshots/apk_info_overview_dark.webp)

</details>

<details>
<summary>APK 签名、签名生成与 APK 生成</summary>

| APK 签名 | 签名生成 |
|:---:|:---:|
| ![APK 签名](screenshots/apk_signing.webp) | ![签名生成](screenshots/keystore_generation.webp) |

![APK 生成](screenshots/apk_generation.webp)

</details>

<details>
<summary>图标生成：五种密度与大图预览</summary>

| 图标总览 | 大图预览 |
|:---:|:---:|
| ![图标总览](screenshots/icon_generator_overview.webp) | ![大图预览](screenshots/icon_generator_preview.webp) |

</details>

<details>
<summary>缓存清理与自定义规则</summary>

| 缓存清理 · 亮色 | 缓存清理 · 暗色 |
|:---:|:---:|
| ![缓存清理 · 亮色](screenshots/cache_cleaner_light.webp) | ![缓存清理 · 暗色](screenshots/cache_cleaner_dark.webp) |

![清理规则编辑](screenshots/cache_cleaner_rules.webp)

</details>

<details>
<summary>设置与外观</summary>

| 设置 · 亮色 | 设置 · 暗色 |
|:---:|:---:|
| ![设置 · 亮色](screenshots/settings_light.webp) | ![设置 · 暗色](screenshots/settings_dark.webp) |

</details>

## 编译与运行

本项目是 **Compose Desktop 桌面应用**，需要在电脑上编译和运行，无需连接 Android 手机或启动模拟器。当前 Gradle 工程仅配置 JVM 目标，普通桌面构建无需另外安装 Android SDK / NDK。

### 设备与环境要求

| 项目 | 要求或建议 |
| --- | --- |
| 电脑 | macOS、Windows 或带图形桌面的 Linux；发布工作流覆盖 x64 和 ARM64，Linux 尚未经充分测试 |
| 硬件 | 开发建议使用 16 GB 或以上内存、SSD，并为 Gradle / Cargo 缓存和打包产物预留数十 GB 空间；这是建议配置，未经最低配置测试 |
| JDK | **JDK 21**，将 `JAVA_HOME` 指向 JDK 安装目录，并将其 `bin` 加入 `PATH`；IDE 的 Gradle JVM 也设为 21。发布工作流使用 JetBrains JDK 21 |
| Rust | 安装稳定版 Rust 工具链，确保 `cargo`、`rustc` 可用；项目未固定 Rust 工具链版本，最低版本取决于 Cargo 解析的依赖 |
| Gradle | 使用仓库自带的 Wrapper，当前版本为 **9.5.1**，无需单独安装 Gradle |
| Kotlin / Compose | 当前构建使用 Kotlin **2.4.20**、Compose Multiplatform 插件 **1.12.1**，由 Gradle 自动下载 |
| 其他 | Git、对应平台的原生编译工具，以及能访问 Gradle、Maven 和 Cargo 依赖仓库的网络 |

依赖版本以 [版本目录](gradle/libs.versions.toml)、[Gradle Wrapper 配置](gradle/wrapper/gradle-wrapper.properties) 和 [Rust 配置](rust/Cargo.toml) 为准。

Rust 图像处理依赖需要 C 编译工具；x64 平台还需准备 NASM。各平台准备方式如下：

| 平台 | 原生编译环境 | 生成安装包时额外需要 |
| --- | --- | --- |
| macOS | Xcode Command Line Tools（`xcode-select --install`）；Intel Mac 安装新版 NASM，例如已有 Homebrew 时执行 `brew install nasm` | 使用包含 `jpackage` / `jlink` 的完整 JDK 21 |
| Windows | Visual Studio Build Tools 的「使用 C++ 的桌面开发」、Windows SDK，以及匹配目标架构的 MSVC 工具；x64 另装 NASM 并加入 `PATH` | WiX Toolset 3.x，确保 `candle.exe` 和 `light.exe` 可被找到 |
| Linux | GCC / Clang 与基础编译工具；Debian / Ubuntu 可安装 `build-essential`，x64 另装 `nasm` | DEB 需要 `dpkg`、`fakeroot`；RPM 需要提供 `rpmbuild` 的软件包 |

工具链安装可参考 [Rust 官方指南](https://doc.rust-lang.org/book/ch01-01-installation.html)，安装包工具要求见 [JDK 21 打包说明](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html)。JDK、Rust 默认目标与本机架构应保持一致，尤其避免在 Apple Silicon 上混用 ARM64 与 x64 工具链。

### 获取源码并启动

```sh
git clone https://github.com/LazyIonEs/AndroidToolKit.git
cd AndroidToolKit
```

先检查 `java -version`、`javac -version`、`cargo --version` 和 `rustc --version` 能正常执行，且 Java 版本为 21。随后在**项目根目录**运行：

macOS / Linux：

```sh
./gradlew --version
./gradlew :composeApp:run
```

Windows PowerShell：

```powershell
.\gradlew.bat --version
.\gradlew.bat :composeApp:run
```

首次运行会下载依赖、编译 Rust 原生库、生成 UniFFI Kotlin 绑定，再编译并启动桌面应用，耗时会比后续运行更长。这些步骤由 Gradle 自动串联，无需手动复制动态库或单独生成绑定。

使用 IDE 时，用支持当前 Kotlin 版本的 IntelliJ IDEA 或 Android Studio 打开仓库根目录，设置 Gradle JVM 为 JDK 21，完成同步后运行 Gradle 任务 `:composeApp:run`。

### 编译、测试与打包

下面命令均在项目根目录执行；Windows 将 `./gradlew` 替换为 `.\gradlew.bat`。

```sh
# 编译 JVM 模块及所需 Rust 原生库，不启动窗口
./gradlew :composeApp:jvmJar

# 运行 shared 模块的 JVM 测试（包含桌面 UI 测试，需要图形环境）
./gradlew :shared:jvmTest

# 生成当前系统的 Release 安装包，与发布工作流使用的任务一致
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

`jvmJar` 生成的模块 JAR 不包含完整的运行依赖；日常调试使用 `run`，分发应用使用安装包。安装包输出到 `composeApp/output/main-release/` 下的 `dmg`、`msi`、`exe`、`deb` 或 `rpm` 子目录。安装包自带所需 Java 运行时，使用者无需安装开发工具链。

安装包需在对应操作系统上构建，本地默认面向当前架构；例如 macOS 生成 DMG，Windows 生成 MSI / EXE，Linux 生成 DEB / RPM。多平台构建可参考 [发布工作流](.github/workflows/build-release.yml) 和 [Compose Desktop 打包文档](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)。

### 常见构建问题

- **找不到 Java 21 或提示 JVM 版本不匹配**：核对 `JAVA_HOME`、`java -version`、`./gradlew --version` 及 IDE 的 Gradle JVM，确认它们使用正确的 JDK。
- **找不到 Cargo**：安装 Rust 后重新打开终端或 IDE；也可在 Gradle 命令后附加 `-PcargoExecutable=/absolute/path/to/cargo`（Windows 指向 `cargo.exe`）。
- **提示缺少 C 编译器、链接器或 NASM**：按上表补齐原生编译工具，并检查 `PATH`；只安装 JDK 无法完成 Rust 依赖编译。
- **依赖下载失败**：检查网络和 Gradle / Cargo 代理配置；首次构建不能依赖 `--offline`。

## 技术栈

- [Kotlin Multiplatform](https://kotlinlang.org/lp/multiplatform/)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Rust](https://github.com/rust-lang/rust)
- [mozjpeg](https://github.com/mozilla/mozjpeg)
- [libimagequant](https://github.com/ImageOptim/libimagequant)
- [uniffi-rs](https://github.com/mozilla/uniffi-rs)

有关所使用依赖项的完整列表，请查看 [catalog](/gradle/libs.versions.toml) 文件

## 代码目录

| 目录 | 职责 |
| --- | --- |
| `composeApp/src/jvmMain` | 桌面应用入口和窗口装配 |
| `shared/src/commonMain/kotlin/org/tool/kit/domain` | 业务模型、仓库接口和用例 |
| `shared/src/commonMain/kotlin/org/tool/kit/feature` | 按功能组织页面、Route、ViewModel 和页面状态；`ui` 放公共 UI 组件 |
| `shared/src/commonMain/kotlin/org/tool/kit/core` | 协程、校验等基础能力 |
| `shared/src/jvmMain/kotlin/org/tool/kit/data` | JVM 仓库实现、数据源和生成器；更新传输及响应模型位于 `source/update` |
| `shared/src/jvmMain/kotlin/org/tool/kit/platform` | 文件选择、剪贴板及桌面系统能力 |
| `shared/src/jvmTest/kotlin/org/tool/kit/tests` | 按 `feature`、`domain`、`data`、`core`、`navigation`、`di`、`platform` 分组的测试；共用辅助代码放在 `support` |
| `rust/src` | 通过 UniFFI 提供给 Kotlin 的原生实现 |

设置页统一放在 `feature/setting`，更新弹窗与状态放在 `feature/update`。新增文件应与所属功能或层放在一起，包名与目录保持一致。导航键的包名参与状态序列化，调整位置时需检查已保存状态的恢复。

`composeResources`、`jvmMain/resources`、`composeApp/resources` 和 `composeApp/launcher` 存放资源、配置或打包文件，即使没有 Kotlin 代码也需要保留。`build`、Rust `target` 和 Gradle 缓存是构建产物，不属于源码目录。

## License

```
MIT License

Copyright (c) 2024 LazyIonEs

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
