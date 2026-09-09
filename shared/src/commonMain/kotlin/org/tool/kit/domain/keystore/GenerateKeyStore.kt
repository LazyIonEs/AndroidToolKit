package org.tool.kit.domain.keystore

enum class KeyStoreFormat { JKS, PKCS12 }

data class GenerateKeyStoreRequest(
    val outputDirectory: String,
    val fileName: String,
    val storePassword: String,
    val aliasPassword: String,
    val alias: String,
    val validityYears: String,
    val authorName: String,
    val organizationalUnit: String,
    val organization: String,
    val city: String,
    val province: String,
    val countryCode: String,
    val format: KeyStoreFormat,
    val keySize: Int,
) {
    override fun toString(): String = "GenerateKeyStoreRequest(redacted)"
}

sealed interface GenerateKeyStoreOutcome {
    data class Success(val outputPath: String) : GenerateKeyStoreOutcome
    data class Failure(val message: String? = null) : GenerateKeyStoreOutcome
}
