package org.tool.kit.feature.apk

import org.tool.kit.feature.signature.SigningCredentialsUi
import org.tool.kit.feature.signature.SignValidationState
import org.tool.kit.model.SignaturePolicy

/** APK generation fields and composed signing credentials. */
data class ApkToolForm(
    val outputPath: String = "", val icon: String = "", val packageName: String = "org.apk.tool",
    val targetSdkVersion: String = "30", val minSdkVersion: String = "21",
    val versionCode: String = "1", val versionName: String = "1.0", val appName: String = "HelloAndroid",
    val enableSign: Boolean = false, val policy: SignaturePolicy = SignaturePolicy.V3,
    val credentials: SigningCredentialsUi = SigningCredentialsUi(),
)

data class ApkToolValidation(
    val outputError: Boolean = false, val iconError: Boolean = false, val keyError: Boolean = false,
    val outputPending: Boolean = false, val iconPending: Boolean = false, val keyPending: Boolean = false,
    val credentials: SignValidationState = SignValidationState(),
) { val pending get() = outputPending || iconPending || keyPending || credentials.pending }

data class ApkToolUiState(
    val form: ApkToolForm = ApkToolForm(), val validation: ApkToolValidation = ApkToolValidation(),
    val busy: Boolean = false,
) {
    val storePasswordError get() = form.credentials.storePassword.isNotBlank() && form.credentials.aliases.isNullOrEmpty()
    val aliasPasswordError get() = !form.credentials.aliases.isNullOrEmpty() && form.credentials.aliasPassword.isNotBlank() && validation.credentials.aliasPasswordValid == false
}

sealed interface ApkToolIntent {
    data class OutputPathChanged(val value: String) : ApkToolIntent
    data class IconPathChanged(val value: String) : ApkToolIntent
    data class PackageNameChanged(val value: String) : ApkToolIntent
    data class TargetSdkChanged(val value: String) : ApkToolIntent
    data class MinSdkChanged(val value: String) : ApkToolIntent
    data class VersionCodeChanged(val value: String) : ApkToolIntent
    data class VersionNameChanged(val value: String) : ApkToolIntent
    data class AppNameChanged(val value: String) : ApkToolIntent
    data class EnableSignChanged(val value: Boolean) : ApkToolIntent
    data class KeyPathChanged(val value: String) : ApkToolIntent
    data class StorePasswordChanged(val value: String) : ApkToolIntent { override fun toString() = "StorePasswordChanged(redacted)" }
    data class AliasChanged(val index: Int) : ApkToolIntent
    data class AliasPasswordChanged(val value: String) : ApkToolIntent { override fun toString() = "AliasPasswordChanged(redacted)" }
    data class FilesDropped(val paths: List<String>) : ApkToolIntent
    data object Refresh : ApkToolIntent
    data object Submit : ApkToolIntent
}

object ApkToolFormReducer {
    private val number = Regex("^\\d+$")
    private fun String.acceptedNumber() = isEmpty() || matches(number)
    private fun SigningCredentialsUi.path(value: String) = if (path == value) this else SigningCredentialsUi(path = value)
    /** 纯函数归并构建表单：数量字段只接受空串或数字，密钥路径变化时重置对应凭据。 */
    fun field(form: ApkToolForm, intent: ApkToolIntent): ApkToolForm = when (intent) {
        is ApkToolIntent.OutputPathChanged -> form.copy(outputPath = intent.value)
        is ApkToolIntent.IconPathChanged -> form.copy(icon = intent.value)
        is ApkToolIntent.PackageNameChanged -> form.copy(packageName = intent.value)
        is ApkToolIntent.TargetSdkChanged -> if (intent.value.acceptedNumber()) form.copy(targetSdkVersion = intent.value) else form
        is ApkToolIntent.MinSdkChanged -> if (intent.value.acceptedNumber()) form.copy(minSdkVersion = intent.value) else form
        is ApkToolIntent.VersionCodeChanged -> if (intent.value.acceptedNumber()) form.copy(versionCode = intent.value) else form
        is ApkToolIntent.VersionNameChanged -> form.copy(versionName = intent.value)
        is ApkToolIntent.AppNameChanged -> form.copy(appName = intent.value)
        is ApkToolIntent.EnableSignChanged -> form.copy(enableSign = intent.value, credentials = form.credentials.path(""))
        is ApkToolIntent.KeyPathChanged -> form.copy(credentials = form.credentials.path(intent.value))
        is ApkToolIntent.StorePasswordChanged -> form.copy(credentials = form.credentials.copy(storePassword = intent.value))
        is ApkToolIntent.AliasChanged -> form.copy(credentials = form.credentials.copy(aliasIndex = intent.index,
            aliasPassword = if (intent.index == form.credentials.aliasIndex) form.credentials.aliasPassword else ""))
        is ApkToolIntent.AliasPasswordChanged -> form.copy(credentials = form.credentials.copy(aliasPassword = intent.value))
        else -> form
    }
}
