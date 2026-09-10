package org.tool.kit.domain.icon

/** Values are captured once at Submit; the pipeline never reads live preferences. */
data class IconProcessingOptions(
    val pngAlgorithm: Int,
    val jpegAlgorithm: Int,
    val lossless: Boolean,
    val minimum: Int,
    val target: Int,
    val speed: Int,
    val preset: Int,
    val quality: Float,
)

data class GenerateIconsRequest(
    val inputPath: String,
    val outputPath: String,
    val fileDir: String,
    val iconDir: String,
    val iconName: String,
    val options: IconProcessingOptions,
)

data class IconOutputFiles(val outputPath: String, val temporaryPath: String)

sealed interface GenerateIconsOutcome {
    data class Success(val outputDirectory: String, val outputPaths: List<String>) : GenerateIconsOutcome
    data class Failure(val outputPaths: List<String>, val message: String?) : GenerateIconsOutcome
    /** The old unsupported-extension branch was silent; it must now also release busy. */
    data object UnsupportedInput : GenerateIconsOutcome
}
