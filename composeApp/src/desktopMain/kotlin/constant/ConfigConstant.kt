package constant

import model.UnsignedApk
import utils.resourcesDirWithCommon
import java.io.File


object ConfigConstant {

    /**
     * 未签名APK列表
     */
    val unsignedApkList = mutableListOf(
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
    val ANDROID_ICON_DIR_LIST = mutableListOf("mipmap", "drawable")

    /**
     * ICON 对应 文件/尺寸
     */
    val ICON_FILE_LIST = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    val ICON_SIZE_LIST = listOf(48.toUInt(), 72.toUInt(), 96.toUInt(), 144.toUInt(), 192.toUInt())
}