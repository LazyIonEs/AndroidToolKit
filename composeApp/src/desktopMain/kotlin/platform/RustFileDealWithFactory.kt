package platform

import uniffi.toolkit.ToolKitRustException

/**
 * 缩放图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param width 宽度
 * @param height 高度
 * @param typIdx 缩放算法 0 Triangle 1 Catrom 2 Mitchell 3 Lanczos3
 */
@Throws(RustException::class)
actual fun resizePng(inputPath: String, outputPath: String, width: UInt, height: UInt, typIdx: UByte) {
    try {
        uniffi.toolkit.resizePng(
            inputPath = inputPath, outputPath = outputPath, dstWidth = width, dstHeight = height, typIdx = typIdx
        )
    } catch (e: ToolKitRustException) {
        e.printStackTrace()
        throw RustException(e.message)
    }
}

/**
 * 缩放图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param width 宽度
 * @param height 高度
 */
@Throws(RustException::class)
actual fun resizeFir(inputPath: String, outputPath: String, width: UInt, height: UInt) {
    try {
        uniffi.toolkit.resizeFir(
            inputPath = inputPath, outputPath = outputPath, dstWidth = width, dstHeight = height
        )
    } catch (e: ToolKitRustException) {
        e.printStackTrace()
        throw RustException(e.message)
    }
}

/**
 * 量化并压缩图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param minimum 最低限度
 * @param speed 速度
 * @param preset 预设
 */
@Throws(RustException::class)
actual fun quantize(inputPath: String, outputPath: String, minimum: UByte, target: UByte, speed: Int, preset: UByte) {
    try {
        uniffi.toolkit.quantize(
            inputPath = inputPath,
            outputPath = outputPath,
            minimum = minimum,
            target = target,
            speed = speed,
            preset = preset
        )
    } catch (e: ToolKitRustException) {
        e.printStackTrace()
        throw RustException(e.message)
    }
}

/**
 * 压缩图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param quality 质量
 */
@Throws(RustException::class)
actual fun mozJpeg(inputPath: String, outputPath: String, quality: Float) {
    try {
        uniffi.toolkit.mozJpeg(
            inputPath = inputPath, outputPath = outputPath, quality = quality
        )
    } catch (e: ToolKitRustException) {
        e.printStackTrace()
        throw RustException(e.message)
    }
}