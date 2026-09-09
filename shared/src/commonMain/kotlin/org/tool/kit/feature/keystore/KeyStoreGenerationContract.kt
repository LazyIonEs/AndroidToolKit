package org.tool.kit.feature.keystore

import androidx.compose.runtime.Immutable

@Immutable
data class KeyStoreForm(
    val keyStorePath: String = "", // 密钥路径
    val keyStoreName: String = "sign.jks", // 密钥名称
    val keyStorePassword: String = "", // 密钥密码
    val keyStoreConfirmPassword: String = "", // 密钥确认密码
    val keyStoreAlisa: String = "", // 别名
    val keyStoreAlisaPassword: String = "", // 别名密码
    val keyStoreAlisaConfirmPassword: String = "", // 别名确认密码
    val validityPeriod: String = "25", // 密码有效期
    val authorName: String = "", // 作者名称
    val organizationalUnit: String = "", // 组织单位
    val organizational: String = "", // 组织
    val city: String = "", // 城市
    val province: String = "", // 省份
    val countryCode: String = "" // 国家编码
) {
    override fun toString(): String = "KeyStoreForm(redacted)"
}

@Immutable
data class KeyStoreValidation(
    val outputPathError: Boolean = false,
    val outputPathPending: Boolean = false,
    val fileNameError: Boolean = false,
    val storeConfirmationError: Boolean = false,
    val aliasConfirmationError: Boolean = false,
) {
    val hasError: Boolean get() = outputPathError || fileNameError || storeConfirmationError || aliasConfirmationError
}

@Immutable
data class KeyStoreGenerationUiState(
    val form: KeyStoreForm = KeyStoreForm(),
    val validation: KeyStoreValidation = KeyStoreValidation(),
    val busy: Boolean = false,
)

sealed interface KeyStoreGenerationIntent {
    data class OutputPathChanged(val value: String) : KeyStoreGenerationIntent
    data class FileNameChanged(val value: String) : KeyStoreGenerationIntent
    data class StorePasswordChanged(val value: String) : KeyStoreGenerationIntent
    data class StoreConfirmationChanged(val value: String) : KeyStoreGenerationIntent
    data class AliasChanged(val value: String) : KeyStoreGenerationIntent
    data class AliasPasswordChanged(val value: String) : KeyStoreGenerationIntent
    data class AliasConfirmationChanged(val value: String) : KeyStoreGenerationIntent
    data class ValidityChanged(val value: String) : KeyStoreGenerationIntent
    data class AuthorNameChanged(val value: String) : KeyStoreGenerationIntent
    data class OrganizationalUnitChanged(val value: String) : KeyStoreGenerationIntent
    data class OrganizationChanged(val value: String) : KeyStoreGenerationIntent
    data class CityChanged(val value: String) : KeyStoreGenerationIntent
    data class ProvinceChanged(val value: String) : KeyStoreGenerationIntent
    data class CountryCodeChanged(val value: String) : KeyStoreGenerationIntent
    data object Submit : KeyStoreGenerationIntent
    data object Refresh : KeyStoreGenerationIntent
}
