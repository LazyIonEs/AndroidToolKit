package org.tool.kit.platform

import java.io.File
import org.tool.kit.utils.resourcesDirWithCommon

object DesktopToolResources {
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

}
