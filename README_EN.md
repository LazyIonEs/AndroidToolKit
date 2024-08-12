<img src="/composeApp/launcher/icon.png" width="64" align="left" />

## AndroidToolKit

![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/LazyIonEs/AndroidToolKit/build-release.yml)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/total)
![GitHub Release](https://img.shields.io/github/v/release/LazyIonEs/AndroidToolKit)
![GitHub License](https://img.shields.io/github/license/LazyIonEs/AndroidToolKit)
<!-- ![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/latest/total) -->

[简体中文](./README.md) | English

Desktop tools applicable to Android development, supporting Windows, Mac and Linux :tada:
> The Linux platform has not been tested. If you have any questions, please give us feedback in time.

### Key Features
- [x] Signature Information - Analyze the signature information of (APK/Signature) (modulus, md5, sha-1, sha-256, etc.)
- [x] APK Information - Parse `AndroidManifest.xml` and extract some information
- [x] APK Signature - Sign your APK
- [x] Signature Generation - Generate a signed certificate
- [x] Icon Generation - Generate icons of multiple sizes with one click
- [ ] Image Compression - Compress images
- [ ] Check for updates - Check for updates/auto-updates (pending)
- [ ] Custom themes - custom color schemes
> Supports APK signature verification; single signature verification (signature password required); file dragging; apk signature file alignment; generate signatures with specified key type and key size; appearance light and dark modes.

### Download and install
- [Github Releases](https://github.com/LazyIonEs/AndroidToolKit/releases)

| device | chip | download |
|:----:|:----:|:----:|
| windows | amd/intel | AndroidToolKit-Version Number-windows-x64.msi |
| macos | apple | AndroidToolKit-Version Number-macos-arm64.dmg |
| macos | intel | AndroidToolKit-Version Number-macos-x64.dmg |
| linux | - | AndroidToolKit-Version Number-linux.deb |
> [!CAUTION]
> It is not recommended to install the Windows version to the C drive (the default installation path is the C drive :clown_face:), which may cause problems such as permissions. If you encounter any problems, please refer to [FAQ](FAQ.md)

### screenshot
#### Signature Information
![Signature Information](screenshots/screenshot_signature_information_1.png)
![Signature Information](screenshots/screenshot_signature_information_2.png)
![Signature Information](screenshots/screenshot_signature_information_3.png)

#### APK Information
![APK Information](screenshots/screenshot_apk_information_1.png)

#### APK Signature
![APK Signature](screenshots/screenshot_apk_signature_1.png)

#### Signature generation
![Signature generation](screenshots/screenshot_signature_generation_1.png)

#### Icon Generation
![Icon Generation](screenshots/screenshot_icon_factory_1.png)
![Icon Generation](screenshots/screenshot_icon_factory_2.png)

#### Black and white theme
![Black and white theme](screenshots/screenshot_light.png)
![Black and white theme](screenshots/screenshot_dark.png)

### Technology Stack
- [Kotlin Multiplatform](https://kotlinlang.org/lp/multiplatform/)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)

For a complete list of dependencies used, check the [catalog](/gradle/libs.versions.toml) file

### License

AndroidToolKit is licensed under the MIT license.
