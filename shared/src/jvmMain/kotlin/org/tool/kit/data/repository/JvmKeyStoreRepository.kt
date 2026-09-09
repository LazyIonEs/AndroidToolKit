package org.tool.kit.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.domain.repository.KeyStoreRepository
import java.io.FileInputStream
import java.security.KeyStore

private val logger = KotlinLogging.logger("KeyStoreRepository")

class JvmKeyStoreRepository(private val io: CoroutineDispatcher,
    private val source: org.tool.kit.data.source.JvmKeyStoreDataSource = org.tool.kit.data.source.JvmKeyStoreDataSource(io)) : KeyStoreRepository {
    override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest) = source.generate(request)

    override suspend fun loadAliases(path: String, password: String): List<String>? = withContext(io) {
        try {
            val store = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(path).use { store.load(it, password.toCharArray()) }
            val aliases = store.aliases()
            buildList { while (aliases.hasMoreElements()) add(aliases.nextElement()) }
        } catch (e: Exception) {
            // Do not log credentials or exception messages from password providers.
            logger.error { "verifyAlisa 验证签名异常 (${e.javaClass.simpleName})" }
            null
        }
    }

    override suspend fun validateAliasPassword(
        path: String, storePassword: String, alias: String?, password: String
    ): Boolean = withContext(io) {
        try {
            val store = KeyStore.getInstance(KeyStore.getDefaultType())
            FileInputStream(path).use { store.load(it, storePassword.toCharArray()) }
            store.containsAlias(alias) && store.getKey(alias, password.toCharArray()) != null
        } catch (e: Exception) {
            logger.error { "verifyAlisaPassword 验证别名密码异常 (${e.javaClass.simpleName})" }
            false
        }
    }
}
