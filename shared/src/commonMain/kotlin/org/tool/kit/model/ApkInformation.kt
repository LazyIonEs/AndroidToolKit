package org.tool.kit.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:43
 */
/**
 * Apk信息
 */
data class ApkInformation(
    var label: String = "", // 名称
    var icon: ImageBitmap? = null, // 图标
    var size: Long = 0L, // 大小
    var md5: String = "", // 文件md5
    var packageName: String = "", // 包名
    var versionCode: String = "", // 版本号
    var versionName: String = "", // 版本
    var compileSdkVersion: String = "", // 编译版本
    var minSdkVersion: String = "", // 最小版本
    var targetSdkVersion: String = "", // 目标版本
    var usesPermissionList: ArrayList<String>? = null, // 权限列表
    var nativeCode: String = "", // 架构
    var channel: String? = null, // 渠道
) {
    fun isBlank(): Boolean {
        return label.isBlank() && packageName.isBlank() && versionCode.isBlank() && versionName.isBlank()
    }
}