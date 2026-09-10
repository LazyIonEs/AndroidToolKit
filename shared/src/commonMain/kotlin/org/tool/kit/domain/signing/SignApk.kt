package org.tool.kit.domain.signing

enum class ApkSigningPolicy { V1, V2, V2Only, V3, V4 }

data class SigningSchemes(val v1: Boolean, val v2: Boolean, val v3: Boolean, val v4: Boolean)
/** 把页面策略展开为 apksig 开关；V2 包含 V1，V2Only 才会关闭 V1。 */
val ApkSigningPolicy.schemes: SigningSchemes get() = when (this) {
    ApkSigningPolicy.V1 -> SigningSchemes(true, false, false, false)
    ApkSigningPolicy.V2 -> SigningSchemes(true, true, false, false)
    ApkSigningPolicy.V2Only -> SigningSchemes(false, true, false, false)
    ApkSigningPolicy.V3 -> SigningSchemes(true, true, true, false)
    ApkSigningPolicy.V4 -> SigningSchemes(true, true, true, true)
}

/** 一次签名所需的凭据；覆盖 toString 避免对象日志泄露密码。 */
data class SigningCredentials(val path: String, val storePassword: String, val alias: String?, val aliasPassword: String) {
    override fun toString() = "SigningCredentials(redacted)"
}

data class SignApkRequest(
    val inputPath: String,
    val outputDirectory: String,
    val prefix: String,
    val suffix: String,
    val overwrite: Boolean,
    val userAlign: Boolean,
    val huaweiAlignment: Boolean,
    val huaweiPresetPath: String,
    val policy: ApkSigningPolicy,
    val v4FileName: String,
    val credentials: SigningCredentials,
) {
    /** 华为预设按配置跳过普通对齐；V4 策略始终强制启用对齐。 */
    val align: Boolean get() = (if (huaweiAlignment) userAlign && inputPath != huaweiPresetPath else userAlign) || policy == ApkSigningPolicy.V4
    /** 拼接可选前缀、原文件名和后缀；仅非空前缀会自动附加连字符。 */
    fun outputFileName(inputNameWithoutExtension: String) =
        (if (prefix.isNotBlank()) "$prefix-" else "") + inputNameWithoutExtension + suffix + ".apk"
}

sealed interface SignApkOutcome {
    /** A single-file request succeeds when the signing engine returns normally. */
    data class Success(val outputPath: String, val outputExists: Boolean) : SignApkOutcome
    data class OutputAlreadyExists(val fileName: String) : SignApkOutcome
    data class Failure(val message: String?) : SignApkOutcome
}
