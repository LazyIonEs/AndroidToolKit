package org.tool.kit.domain.signing

enum class ApkSigningPolicy { V1, V2, V2Only, V3, V4 }

data class SigningSchemes(val v1: Boolean, val v2: Boolean, val v3: Boolean, val v4: Boolean)
val ApkSigningPolicy.schemes: SigningSchemes get() = when (this) {
    ApkSigningPolicy.V1 -> SigningSchemes(true, false, false, false)
    ApkSigningPolicy.V2 -> SigningSchemes(true, true, false, false)
    ApkSigningPolicy.V2Only -> SigningSchemes(false, true, false, false)
    ApkSigningPolicy.V3 -> SigningSchemes(true, true, true, false)
    ApkSigningPolicy.V4 -> SigningSchemes(true, true, true, true)
}

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
    val align: Boolean get() = (if (huaweiAlignment) userAlign && inputPath != huaweiPresetPath else userAlign) || policy == ApkSigningPolicy.V4
    fun outputFileName(inputNameWithoutExtension: String) =
        (if (prefix.isNotBlank()) "$prefix-" else "") + inputNameWithoutExtension + suffix + ".apk"
}

sealed interface SignApkOutcome {
    /** The legacy single-file flow reports success when the engine returns normally. */
    data class Success(val outputPath: String, val outputExists: Boolean) : SignApkOutcome
    data class OutputAlreadyExists(val fileName: String) : SignApkOutcome
    data class Failure(val message: String?) : SignApkOutcome
}
