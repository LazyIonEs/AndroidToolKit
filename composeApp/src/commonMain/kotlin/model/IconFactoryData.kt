package model

import kotlinx.serialization.Serializable

@Serializable
data class IconFactoryData(
    val pngTypIdx: PngAlgorithm,
    val jpegTypIdx: JpegAlgorithm,
    val lossless: Boolean,
    val minimum: Int,
    val target: Int,
    val speed: Int,
    val preset: Int,
    val percentage: Float,
    val quality: Float
)

enum class PngAlgorithm(val typIdx: Int) {
    Triangle(0), Catrom(1), Mitchell(2), Lanczos3(3)
}

enum class JpegAlgorithm(val typIdx: Int) {
    Bilinear(0), Hamming(1), CatmullRom(2), Mitchell(3), Gaussian(4), Lanczos3(5)
}