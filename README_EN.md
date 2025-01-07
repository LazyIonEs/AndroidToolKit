<img src="/composeApp/launcher/icon.png" width="56" align="left" />

## AndroidToolKit

<p align="start">
<a href="https://opensource.org/license/mit"><img src="https://img.shields.io/github/license/LazyIonEs/AndroidToolKit?color=green"/></a>
<img alt="Static Badge" src="https://img.shields.io/badge/platform-%20macos%20%7C%20windows%20%7C%20linux%20-5776E0">
<a href="https://github.com/LazyIonEs/AndroidToolKit/actions"><img src="https://img.shields.io/github/actions/workflow/status/LazyIonEs/AndroidToolKit/build-release.yml"/></a>
<a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest"><img src="https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/total?color=orange"/></a>
<a href="https://github.com/LazyIonEs/AndroidToolKit/releases/latest"><img src="https://img.shields.io/github/v/release/LazyIonEs/AndroidToolKit"/></a>
<a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/kotlin-2.1.0-7a54f6"/></a>
</p>


<!-- ![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/LazyIonEs/AndroidToolKit/build-release.yml)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/total)
![GitHub Release](https://img.shields.io/github/v/release/LazyIonEs/AndroidToolKit)
![GitHub License](https://img.shields.io/github/license/LazyIonEs/AndroidToolKit)
![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/LazyIonEs/AndroidToolKit/latest/total) -->


[简体中文](./README.md) | English

Desktop tools applicable to Android development, supporting Windows, Mac and Linux :tada:
> The Linux platform has not been tested. If you have any questions, please give us feedback in time.

## Key Features

- [x] Signature Information - Analyze the signature information of (APK/Signature) (modulus, md5, sha-1, sha-256, etc.)
- [x] APK Information - Parse `AndroidManifest.xml` and extract some information
- [x] APK Signature - Sign your APK
- [x] Signature Generation - Generate a signed certificate
- [x] Icon Generation - Generate icons of multiple sizes with one click
- [ ] ~~Image Compression - Compress images~~

> Supports APK signature verification; single signature verification (signature password required); file dragging; apk
> signature file alignment; generate signatures with specified key type and key size; appearance light and dark modes.

## Download - [Releases](https://github.com/LazyIonEs/AndroidToolKit/releases/latest)

| device  |   chip    |                                                                                                                               download                                                                                                                                |
|:-------:|:---------:|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------:|
|  macos  |   apple   |                                                                    [`macos-arm64.dmg`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-macos-arm64.dmg)                                                                     |
|  macos  |   intel   |                                                                      [`macos-x64.dmg`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-macos-x64.dmg)                                                                       |
| windows | intel/amd |  [`windows-x64.msi`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-windows-x64.msi) **/** [`windows-x64.exe`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-windows-x64.exe)  |
|  linux  |     -     | [`linux-amd64.deb`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-linux-amd64.deb) **/** [`linux-x86_64.rpm`](https://github.com/LazyIonEs/AndroidToolKit/releases/latest/download/AndroidToolKit-1.5.3-linux-x86_64.rpm) |

> [!CAUTION]
> It is not recommended to install the Windows version to the C drive (the default installation path is the C drive :
> clown_face:), which may cause problems such as permissions. If you encounter any problems, please refer
> to [FAQ](FAQ.md)

## screenshot

|                                    Signature Information                                    |                                    Signature Information                                    |                                    Signature Information                                    |
|:-------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------:|
| <img src="./screenshots/screenshot_signature_information_1.png" alt="" style="zoom:33%;" /> | <img src="./screenshots/screenshot_signature_information_2.png" alt="" style="zoom:33%;" /> | <img src="./screenshots/screenshot_signature_information_3.png" alt="" style="zoom:33%;" /> |
|                                       APK Information                                       |                                        APK Signature                                        |                                    Signature generation                                     |
|    <img src="./screenshots/screenshot_apk_information_1.png" alt="" style="zoom:32%;" />    |     <img src="./screenshots/screenshot_apk_signature_1.png" alt="" style="zoom:33%;" />     | <img src="./screenshots/screenshot_signature_generation_1.png" alt="" style="zoom:33%;" />  |

|                                  Icon Generation                                   |                           Black and white theme                           |
|:----------------------------------------------------------------------------------:|:-------------------------------------------------------------------------:|
| <img src="./screenshots/screenshot_icon_factory_1.png" alt="" style="zoom:33%;" /> | <img src="./screenshots/screenshot_light.png" alt="" style="zoom:33%;" /> |
| <img src="./screenshots/screenshot_icon_factory_2.png" alt="" style="zoom:33%;" /> | <img src="./screenshots/screenshot_dark.png" alt="" style="zoom:33%;" />  |

## Technology Stack

- [Kotlin Multiplatform](https://kotlinlang.org/lp/multiplatform/)
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Kotlinx Serialization](https://github.com/Kotlin/kotlinx.serialization)

For a complete list of dependencies used, check the [catalog](/gradle/libs.versions.toml) file

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
