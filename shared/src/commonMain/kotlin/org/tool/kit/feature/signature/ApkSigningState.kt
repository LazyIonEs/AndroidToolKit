package org.tool.kit.feature.signature

import org.tool.kit.model.SignaturePolicy

/** Immutable credential composition shared with signing validation. */
data class SigningCredentialsUi(
    val path: String = "", val storePassword: String = "", val aliases: List<String>? = null,
    val aliasIndex: Int = 0, val aliasPassword: String = "",
) {
    override fun toString() = "SigningCredentialsUi(redacted)"
}

data class ApkSignatureForm(
    val apkPath: String = "", val outputPath: String = "", val outputPrefix: String = "",
    val policy: SignaturePolicy = SignaturePolicy.V2,
    val v4FileName: String = "apk-name.apk.idsig",
    val credentials: SigningCredentialsUi = SigningCredentialsUi(),
)

data class ApkSigningValidation(
    val apkError: Boolean = false, val outputError: Boolean = false, val keyError: Boolean = false,
    val apkPending: Boolean = false, val outputPending: Boolean = false, val keyPending: Boolean = false,
    val namePending: Boolean = false, val credentials: SignValidationState = SignValidationState(),
) {
    val pending get() = apkPending || outputPending || keyPending || namePending || credentials.pending
}

data class ApkSigningUiState(
    val form: ApkSignatureForm = ApkSignatureForm(), val validation: ApkSigningValidation = ApkSigningValidation(),
    val busy: Boolean = false,
) {
    val storePasswordError get() = form.credentials.storePassword.isNotBlank() && form.credentials.aliases.isNullOrEmpty()
    val aliasPasswordError get() = !form.credentials.aliases.isNullOrEmpty() && form.credentials.aliasPassword.isNotBlank() && validation.credentials.aliasPasswordValid == false
    val hasError get() = validation.apkError || validation.outputError || validation.keyError || storePasswordError || aliasPasswordError
}

sealed interface ApkSigningIntent {
    data class ApkPathChanged(val value: String) : ApkSigningIntent
    data class OutputPathChanged(val value: String) : ApkSigningIntent
    data class PrefixChanged(val value: String) : ApkSigningIntent
    data class PolicyChanged(val value: SignaturePolicy) : ApkSigningIntent
    data class KeyPathChanged(val value: String) : ApkSigningIntent
    data class StorePasswordChanged(val value: String) : ApkSigningIntent { override fun toString() = "StorePasswordChanged(redacted)" }
    data class AliasChanged(val index: Int) : ApkSigningIntent
    data class AliasPasswordChanged(val value: String) : ApkSigningIntent { override fun toString() = "AliasPasswordChanged(redacted)" }
    data class V4NameChanged(val value: String) : ApkSigningIntent
    data class FilesDropped(val paths: List<String>) : ApkSigningIntent
    data object Refresh : ApkSigningIntent
    data object Submit : ApkSigningIntent
}

/** The path adapter supplies an existing File.name; the reducer never accesses the filesystem. */
object SigningFormReducer {
    fun field(form: ApkSignatureForm, intent: ApkSigningIntent): ApkSignatureForm = when (intent) {
        is ApkSigningIntent.ApkPathChanged -> form.copy(apkPath = intent.value,
            v4FileName = if (intent.value != form.apkPath && intent.value.isBlank())
                (if (form.outputPrefix.isNotBlank()) "${intent.value}-" else "") + "apk-name.apk.idsig" else form.v4FileName)
        is ApkSigningIntent.OutputPathChanged -> form.copy(outputPath = intent.value)
        is ApkSigningIntent.PrefixChanged -> form.copy(outputPrefix = intent.value,
            v4FileName = if (intent.value != form.outputPrefix && form.apkPath.isBlank())
                (if (intent.value.isNotBlank()) "${intent.value}-" else "") + "apk-name.apk.idsig" else form.v4FileName)
        is ApkSigningIntent.PolicyChanged -> form.copy(policy = intent.value)
        is ApkSigningIntent.KeyPathChanged -> if (intent.value == form.credentials.path) form
            else form.copy(credentials = SigningCredentialsUi(path = intent.value))
        is ApkSigningIntent.StorePasswordChanged -> form.copy(credentials = form.credentials.copy(storePassword = intent.value))
        is ApkSigningIntent.AliasChanged -> form.copy(credentials = form.credentials.copy(aliasIndex = intent.index,
            aliasPassword = if (intent.index == form.credentials.aliasIndex) form.credentials.aliasPassword else ""))
        is ApkSigningIntent.AliasPasswordChanged -> form.copy(credentials = form.credentials.copy(aliasPassword = intent.value))
        is ApkSigningIntent.V4NameChanged -> form.copy(v4FileName = intent.value)
        else -> form
    }
    fun resolvedName(form: ApkSignatureForm, existingFileName: String?): ApkSignatureForm =
        if (existingFileName == null || form.apkPath.isBlank()) form else form.copy(v4FileName =
            (if (form.outputPrefix.isNotBlank()) "${form.outputPrefix}-" else "") + existingFileName + ".idsig")
}

data class SigningPreset(val title: String, val path: String)
data class SigningPresets(val items: List<SigningPreset>, val allPath: String, val huaweiPath: String) {
    val batchPaths get() = items.filter { it.path != allPath }.map { it.path }
}
