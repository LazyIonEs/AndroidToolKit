import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hot.reload)
}

// Build properties
val kitVersion: String by project
val kitPackageName: String by project
val kitDescription: String by project
val kitCopyright: String by project
val kitVendor: String by project
val kitLicenseFile: String by project

val javaLanguageVersion = JavaLanguageVersion.of(17)

// Group and version
group = "org.tool.kit"
version = kitVersion

// Exclude conflicting dependencies
configurations.commonMainApi {
    // Exclude BouncyCastle modules (using custom versions in shared/libs)
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
    exclude(group = "net.sf.kxml", module = "kxml2")
}

kotlin {
    // Java toolchain configuration
    jvmToolchain {
        languageVersion.set(javaLanguageVersion)
    }
    
    // Target configuration
    jvm()
    
    // Source sets
    sourceSets {
        // Common dependencies
        commonMain.dependencies {
            implementation(projects.shared)
        }
        
        // JVM-specific dependencies
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

// Kotlin compiler options
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

// Java execution configuration
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(javaLanguageVersion)
    })
}

// ========================================
// Compose Desktop Application Configuration
// ========================================

compose.desktop {
    application {
        mainClass = "org.tool.kit.MainKt"

        // Determine build environment
        val env = if (project.gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }) "release" else "debug"

        // JVM arguments for desktop application
        jvmArgs += listOf(
            "-Dapple.awt.application.appearance=system",  // System appearance on macOS
            "-Djava.net.useSystemProxies=true",           // Use system proxies
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO",  // Logging level
            "-Dkotlin-logging-to-logback=true",           // Logback integration
            "-Dapp.log.dir=${'$'}APPDIR",                 // Log directory
            "-Dapp.log.env=$env",                         // Build environment
        )

        // Depend on Rust build tasks
        this@application.dependsOn("rustTasks")

        // Native distributions configuration
        nativeDistributions {
            // Supported target formats
            targetFormats(
                TargetFormat.Dmg,   // macOS
                TargetFormat.Msi,   // Windows installer
                TargetFormat.Exe,   // Windows executable
                TargetFormat.Deb,   // Debian/Ubuntu
                TargetFormat.Rpm    // RedHat/Fedora
            )
            
            // Application metadata
            packageName = kitPackageName
            packageVersion = kitVersion
            description = kitDescription
            copyright = kitCopyright
            vendor = kitVendor
            
            // Required JVM modules
            modules(
                "java.compiler",
                "java.instrument",
                "java.naming",
                "java.prefs",
                "java.rmi",
                "java.scripting",
                "java.security.jgss",
                "java.sql",
                "jdk.management",
                "jdk.security.auth",
                "jdk.unsupported"
            )

            // Output directories
            outputBaseDir.set(project.layout.projectDirectory.dir("output"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            // Linux-specific configuration
            linux {
                debPackageVersion = packageVersion
                rpmPackageVersion = packageVersion
                debMaintainer = "lazyiones@gmail.com"
                iconFile.set(project.file("launcher/icon.png"))
            }
            
            // macOS-specific configuration
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
            
            // Windows-specific configuration
            windows {
                msiPackageVersion = packageVersion
                exePackageVersion = packageVersion
                menuGroup = packageName
                dirChooser = false
                perUserInstall = true
                shortcut = true
                menu = true
                // UUID for Windows installer upgrade
                upgradeUuid = "2B0C6D0B-BEB7-4E64-807E-BEE0F91C7B04"
                iconFile.set(project.file("launcher/icon.ico"))
            }
        }
        
        // ProGuard configuration for release builds
        buildTypes.release.proguard {
            obfuscate.set(true)
            optimize.set(true)
            joinOutputJars.set(true)
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
    }
}

// Hot reload configuration
tasks.withType<org.jetbrains.compose.reload.gradle.ComposeHotRun>().configureEach {
    mainClass.set("org.tool.kit.MainKt")
}

// Compose compiler configuration
composeCompiler {
    // Enable optimization for non-skipping groups
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}