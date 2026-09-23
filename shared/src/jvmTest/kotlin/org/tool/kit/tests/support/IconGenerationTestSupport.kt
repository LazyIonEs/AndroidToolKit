package org.tool.kit.tests.support

import org.tool.kit.domain.icon.GenerateIconsRequest
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.GenerateIconsUseCase

internal fun unusedGenerateIcons() = GenerateIconsUseCase(UnusedImageProcessor, object : IconOutputs {
    override suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T = error("Unused icon generation")
})

internal object UnusedImageProcessor : ImageProcessor {
    override suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int): Unit = error("Unused")
    override suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int): Unit = error("Unused")
    override suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int): Unit = error("Unused")
    override suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int): Unit = error("Unused")
    override suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float): Unit = error("Unused")
}
