package org.tool.kit.feature.apk

import androidx.compose.ui.graphics.ImageBitmap
import org.tool.kit.domain.apk.ApkComponent
import org.tool.kit.domain.apk.ApkArchiveInformation

enum class ApkInformationPhase { Idle, Loading, Result }
data class ApkInformationUiState(
    val phase: ApkInformationPhase = ApkInformationPhase.Idle,
    val inputFile: String? = null,
    val result: ApkInformationResultUi? = null,
) { val busy get() = phase == ApkInformationPhase.Loading }
sealed interface ApkInformationIntent {
    data class ReadApk(val path: String) : ApkInformationIntent
    data class CopyText(val value: String) : ApkInformationIntent
}
data class ApkInformationResultUi(
    val label: String = "", // 名称
    val icon: ImageBitmap? = null, // 图标
    val size: Long = 0L, // 大小
    val md5: String = "", // 文件md5
    val packageName: String = "", // 包名
    val versionCode: String = "", // 版本号
    val versionName: String = "", // 版本
    val compileSdkVersion: String = "", // 编译版本
    val minSdkVersion: String = "", // 最小版本
    val targetSdkVersion: String = "", // 目标版本
    val usesPermissionList: List<String>? = null, // 权限列表
    val nativeCode: String = "", // 架构
    val channel: String? = null, // 渠道
    val sha256: String = "", // APK 文件 SHA-256（非证书指纹）
    val launchableActivity: String = "", // 启动入口
    val components: List<ApkComponent>? = null,
    val archive: ApkArchiveInformation? = null,
) {
    fun isBlank(): Boolean {
        return label.isBlank() && packageName.isBlank() && versionCode.isBlank() && versionName.isBlank()
    }
}
