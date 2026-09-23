package org.tool.kit.feature.signature

import org.tool.kit.domain.signature.*
import org.tool.kit.model.CopyMode

enum class VerificationPhase { Idle, Loading, Result }
data class PasswordDialogState(
    val path: String,
    val password: String = "",
    val aliases: List<String>? = null,
    val selectedAlias: String = "",
    val pending: Boolean = false,
) { override fun toString() = "PasswordDialogState(redacted)" }
data class VerifierResultUi(val isSuccess: Boolean, val isApk: Boolean, val path: String, val name: String, val data: List<CertificateInformation>)
internal fun SignatureVerification.toUi() = VerifierResultUi(isSuccess, isApk, path, name, data.toList())
data class SignatureInformationUiState(
    val phase: VerificationPhase = VerificationPhase.Idle,
    val result: VerifierResultUi? = null,
    val inputFile: String = "",
    val passwordDialog: PasswordDialogState? = null,
    val copyMode: CopyMode = CopyMode.UPPERCASE_WITH_COLON,
) { val busy get() = phase == VerificationPhase.Loading }
sealed interface SignatureInformationIntent {
    data class VerifyApk(val path: String) : SignatureInformationIntent
    data class KeyStoreSelected(val path: String) : SignatureInformationIntent
    data class PasswordChanged(val value: String) : SignatureInformationIntent
    data class AliasSelected(val value: String) : SignatureInformationIntent
    data object VerifyCertificate : SignatureInformationIntent
    data object DismissPasswordDialog : SignatureInformationIntent
    data class CopyModeChanged(val value: CopyMode) : SignatureInformationIntent
    data class CopyFingerprint(val value: String) : SignatureInformationIntent
    data class CopyText(val value: String) : SignatureInformationIntent
}
