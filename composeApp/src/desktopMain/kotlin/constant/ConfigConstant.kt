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
}