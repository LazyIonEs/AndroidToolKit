package org.tool.kit.domain.repository

interface KeyStoreRepository {
    suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome
    /** Null on load failure; an empty list is a successfully loaded empty store. */
    suspend fun loadAliases(path: String, password: String): List<String>?
    suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean
}
