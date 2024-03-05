package model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/1 20:23
 * @Description : 描述
 * @Version     : 1.0
 */

/**
 * Apk信息
 */
data class ApkInformation(
    var label: String = "",
    var icon: ImageBitmap? = null,
    var size: Long = 0L,
    var md5: String = "",
    var packageName: String = "",
    var versionCode: String = "",
    var versionName: String = "",
    var compileSdkVersion: String = "",
    var minSdkVersion: String = "",
    var targetSdkVersion: String = "",
    var usesPermissionList: ArrayList<String>? = null,
    var nativeCode: String = ""
)

/**
 * Apk签名信息结果
 */
data class VerifierResult(
    val isSuccess: Boolean,
    val isApk: Boolean,
    val path: String,
    val name: String,
    val data: ArrayList<Verifier>
)

/**
 * Apk签名，存储页面信息，viewModel中
 */
data class ApkSignature(
    var apkPath: String = "",
    var outPutPath: String = "",
    var signaturePolicy: SignaturePolicy = SignaturePolicy.V2,
    var signaturePath: String = "",
    var signaturePassword: String = "",
    var signatureAlisa: String = "",
    var signatureAlisaPassword: String = ""
) {
    constructor(apkSignature: ApkSignature) : this(
        apkSignature.apkPath,
        apkSignature.outPutPath,
        apkSignature.signaturePolicy,
        apkSignature.signaturePath,
        apkSignature.signaturePassword,
        apkSignature.signatureAlisa,
        apkSignature.signatureAlisaPassword
    )
}

/**
 * Apk签名更新索引
 */
enum class SignatureEnum {
    APK_PATH,
    OUT_PUT_PATH,
    SIGNATURE_POLICY,
    SIGNATURE_PATH,
    SIGNATURE_PASSWORD,
    SIGNATURE_ALISA,
    SIGNATURE_ALISA_PASSWORD,
}

/**
 * APK签名策略
 */
enum class SignaturePolicy(val title: String, val value: String) {
    V1("V1", "APK Signature Scheme V1"),
    V2("V2", "APK Signature Scheme V2，包含V1"),
    V2Only("V2 Only", "APK Signature Scheme V2，不包含V1"),
    V3("V3", "APK Signature Scheme V3，包含V1和V2")
}

/**
 * 签名信息结果
 */
data class Verifier(
    val version: Int,
    val subject: String,
    val validFrom: String,
    val validUntil: String,
    val publicKeyType: String,
    val modulus: String,
    val signatureType: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

/**
 * 外观
 */
enum class Exterior(val title: String, val mode: Long) {
    AutoMode("跟随系统", 0), LightMode("浅色模式", 1), DarkMode("暗色模式", 2)
}
