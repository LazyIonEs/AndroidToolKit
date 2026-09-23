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


[简体中文](./README.md) | English

A cross-platform desktop toolkit for Android developers. Inspect certificates and APKs, sign packages, create keystores and APKs, generate icons, and clean build caches on Windows, macOS, and Linux.

[Download the latest release](https://github.com/LazyIonEs/AndroidToolKit/releases/latest) · [Screenshots](#screenshots) · [Build and run](#building-and-running) · [FAQ (中文)](FAQ.md)

> Linux has not been thoroughly tested. Please report problems through [Issues](https://github.com/LazyIonEs/AndroidToolKit/issues).

## Key Features

| Tool | What you can do |
| --- | --- |
| Signature Information | Import APK, JKS, or Keystore files; inspect certificate subjects, validity, algorithms, and MD5 / SHA-1 / SHA-256 fingerprints; view APK signature verification results |
| APK Information | Explore package size, files, permissions, and components; filter native libraries by ABI and inspect ELF / ZIP 16 KB alignment; read versions, SDK levels, and file checksums |
| APK Signing | Sign APKs with a keystore and alias; supports V1, V2, V2 Only, V3, and V4 policies and file alignment |
| Keystore Generation | Create a keystore with a custom alias, passwords, validity period, certificate details, key type, and key size |
| APK Generation | Configure the app name, package name, icon, version, and SDK levels; optionally sign the APK immediately after generation |
| Icon Generation | Generate five Android icon densities from PNG / JPG / JPEG images, preview them together or individually, and customize output paths, names, and compression |
| Cache Cleaner | Scan files or folders with custom rules, preview matches, select items, and confirm deletion |

Supports drag and drop, copying information, and system, light, or dark appearance. Settings also provide a default output path and update checks.

## Download - [Releases](https://github.com/LazyIonEs/AndroidToolKit/releases/latest)

| device  |         chip          |                                                                                                                                                                                                               download                                                                                                                                                                                                                |
|:-------:|:---------------------:|:-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|  macOS  | Apple Silicon (arm64) |                                                                                                                 <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-macos-arm64.dmg"><img src="https://img.shields.io/badge/DMG-Apple%20Silicon-%23000000?logo=Apple" /></a>                                                                                                                 |
|  macOS  |      Intel (x64)      |                                                                                                                    <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-macos-x64.dmg"><img src="https://img.shields.io/badge/DMG-Intel%20x64-%2300A9E0?logo=Apple" /></a>                                                                                                                    |
| Windows |   x64 (Intel / AMD)   |     <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-x64.msi"><img src="https://img.shields.io/badge/MSI-x64-%232d7d9a?logo=writedotas&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-x64.exe"><img src="https://img.shields.io/badge/EXE-x64-%232d7d9a?logo=writedotas&logoColor=white" /></a>     |
| Windows |         ARM64         | <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-arm64.msi"><img src="https://img.shields.io/badge/MSI-arm64-%232d7d9a?logo=writedotas&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-windows-arm64.exe"><img src="https://img.shields.io/badge/EXE-arm64-%232d7d9a?logo=writedotas&logoColor=white" /></a> |
|  Linux  | x64 (AMD64 / x86_64)  |        <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-amd64.deb"><img src="https://img.shields.io/badge/DEB-x64-%23FF9966?logo=debian&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-x86_64.rpm"><img src="https://img.shields.io/badge/RPM-x64-%23F1B42F?logo=redhat&logoColor=white" /></a>         |
|  Linux  |    ARM64 (aarch64)    |     <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-arm64.deb"><img src="https://img.shields.io/badge/DEB-arm64-%23FF9966?logo=debian&logoColor=white" /></a> <a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-linux-aarch64.rpm"><img src="https://img.shields.io/badge/RPM-aarch64-%23F1B42F?logo=redhat&logoColor=white" /></a>     |

If installation fails on Windows due to permissions, try running the installer as administrator. For macOS launch prompts and other troubleshooting, see the [FAQ (中文)](FAQ.md).

## Getting Started

1. **Inspect a file**: Drop an APK into Signature Information or APK Information. For keystores, enter the password and select an alias when prompted.
2. **Sign or generate**: Open the appropriate tool and fill in the input, output directory, and required options. After signing, inspect the output in Signature Information.
3. **Create icons**: Select an image, review the directory, name, and compression options in More settings, then start generation. The UI suggests a 1024 × 1024 px source image.
4. **Clean caches**: Configure rules, then select a project folder or its parent. Review the paths and selected items before confirming deletion.

### Cleanup Rules

Open **Cache Cleaner → Manage rules** to configure file or folder rules:

- Match names and relative paths with equals, starts with, ends with, or contains. File rules also support size thresholds in KB / MB / GB.
- Combine conditions within a rule using ALL or ANY. A match from any enabled rule is included in the results.
- Duplicate, reorder, enable, or disable rules, and choose whether matches are selected by default.
- Use **Save** to apply changes, **Save and try scan** to pick a folder and scan, or **Cancel** to discard the edits.

The default rule matches the exact, case-sensitive folder name `build`; `Build` and `build.foo` do not match. Scanning and deletion do not follow symbolic links, and candidate paths are checked again before deletion. See the [FAQ (中文)](FAQ.md#缓存清理) for details.

## Screenshots

The screenshots below show the updated Chinese interface. Click an image to view it at full size.

| Signature Information · Light | Signature Information · Dark |
|:---:|:---:|
| ![Signature Information · Light](screenshots/signature_info_light.webp) | ![Signature Information · Dark](screenshots/signature_info_dark.webp) |

<details>
<summary>APK Information: from package overview to files and native libraries</summary>

| Import an APK | Package Composition |
|:---:|:---:|
| ![Import an APK](screenshots/apk_info_import.webp) | ![Package Composition](screenshots/apk_info_overview_light.webp) |

| Declared Permissions | Components |
|:---:|:---:|
| ![Declared Permissions](screenshots/apk_info_permissions.webp) | ![Components](screenshots/apk_info_components.webp) |

| Native Libraries and Alignment | App Details |
|:---:|:---:|
| ![Native Libraries and Alignment](screenshots/apk_info_native_libraries.webp) | ![App Details](screenshots/apk_info_app_details.webp) |

![Package Composition · Dark](screenshots/apk_info_overview_dark.webp)

</details>

<details>
<summary>APK Signing, Keystore Generation, and APK Generation</summary>

| APK Signing | Keystore Generation |
|:---:|:---:|
| ![APK Signing](screenshots/apk_signing.webp) | ![Keystore Generation](screenshots/keystore_generation.webp) |

![APK Generation](screenshots/apk_generation.webp)

</details>

<details>
<summary>Icon Generation: five densities and individual previews</summary>

| Icon Overview | Individual Preview |
|:---:|:---:|
| ![Icon Overview](screenshots/icon_generator_overview.webp) | ![Individual Preview](screenshots/icon_generator_preview.webp) |

</details>

<details>
<summary>Cache Cleaner and Custom Rules</summary>

| Cache Cleaner · Light | Cache Cleaner · Dark |
|:---:|:---:|
| ![Cache Cleaner · Light](screenshots/cache_cleaner_light.webp) | ![Cache Cleaner · Dark](screenshots/cache_cleaner_dark.webp) |

![Cleanup Rule Editor](screenshots/cache_cleaner_rules.webp)

</details>

<details>
<summary>Settings and Appearance</summary>

| Settings · Light | Settings · Dark |
|:---:|:---:|
| ![Settings · Light](screenshots/settings_light.webp) | ![Settings · Dark](screenshots/settings_dark.webp) |

</details>

## Building and Running

This is a **Compose Desktop application** built and run on a computer. No Android phone or emulator is required. The Gradle project currently configures JVM targets only, so a normal desktop build does not require a separate Android SDK or NDK installation.

### Hardware and Environment

| Item | Requirement or recommendation |
| --- | --- |
| Computer | macOS, Windows, or Linux with a graphical desktop; the release workflow covers x64 and ARM64. Linux has not been thoroughly tested |
| Hardware | For development, 16 GB or more RAM, an SSD, and tens of GB of free space for Gradle / Cargo caches and packaging output are recommended; these are recommendations, not tested minimums |
| JDK | **JDK 21**. Point `JAVA_HOME` to the JDK installation, add its `bin` directory to `PATH`, and set the IDE's Gradle JVM to 21. The release workflow uses JetBrains JDK 21 |
| Rust | Install a stable Rust toolchain with working `cargo` and `rustc` commands. The project does not pin a Rust toolchain version; the minimum depends on the dependencies resolved by Cargo |
| Gradle | Use the included Wrapper, currently **9.5.1**; no separate Gradle installation is needed |
| Kotlin / Compose | The build currently uses Kotlin **2.4.20** and the Compose Multiplatform plugin **1.12.1**, downloaded automatically by Gradle |
| Other tools | Git, native build tools for your platform, and network access to Gradle, Maven, and Cargo dependency repositories |

For the authoritative versions, see the [version catalog](gradle/libs.versions.toml), [Gradle Wrapper configuration](gradle/wrapper/gradle-wrapper.properties), and [Rust configuration](rust/Cargo.toml).

Rust image-processing dependencies need a C compiler; x64 builds also need NASM. Prepare the following tools:

| Platform | Native compilation tools | Additional installer requirements |
| --- | --- | --- |
| macOS | Xcode Command Line Tools (`xcode-select --install`); on Intel Macs, install a current NASM, for example with `brew install nasm` if Homebrew is available | A full JDK 21 installation with `jpackage` / `jlink` |
| Windows | Visual Studio Build Tools with Desktop development with C++, the Windows SDK, and MSVC tools for the target architecture; for x64, add NASM to `PATH` | WiX Toolset 3.x with discoverable `candle.exe` and `light.exe` |
| Linux | GCC / Clang and basic compilation tools; on Debian / Ubuntu, install `build-essential`, plus `nasm` for x64 | `dpkg` and `fakeroot` for DEB; a package providing `rpmbuild` for RPM |

See the [Rust installation guide](https://doc.rust-lang.org/book/ch01-01-installation.html) and [JDK 21 packaging requirements](https://docs.oracle.com/en/java/javase/21/jpackage/packaging-overview.html). Keep the JDK, default Rust target, and host architecture aligned, particularly on Apple Silicon where ARM64 and x64 toolchains can coexist.

### Get the Source and Run

```sh
git clone https://github.com/LazyIonEs/AndroidToolKit.git
cd AndroidToolKit
```

Check that `java -version`, `javac -version`, `cargo --version`, and `rustc --version` work and that Java reports version 21. Then run from the **repository root**:

macOS / Linux:

```sh
./gradlew --version
./gradlew :composeApp:run
```

Windows PowerShell:

```powershell
.\gradlew.bat --version
.\gradlew.bat :composeApp:run
```

The first run downloads dependencies, compiles the Rust native library, generates UniFFI Kotlin bindings, and then builds and launches the desktop app. It takes longer than subsequent runs. Gradle connects these steps automatically; there is no need to copy native libraries or generate bindings manually.

For IDE development, open the repository root in IntelliJ IDEA or Android Studio with support for the current Kotlin version. Set the Gradle JVM to JDK 21, sync the project, and run the Gradle task `:composeApp:run`.

### Compile, Test, and Package

Run these commands from the repository root. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
# Compile the JVM modules and required Rust library without opening a window
./gradlew :composeApp:jvmJar

# Run shared JVM tests, including desktop UI tests that need a graphical environment
./gradlew :shared:jvmTest

# Create Release installers for the current OS, using the release workflow's task
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
```

The module JAR produced by `jvmJar` does not include all runtime dependencies. Use `run` for development and installers for distribution. Installers are written to the `dmg`, `msi`, `exe`, `deb`, or `rpm` subdirectories of `composeApp/output/main-release/`. They include the required Java runtime, so users do not need the development toolchain.

Build installers on their target OS; local builds use the current architecture by default. macOS produces DMG, Windows produces MSI / EXE, and Linux produces DEB / RPM. See the [release workflow](.github/workflows/build-release.yml) for builds across platforms and the [Compose Desktop packaging guide](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) for details.

### Build Troubleshooting

- **Java 21 is missing or JVM versions do not match**: Check `JAVA_HOME`, `java -version`, `./gradlew --version`, and the IDE's Gradle JVM.
- **Cargo cannot be found**: Restart the terminal or IDE after installing Rust. You can also append `-PcargoExecutable=/absolute/path/to/cargo` to the Gradle command; on Windows, point it to `cargo.exe`.
- **A C compiler, linker, or NASM is missing**: Install the native tools listed above and check `PATH`. A JDK alone cannot build the Rust dependencies.
- **Dependency downloads fail**: Check network access and Gradle / Cargo proxy settings. A first build cannot rely on `--offline`.

## Technology Stack

- [Kotlin Multiplatform](https://kotlinlang.org/lp/multiplatform/)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Rust](https://github.com/rust-lang/rust)
- [mozjpeg](https://github.com/mozilla/mozjpeg)
- [libimagequant](https://github.com/ImageOptim/libimagequant)
- [uniffi-rs](https://github.com/mozilla/uniffi-rs)

For a complete list of dependencies used, check the [catalog](/gradle/libs.versions.toml) file

## Project Architecture

The project uses Compose Desktop, Kotlin Multiplatform, and Rust. The Gradle build has two modules, `:composeApp` and `:shared`, and currently configures only a JVM desktop target. `rust` is a separate Cargo project invoked by Gradle build tasks. `commonMain` and `jvmMain` are source sets within `:shared`, not additional Gradle modules.

### Modules and responsibilities

| Architecture unit | Main responsibilities | Dependency boundary |
| --- | --- | --- |
| `:composeApp` | `main()`, Koin startup, application initialization, desktop window, and installer configuration | Depends on `:shared`; does not implement individual tool workflows |
| `:shared` / `commonMain` | Compose screens, Routes, ViewModels, navigation, theming, domain models, use cases, and repository interfaces; also common data implementations such as preferences and cleaner rules | Use cases access data through interfaces, without referring to desktop repositories or Rust APIs |
| `:shared` / `jvmMain` | Repository and data source implementations, file and process operations, APK tooling, network updates, desktop adapters, and dependency wiring | Implements `commonMain` contracts and calls JVM libraries, system services, and UniFFI bindings |
| `rust` | Image resizing, PNG quantization and optimization, and JPEG re-encoding | Exposed to the JVM through interfaces defined with UniFFI |

The diagram shows the main runtime request path. Repository interfaces are defined in `commonMain`; the desktop Koin modules bind them to concrete implementations.

```mermaid
flowchart TD
    A["composeApp<br/>Entry point · window"] --> B["shared / commonMain<br/>Route · Screen · ViewModel"]
    B --> C["shared / commonMain<br/>UseCase · repository interfaces"]
    C -- "Calls Koin-injected implementation at runtime" --> D["shared / jvmMain<br/>Repositories · data sources · desktop adapters"]
    D --> E["JVM libraries · files · processes · network"]
    D --> F["UniFFI Kotlin bindings"]
    F --> G["rust<br/>Image processing"]
```

### Startup and dependency wiring

1. `main()` calls `startKoin(desktopModules())`. The desktop dependency graph combines dispatchers and settings, desktop data implementations, domain use cases, and page ViewModels.
2. Before creating the window, `AppBootstrap.prepare()` waits for the initial settings load and reads storage capacity. The first screen therefore uses restored settings instead of temporary defaults.
3. `Window { App() }` connects Koin to Compose. The root observes settings to choose the theme and navigation rail options, hosts navigation, global messages, and the update dialog, and starts a silent update check when enabled.
4. Closing the window shuts down Koin idempotently. Navigation entries manage the saved state and lifetime of their page ViewModels separately.

### Feature request flow

Features generally follow **Screen → Route → ViewModel → UseCase → repository interface → desktop implementation**. A Screen renders state and reports actions; its Route connects UI services such as file pickers or drag and drop; the ViewModel handles intents and publishes page state through `StateFlow`; a use case coordinates the business steps. Desktop data sources perform time-consuming file, process, and network work.

- **Icon generation:** `IconFactoryViewModel` calls `GenerateIconsUseCase`, which uses `ImageProcessor` and `IconOutputs` to create icons for five densities. `JvmImageProcessor` invokes the UniFFI bindings on an IO dispatcher, Rust resizes and compresses the images, and the output session manages temporary files.
- **APK inspection and generation:** `ReadApkInformationUseCase` combines package metadata, manifest, icons, and components. `BuildApkUseCase` coordinates template changes, the build, and optional signing. File inspection, `aapt2`, APK tooling, and signing implementations live in the JVM data layer.
- **Update checks:** The root `UpdateViewModel` gets release information and downloads installers through `UpdateRepository`. Its JVM implementation handles HTTP transport and filters release assets for the current OS; a desktop action adapter passes installation requests to the operating system.

### State, navigation, and lifecycle

- Navigation 3 keeps a separate back stack for each top-level page, preserving its history when users switch navigation rail items. Navigation entries own saveable UI state and a ViewModelStore. Navigation keys are serialized by fully qualified class name, so moving or renaming them requires checking restoration of previously saved state.
- `PreferencesRepository.state` publishes snapshots to the root and feature pages. Changes update in-memory state first, then a single writer persists them in order. `revision` and `persistedRevision` distinguish accepted changes from persisted ones.
- Pages send one-time messages through the application-level `AppEffectSink` to the root `AppEffectHost`; leaving a page does not close that channel. The update dialog also lives at the root, independent of any tool page's lifecycle.

### Build and verification

`:shared:rustTasks` coordinates Cargo compilation, UniFFI Kotlin binding generation, and copying the native library as a resource. `jvmMain` includes the generated sources and resources in compilation. `:composeApp` run and packaging tasks depend on this preparation and produce installers for the current operating system. Compose resources, desktop configuration, and installer icons are managed by their respective modules.

Tests live in `:shared`'s `jvmTest` source set and cover feature state, domain use cases, data implementations, navigation, dependency wiring, and platform adapters. See [Compile, Test, and Package](#compile-test-and-package) above for commands.

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
