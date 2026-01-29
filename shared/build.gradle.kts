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
    alias(libs.plugins.about.libraries)
}

// Build properties
val kitVersion: String by project
val kitPackageName: String by project
val kitDescription: String by project
val kitCopyright: String by project
val kitVendor: String by project
val kitLicenseFile: String by project

// Rust configuration
val linuxArmTarget = "aarch64-unknown-linux-gnu"
val linuxX64Target = "x86_64-unknown-linux-gnu"
val useCross = (properties.getOrDefault("useCross", "false") as String).toBoolean()
val isLinuxAarch64 = (properties.getOrDefault("isLinuxAarch64", "false") as String).toBoolean()

// Generated source paths
val rustGeneratedSource =
    "${layout.buildDirectory.get()}/generated/source/uniffi/main/org/tool/kit/kotlin"
val aboutLibrariesSource = "src/commonMain/composeResources/files/aboutlibraries.json"

val javaLanguageVersion = JavaLanguageVersion.of(21)

// Group and version
group = "org.tool.kit"
version = kitVersion

kotlin {
    // Java toolchain
    jvmToolchain {
        languageVersion.set(javaLanguageVersion)
    }

    // Target configuration
    jvm()

    // Source sets
    sourceSets {
        // Add Rust generated source to JVM
        jvmMain.get().kotlin.srcDir(rustGeneratedSource)

        // Common dependencies
        commonMain.dependencies {
            // Compose dependencies (API for downstream modules)
            api(libs.compose.runtime)
            api(libs.compose.runtime.saveable)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            api(libs.compose.material.icons.extended.desktop)
            api(libs.compose.navigation3.ui)
            api(libs.compose.adaptive)
            api(libs.compose.adaptive.navigation3)
            api(libs.compose.savedstate.compose)

            // Lifecycle
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.lifecycle.viewmodel.navigation3)

            // Kotlin libraries
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Logging
            api(libs.logging)
            implementation(libs.slf4j.api)
            implementation(libs.logback.core)
            implementation(libs.logback.classic)

            // Android tools (with exclusions to avoid conflicts)
            implementation(libs.android.apksig)
            implementation(libs.android.sdk.common)
            implementation(libs.android.binary.resources)

            // Third-party libraries
            implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
            implementation(libs.commons.codec)
            implementation(libs.asm)
            implementation(libs.jna)

            // File handling
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)

            // Settings
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.multiplatform.settings.serialization)

            // Image handling
            implementation(libs.coil.compose)
            implementation(libs.zoomimage.compose.coil3)

            // APK tools
            implementation(libs.apktool.lib)

            // Network
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.apache5)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // UI components
            implementation(libs.markdown.renderer.jvm)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.compottie)
            implementation(libs.compottie.dot)
            implementation(libs.compottie.resources)

            // About libraries
            implementation(libs.about.libraries.core)
            implementation(libs.about.libraries.compose.m3)

            // IntelliJ utilities (with extensive exclusions)
            implementation("com.jetbrains.intellij.platform:util:253.29346.308") {
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
                exclude(
                    group = "org.jetbrains.intellij.deps.fastutil",
                    module = "intellij-deps-fastutil"
                )
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
                exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json-jvm")
                exclude(group = "org.lz4", module = "lz4-java")
                exclude(group = "org.slf4j", module = "log4j-over-slf4j")
                exclude(group = "oro", module = "oro")
            }
        }

        // JVM-specific dependencies
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            runtimeOnly(libs.kotlinx.coroutines.swing)
        }
    }
}

// Kotlin compiler options
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// About Libraries configuration
aboutLibraries {
    export {
        outputFile = file(aboutLibrariesSource)
    }
}

// BuildConfig generation
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
    buildConfigField("APP_LICENSE_FILE", rootProject.file(kitLicenseFile))
    buildConfigField("AUTHOR_GITHUB_URI", uri("https://github.com/LazyIonEs"))
    buildConfigField("APP_GITHUB_URI", uri("https://github.com/LazyIonEs/AndroidToolKit"))
}

// ========================================
// Rust Integration Configuration
// ========================================

/**
 * Operating system enumeration
 */
enum class OS {
    LINUX,
    WINDOWS,
    MAC
}

/**
 * Determine current operating system
 */
fun currentOs(): OS {
    val os = System.getProperty("os.name")
    return when {
        os.equals("Mac OS X", ignoreCase = true) -> OS.MAC
        os.startsWith("Win", ignoreCase = true) -> OS.WINDOWS
        os.startsWith("Linux", ignoreCase = true) -> OS.LINUX
        else -> error("Unknown OS name: $os")
    }
}

/**
 * Get the destination file for Rust library
 */
fun getRustDestinyLibFile(): File {
    val outputDir = "${layout.buildDirectory.asFile.get().absolutePath}/classes/kotlin/jvm/main"
    val directory = File(outputDir)
    directory.mkdirs()
    val destinyLib = when (currentOs()) {
        OS.LINUX -> "libuniffi_toolkit.so"
        OS.WINDOWS -> "uniffi_toolkit.dll"
        OS.MAC -> "libuniffi_toolkit.dylib"
    }
    return File(directory, destinyLib)
}

/**
 * Get the destination file for generated Kotlin code
 */
fun getRustDestinyKtFile(): File =
    File(rustGeneratedSource + File.separator + "uniffi" + File.separator + "toolkit", "toolkit.kt")

fun getRustLibFile(): File {
    val workingDirPath = if (currentOs() == OS.LINUX && useCross) {
        if (isLinuxAarch64) {
            "rust/target/$linuxArmTarget/release"
        } else {
            "rust/target/$linuxX64Target/release"
        }
    } else {
        "rust/target/release"
    }

    val workingDir = File(rootDir, workingDirPath)

    val originLib = when (currentOs()) {
        OS.LINUX -> "libtoolkit_rs.so"
        OS.WINDOWS -> "toolkit_rs.dll"
        OS.MAC -> "libtoolkit_rs.dylib"
    }

    val originFile = File(workingDir, originLib)
    return originFile
}

/**
 * Build Rust library
 */
fun buildRust() {
    providers.exec {
        println("Building Rust library...")
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

        workingDir = File(rootDir, "rust")
        commandLine = params
    }.result.get()
}

/**
 * Copy built Rust library to destination
 */
fun copyRustBuild() {
    val destinyFile = getRustDestinyLibFile()
    Files.copy(getRustLibFile().toPath(), FileOutputStream(destinyFile))
    println("Rust library copied successfully")
}

/**
 * Generate Kotlin bindings from UDL
 */
fun generateKotlinFromUdl() {
    providers.exec {
        println("Generating Kotlin bindings from UDL...")
        workingDir = File(rootDir, "rust")
        commandLine = listOf(
            "cargo", "run", "--features=uniffi/cli",
            "--bin", "uniffi-bindgen", "generate", "src/toolkit.udl",
            "--language", "kotlin",
            "--out-dir", rustGeneratedSource
        )
    }.result.get()
}

/**
 * Main Rust build task
 */
fun runBuildRust() {
    val destinyKtFile = getRustDestinyKtFile()
    val rustLibFile = getRustLibFile()

    if (!rustLibFile.exists()) {
        buildRust()
    }
    copyRustBuild()
    if (!destinyKtFile.exists()) {
        generateKotlinFromUdl()
    }
}

// ========================================
// Task Configuration
// ========================================

// Register Rust build task
task("rustTasks") {
    runBuildRust()
}

// Export library definitions for aboutlibraries
tasks.getByName("copyNonXmlValueResourcesForCommonMain").dependsOn("exportLibraryDefinitions")

// Ensure Rust is built before Kotlin compilation
tasks.getByName("compileKotlinJvm").doLast {
    runBuildRust()
}
