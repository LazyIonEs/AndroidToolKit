package org.tool.kit.domain.repository

interface KeyStoreRepository {
    /** Null on load failure; an empty list is a successfully loaded empty store. */
    suspend fun loadAliases(path: String, password: String): List<String>?
    suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean
}
