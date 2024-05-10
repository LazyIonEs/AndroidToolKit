import org.gradle.jvm.tasks.Jar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.sqlDelight)
    alias(libs.plugins.githubBuildconfig)
}

sqldelight {
    databases {
        create("ToolKitDatabase") {
            packageName.set("kit")
        }
    }
}

val kitVersion by extra("1.4.2")
val kitPackageName = "AndroidToolKit"
val kitDescription = "Desktop tools for Android development, supports Windows and Mac"
val kitCopyright = "Copyright (c) 2024 LazyIonEs"
val kitVendor = "LazyIonEs"
val kitLicenseFile = project.rootProject.file("LICENSE")

group = "org.tool.kit"
version = kitVersion

val osName: String = System.getProperty("os.name")
val targetOs = when {
    osName == "Mac OS X" -> "macos"
    osName.startsWith("Win") -> "windows"
    osName.startsWith("Linux") -> "linux"
    else -> error("Unsupported OS: $osName")
}

var targetArch = when (val osArch = System.getProperty("os.arch")) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported arch: $osArch")
}

val target = "${targetOs}-${targetArch}"

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.foundation)
            implementation(libs.sqlDelight.coroutine)
            implementation(libs.sqlDelight.runtime)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.android.apksig)
            implementation("org.jetbrains.skiko:skiko-awt-runtime-$target:0.7.9")
            implementation(libs.mpfilepicker)
            implementation(libs.android.tools.sdk.common)
            implementation(libs.sqlDelight.driver)
            implementation(libs.commons.codec)
            implementation(libs.asm)
        }
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = JavaVersion.VERSION_17.majorVersion
    }

    withType<Jar> {
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.EC")
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = kitPackageName
            packageVersion = kitVersion
            description = kitDescription
            copyright = kitCopyright
            vendor = kitVendor
            licenseFile.set(kitLicenseFile)

            modules("jdk.unsupported", "java.sql")

            outputBaseDir.set(project.layout.projectDirectory.dir("output"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            linux {
                debPackageVersion = packageVersion
                rpmPackageVersion = packageVersion
                iconFile.set(project.file("launcher/icon.png"))
            }
            macOS {
                dmgPackageVersion = packageVersion
                pkgPackageVersion = packageVersion

                packageBuildVersion = packageVersion
                dmgPackageBuildVersion = packageVersion
                pkgPackageBuildVersion = packageVersion
                bundleID = "org.tool.kit"

                dockName = kitPackageName
                iconFile.set(project.file("launcher/icon.icns"))
            }
            windows {
                msiPackageVersion = packageVersion
                exePackageVersion = packageVersion
                menuGroup = packageName
                perUserInstall = true
                shortcut = true
                menu = true
                upgradeUuid = "2B0C6D0B-BEB7-4E64-807E-BEE0F91C7B04"
                iconFile.set(project.file("launcher/icon.ico"))
                installationPath = "AndroidToolKit"
            }
        }
        buildTypes.release.proguard {
            obfuscate.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
            isEnabled = false
        }
    }
}

buildConfig {
    className("BuildConfig")
    packageName("org.tool.kit")
    buildConfigField("APP_NAME", kitPackageName)
    buildConfigField("APP_VERSION", kitVersion)
    buildConfigField("APP_DESCRIPTION", kitDescription)
    buildConfigField("APP_COPYRIGHT", kitCopyright)
    buildConfigField("APP_VENDOR", kitVendor)
    buildConfigField("APP_LICENSE", "MIT License")
    buildConfigField("APP_LICENSE_URI", uri("https://opensource.org/license/mit"))
    buildConfigField("APP_LICENSE_FILE", kitLicenseFile)
    buildConfigField("AUTHOR_GITHUB_URI", uri("https://github.com/LazyIonEs"))
    buildConfigField("APP_GITHUB_URI", uri("https://github.com/LazyIonEs/AndroidToolKit"))
}