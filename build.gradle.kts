plugins {
    // This is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.githubBuildconfig) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.about.libraries) apply false
    alias(libs.plugins.koin.compiler) apply false
    id("org.jetbrains.compose.hot-reload") version "1.2.0" apply false
}

// Root project tasks
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
