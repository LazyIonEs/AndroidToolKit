package org.tool.kit.domain.repository

/** Only the platform data source invokes the synchronous Rust facade. */
interface ImageProcessor {
    /** 以指定算法缩放 PNG 为 size × size 像素，保留 PNG 处理链。 */
    suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int)
    /** 缩放 JPEG 为 size × size 像素，为后续 JPEG 压缩准备输入。 */
    suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int)
    /** 使用指定优化等级无损优化 PNG。 */
    suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int)
    /** 按质量上下限和速度进行调色板量化，再以 preset 优化 PNG。 */
    suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int)
    /** 按 quality 重新编码 JPEG；质量值由提交时的处理选项提供。 */
    suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float)
}
