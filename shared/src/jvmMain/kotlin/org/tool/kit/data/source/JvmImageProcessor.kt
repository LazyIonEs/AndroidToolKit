package org.tool.kit.data.source

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.domain.repository.ImageProcessor
import org.tool.kit.platform.*
import java.io.File

/** The existing facade keeps all conversions, algorithms and Rust error mapping unchanged. */
class JvmImageProcessor(private val io: CoroutineDispatcher) : ImageProcessor {
    override suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int) = withContext(io) {
        org.tool.kit.platform.resizePng(File(inputPath).absolutePath, File(outputPath).absolutePath,
            size.toUInt(), size.toUInt(), algorithm.toUByte())
    }
    override suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int) = withContext(io) {
        resizeFir(File(inputPath).absolutePath, File(outputPath).absolutePath,
            size.toUInt(), size.toUInt(), algorithm.toUByte())
    }
    override suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int) = withContext(io) {
        oxipng(File(inputPath).absolutePath, File(outputPath).absolutePath, preset)
    }
    override suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int) = withContext(io) {
        quantize(File(inputPath).absolutePath, File(outputPath).absolutePath, minimum, target, speed, preset)
    }
    override suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float) = withContext(io) {
        mozJpeg(File(inputPath).absolutePath, File(outputPath).absolutePath, quality)
    }
}
