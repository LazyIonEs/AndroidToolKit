plugins {
    // This is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.githubBuildconfig) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.about.libraries) apply false
    alias(libs.plugins.hot.reload) apply false
}

// Configure all subprojects
subprojects {
    // Ensure consistent Java version across all modules
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}

// Root project tasks
tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}