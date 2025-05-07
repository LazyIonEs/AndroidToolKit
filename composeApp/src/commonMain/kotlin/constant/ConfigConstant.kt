package constant

import model.JpegAlgorithm
import model.PngAlgorithm
import model.UnsignedApk
import utils.resourcesDirWithCommon
import java.io.File


object ConfigConstant {

    /**
     * 未签名APK列表
     */
    val unsignedApkList = listOf(
        UnsignedApk("oppo", File(resourcesDirWithCommon, "oppo_unsigned.apk").absolutePath),
        UnsignedApk("vivo", File(resourcesDirWithCommon, "vivo_unsigned.apk").absolutePath),
        UnsignedApk("huawei", File(resourcesDirWithCommon, "huawei_unsigned.apk").absolutePath),
        UnsignedApk("xiaomi", File(resourcesDirWithCommon, "xiaomi_unsigned.apk").absolutePath),
        UnsignedApk("qq", File(resourcesDirWithCommon, "qq_unsigned.apk").absolutePath),
        UnsignedApk("honor", File(resourcesDirWithCommon, "honor_unsigned.apk").absolutePath)
    )

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