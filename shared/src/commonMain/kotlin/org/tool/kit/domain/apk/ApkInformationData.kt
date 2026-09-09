package org.tool.kit.domain.apk

/** Encoded source is copied at the boundary and never exposes mutable storage. */
class ApkIconSource(bytes: ByteArray) {
    private val encoded = bytes.copyOf()
    fun bytes(): ByteArray = encoded.copyOf()
}
data class ApkFileMetadata(val size: Long, val md5: String)
data class ApkInformationData(
    val label: String = "",
    val icon: ApkIconSource? = null,
    val size: Long = 0,
    val md5: String = "",
    val packageName: String = "",
    val versionCode: String = "",
    val versionName: String = "",
    val compileSdkVersion: String = "",
    val minSdkVersion: String = "",
    val targetSdkVersion: String = "",
    val usesPermissionList: List<String>? = null,
    val nativeCode: String = "",
    val channel: String? = null,
) {
    fun isBlank() = label.isBlank() && packageName.isBlank() && versionCode.isBlank() && versionName.isBlank()
}
class ApkCommandFailed : Exception()
class EmptyApkInformation : Exception()
