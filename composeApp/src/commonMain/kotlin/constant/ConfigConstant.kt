package constant

import model.JpegAlgorithm
import model.PngAlgorithm
import utils.resourcesDirWithCommon
import java.io.File


object ConfigConstant {

    enum class APK(val title: String, val path: String) {
        Oppo("oppo", File(resourcesDirWithCommon, "oppo.apk").absolutePath),
        Vivo("vivo", File(resourcesDirWithCommon, "vivo.apk").absolutePath),
        Huawei("huawei", File(resourcesDirWithCommon, "huawei.apk").absolutePath),
        Xiaomi("xiaomi", File(resourcesDirWithCommon, "xiaomi.apk").absolutePath),
        QQ("qq", File(resourcesDirWithCommon, "qq.apk").absolutePath),
        Honor("honor", File(resourcesDirWithCommon, "honor.apk").absolutePath),
        All("All", "All"),
    }

    val APKTOOL_FILE = File(resourcesDirWithCommon, "apktool.apk")

    /**
     * Android 图标目录
     */
    val ANDROID_ICON_DIR_LIST = listOf("mipmap", "drawable")

    /**
     * ICON 对应 文件/尺寸
     */
    val ICON_FILE_LIST = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    val ICON_SIZE_LIST = listOf(48.toUInt(), 72.toUInt(), 96.toUInt(), 144.toUInt(), 192.toUInt())

    /**
     * ICON PNG 缩放算法
     */
    val ICON_PNG_ALGORITHM = listOf(
        PngAlgorithm.Triangle,
        PngAlgorithm.Catrom,
        PngAlgorithm.Mitchell,
        PngAlgorithm.Lanczos3,
    )

    /**
     * ICON JPEG 缩放算法
     */
    val ICON_JPEG_ALGORITHM = listOf(
        JpegAlgorithm.Bilinear,
        JpegAlgorithm.Hamming,
        JpegAlgorithm.CatmullRom,
        JpegAlgorithm.Mitchell,
        JpegAlgorithm.Gaussian,
        JpegAlgorithm.Lanczos3,
    )
}