package org.tool.kit.tests.support

import org.tool.kit.domain.repository.*

internal object AllPathsExist : StorageRepository {
    override suspend fun readCapacity() = StorageCapacity(1_000, 400)
    override suspend fun inspectPath(path: String) = PathMetadata(true, true)
}

internal object EmptyKeys : KeyStoreRepository {
    override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome = error("Unexpected key generation")
    override suspend fun loadAliases(path: String, password: String): List<String>? = null
    override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String) = false
}
