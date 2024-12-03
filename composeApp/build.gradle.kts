import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FileOutputStream
import java.nio.file.Files

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.githubBuildconfig)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
}

val javaLanguageVersion = JavaLanguageVersion.of(17)
val linuxArmTarget = "aarch64-unknown-linux-gnu"
val linuxX64Target = "x86_64-unknown-linux-gnu"

val kitVersion by extra("1.5.2")
val kitPackageName = "AndroidToolKit"
val kitDescription = "Desktop tools for Android development, supports Windows and Mac"
val kitCopyright = "Copyright (c) 2024 LazyIonEs"
val kitVendor = "LazyIonEs"
val kitLicenseFile = project.rootProject.file("LICENSE")

val useCross = (properties.getOrDefault("useCross", "false") as String).toBoolean()
val isLinuxAarch64 = (properties.getOrDefault("isLinuxAarch64", "false") as String).toBoolean()

val rustGeneratedSource = "${layout.buildDirectory.get()}/generated/source/uniffi/main/org/tool/kit/kotlin"

group = "org.tool.kit"
version = kitVersion

kotlin {
    jvmToolchain {
        languageVersion.set(javaLanguageVersion)
    }

    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.kotlin.srcDir(rustGeneratedSource)

        commonMain.dependencies {
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.foundation)
            implementation(libs.slf4j.api)
            implementation(libs.slf4j.simple)
            implementation(libs.android.apksig)
            implementation(libs.commons.codec)
            implementation(libs.asm)
            implementation(libs.lifecycle.viewmodel.compose)
            runtimeOnly(libs.kotlinx.coroutines.swing)
            implementation(libs.jna)
            implementation("com.android.tools:sdk-common:31.7.2") {
                exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
                exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
                exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
            }
            implementation(libs.filekit.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.binary.resources)
            implementation("com.jetbrains.intellij.platform:util:243.21565.208") {
                exclude(group = "com.fasterxml", module = "aalto-xml")
                exclude(group = "com.github.ben-manes.caffeine", module = "caffeine")
                exclude(group = "com.intellij.platform", module = "kotlinx-coroutines-core-jvm")
                exclude(group = "com.intellij.platform", module = "kotlinx-coroutines-debug")
                exclude(group = "com.jetbrains.intellij.platform", module = "util-jdom")
                exclude(group = "com.jetbrains.intellij.platform", module = "util-class-loader")
                exclude(group = "com.jetbrains.intellij.platform", module = "util-xml-dom")
                exclude(group = "commons-codec", module = "commons-codec")
                exclude(group = "commons-io", module = "commons-io")
                exclude(group = "net.java.dev.jna", module = "jna-platform")
                exclude(group = "org.apache.commons", module = "commons-compress")
                exclude(group = "org.jetbrains.intellij.deps.fastutil", module = "intellij-deps-fastutil")
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json-jvm")
                exclude(group = "org.lz4", module = "lz4-java")
                exclude(group = "org.slf4j", module = "log4j-over-slf4j")
                exclude(group = "oro", module = "oro")
            }
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
    compilerOptions.freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
}

tasks.withType<JavaExec> {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(javaLanguageVersion)
    })
}

compose.desktop {
    application {
        mainClass = "MainKt"

        this@application.dependsOn("rustTasks")

        sourceSets.forEach {
            it.java.srcDir(rustGeneratedSource)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = kitPackageName
            packageVersion = kitVersion
            description = kitDescription
            copyright = kitCopyright
            vendor = kitVendor
            licenseFile.set(kitLicenseFile)

            modules("java.compiler", "java.instrument", "java.naming", "java.prefs", "java.sql", "jdk.management", "jdk.unsupported")

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
            optimize.set(true)
            joinOutputJars.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

task("rustTasks") {
    buildRust()
    copyRustBuild()
    generateKotlinFromUdl()
}

tasks.getByName("compileKotlinDesktop").doLast {
    println("compileKotlinDesktop called")
    buildRust()
    copyRustBuild()
    generateKotlinFromUdl()
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

enum class OS {
    LINUX,
    WINDOWS,
    MAC
}

fun currentOs(): OS {
    val os = System.getProperty("os.name")
    return when {
        os.equals("Mac OS X", ignoreCase = true) -> OS.MAC
        os.startsWith("Win", ignoreCase = true) -> OS.WINDOWS
        os.startsWith("Linux", ignoreCase = true) -> OS.LINUX
        else -> error("Unknown OS name: $os")
    }
}

fun buildRust() {
    exec {
        println("Build rs called")
        val binary = if (currentOs() == OS.LINUX && useCross) {
            "cross"
        } else {
            "cargo"
        }

        val params = mutableListOf(
            binary, "build", "--release", "--features=uniffi/cli",
        )

        if (currentOs() == OS.LINUX && useCross) {
            if (isLinuxAarch64) {
                params.add("--target=$linuxArmTarget")
            } else {
                params.add("--target=$linuxX64Target")
            }
        }

        workingDir = File(rootDir, "rs")
        commandLine = params
    }
}

fun copyRustBuild() {
    val outputDir = "${layout.buildDirectory.asFile.get().absolutePath}/classes/kotlin/desktop/main"

    val workingDirPath = if (currentOs() == OS.LINUX && useCross) {
        if (isLinuxAarch64) {
            "rs/target/$linuxArmTarget/release"
        } else {
            "rs/target/$linuxX64Target/release"
        }
    } else {
        "rs/target/release"
    }

    val workingDir = File(rootDir, workingDirPath)

    val directory = File(outputDir)
    directory.mkdirs()

    val originLib = when (currentOs()) {
        OS.LINUX -> "libtoolkit_rs.so"
        OS.WINDOWS -> "toolkit_rs.dll"
        OS.MAC -> "libtoolkit_rs.dylib"
    }

    val destinyLib = when (currentOs()) {
        OS.LINUX -> "libuniffi_toolkit.so"
        OS.WINDOWS -> "uniffi_toolkit.dll"
        OS.MAC -> "libuniffi_toolkit.dylib"
    }

    val originFile = File(workingDir, originLib)
    val destinyFile = File(directory, destinyLib)

    Files.copy(originFile.toPath(), FileOutputStream(destinyFile))
    println("Copy rs build completed")
}

fun generateKotlinFromUdl() {
    exec {
        workingDir = File(rootDir, "rs")
        commandLine = listOf(
            "cargo", "run", "--features=uniffi/cli",
            "--bin", "uniffi-bindgen", "generate", "src/toolkit.udl",
            "--language", "kotlin",
            "--out-dir", rustGeneratedSource
        )
    }
}