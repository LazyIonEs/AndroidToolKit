package org.tool.kit.domain.apk

import org.tool.kit.domain.signing.SignApkOutcome
import org.tool.kit.domain.signing.SignApkRequest

/** All build and optional signing values belong to one submission. */
data class BuildApkRequest(
    val outputDirectory: String, val iconPath: String, val packageName: String,
    val targetSdkVersion: String, val minSdkVersion: String, val versionCode: String,
    val versionName: String, val appName: String, val signing: SignApkRequest? = null,
) {
    val outputFileName get() = "$appName.apk"
}

data class ApkBuildWorkspace(val directory: String, val outputPath: String, val frameworkDirectory: String? = null)

sealed interface BuildApkOutcome {
    /** Size and navigation deliberately refer to the original, unsigned output. */
    data class Success(val outputPath: String, val sizeBytes: Long, val signing: SignApkOutcome?) : BuildApkOutcome
    data class Failure(val message: String?) : BuildApkOutcome
}
