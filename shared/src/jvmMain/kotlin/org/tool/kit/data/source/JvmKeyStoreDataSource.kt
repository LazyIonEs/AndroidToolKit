package org.tool.kit.data.source

import com.android.ide.common.signing.KeystoreHelper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.keystore.GenerateKeyStoreOutcome
import org.tool.kit.domain.keystore.GenerateKeyStoreRequest
import java.io.File

/** Blocking key generation owns its output until the helper actually returns. */
class JvmKeyStoreDataSource(private val io: CoroutineDispatcher) {
    private val generationLock = Mutex()
    private val logger = KotlinLogging.logger("KeyStoreGeneration")

    suspend fun generate(request: GenerateKeyStoreRequest): GenerateKeyStoreOutcome = generationLock.withLock {
        withContext(io) {
            try {
                val outputFile = File(request.outputDirectory, request.fileName)
                val result = KeystoreHelper.createNewStore(
                    request.format.name,
                    outputFile,
                    request.storePassword,
                    request.aliasPassword,
                    request.alias,
                    "CN=${request.authorName},OU=${request.organizationalUnit},O=${request.organization},L=${request.city},S=${request.province}, C=${request.countryCode}",
                    request.validityYears.toInt(),
                    request.keySize
                )
                if (result) GenerateKeyStoreOutcome.Success(outputFile.path) else GenerateKeyStoreOutcome.Failure()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error { "createSignature 生成签名异常 (${e.javaClass.simpleName})" }
                GenerateKeyStoreOutcome.Failure(e.message)
            }
        }
    }
}
