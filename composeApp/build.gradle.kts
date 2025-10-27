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

val javaLanguageVersion = JavaLanguageVersion.of(17)

val kitVersion = project.property("kitVersion") as String
val kitPackageName = project.property("kitPackageName") as String
val kitDescription = project.property("kitDescription") as String
val kitCopyright = project.property("kitCopyright") as String
val kitVendor = project.property("kitVendor") as String
val kitLicenseFile = project.rootProject.file(project.property("kitLicenseFile") as String)

group = "org.tool.kit"
version = kitVersion

configurations.commonMainApi {
    // com.android.tools:sdk-common
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
    exclude(group = "net.sf.kxml", module = "kxml2")
}

kotlin {
    jvmToolchain {
        languageVersion.set(javaLanguageVersion)
    }
    jvm()
    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
        }
        jvmMain.dependencies {
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
        mainClass = "org.tool.kit.MainKt"

        val env = if (project.gradle.startParameter.taskNames.any {
                it.contains("Release", ignoreCase = true)
            }) "release" else "debug"

        jvmArgs += listOf(
            "-Dapple.awt.application.appearance=system",
            "-Djava.net.useSystemProxies=true",
            "-Dorg.slf4j.simpleLogger.defaultLogLevel=INFO",
            "-Dkotlin-logging-to-logback=true",
            "-Dapp.log.dir=${'$'}APPDIR",
            "-Dapp.log.env=$env",
        )

        this@application.dependsOn("rustTasks")

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Exe,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )
            packageName = kitPackageName
            packageVersion = kitVersion
            description = kitDescription
            copyright = kitCopyright
            vendor = kitVendor
            // licenseFile.set(kitLicenseFile)

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

            outputBaseDir.set(project.layout.projectDirectory.dir("output"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            linux {
                debPackageVersion = packageVersion
                rpmPackageVersion = packageVersion
                debMaintainer = "lazyiones@gmail.com"
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
                dirChooser = false
                perUserInstall = true
                shortcut = true
                menu = true
                upgradeUuid = "2B0C6D0B-BEB7-4E64-807E-BEE0F91C7B04"
                iconFile.set(project.file("launcher/icon.ico"))
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

tasks.withType<org.jetbrains.compose.reload.gradle.ComposeHotRun>().configureEach {
    mainClass.set("org.tool.kit.MainKt")
}

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}