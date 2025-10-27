package org.tool.kit.platform

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings

@OptIn(ExperimentalSettingsApi::class)
expect fun createFlowSettings(): FlowSettings

/**
 * 缩放图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param width 宽度
 * @param height 高度
 * @param typIdx 缩放算法 0 Triangle 1 Catrom 2 Mitchell 3 Lanczos3
 */
@Throws(RustException::class)
expect fun resizePng(inputPath: String, outputPath: String, width: UInt, height: UInt, typIdx: UByte = 3u)

/**
 * 缩放图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param width 宽度
 * @param height 高度
 * @param typIdx 缩放算法 0 Bilinear 1 Hamming 2 CatmullRom 3 Mitchell 4 Gaussian 5 Lanczos3
 */
@Throws(RustException::class)
expect fun resizeFir(inputPath: String, outputPath: String, width: UInt, height: UInt, typIdx: UByte = 5u)

/**
 * 量化（有损）并压缩图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param minimum 最低限度
 * @param target 目标质量 如果不能满足最低质量，量化将因错误而中止。默认值为最小值 0，最大值 100，表示尽力而为，并且永不中止该过程。
 * 如果最大值小于 100，则库将尝试使用较少的颜色。颜色较少的图像并不总是较小，因为它会导致抖动增加。
 * @param speed 速度 1 - 10 更快的速度会生成质量较低的图像，但可能对于实时生成图像有用
 * @param preset 预设
 */
@Throws(RustException::class)
expect fun quantize(
    inputPath: String,
    outputPath: String,
    @IntRange(from = 0, to = 100) minimum: Int = 70,
    @IntRange(from = 30, to = 100) target: Int = 100,
    @IntRange(from = 1, to = 10) speed: Int = 1,
    @IntRange(from = 0, to = 6) preset: Int = 6
)

/**
 * 无损压缩PNG
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param preset 预设 1 - 6 预设越高、速度越慢、压缩效果越好
 */
@Throws(RustException::class)
expect fun oxipng(
    inputPath: String,
    outputPath: String,
    @IntRange(from = 0, to = 6) preset: Int = 6
)

/**
 * 压缩图片
 * @param inputPath 输入路径
 * @param outputPath 输出路径
 * @param quality 图像质量。建议值为 60-80
 */
@Throws(RustException::class)
expect fun mozJpeg(inputPath: String, outputPath: String, @FloatRange(from = 0.0, to = 100.0) quality: Float = 85f)

class RustException(
    message: String?
) : Exception(message)