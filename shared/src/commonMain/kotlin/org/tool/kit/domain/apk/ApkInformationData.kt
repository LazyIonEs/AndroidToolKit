package org.tool.kit.domain.apk

/** Encoded source is copied at the boundary and never exposes mutable storage. */
class ApkIconSource(bytes: ByteArray) {
    private val encoded = bytes.copyOf()
    /** 返回独立副本，调用方解码或修改字节数组不会污染已发布的图标状态。 */
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
    /** 以应用名、包名和两个版本字段判断是否取得有效信息，忽略文件大小等基础元数据。 */
    fun isBlank() = label.isBlank() && packageName.isBlank() && versionCode.isBlank() && versionName.isBlank()
}
class ApkCommandFailed : Exception()
class EmptyApkInformation : Exception()
