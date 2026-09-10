package org.tool.kit.domain.repository

/** Only the platform data source invokes the synchronous Rust facade. */
interface ImageProcessor {
    suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int)
    suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int)
    suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int)
    suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int)
    suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float)
}
