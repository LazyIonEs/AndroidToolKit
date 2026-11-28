package org.tool.kit.data.source.rules

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.commons.codec.binary.Hex
import java.io.File
import java.io.FileInputStream
import java.net.ProxySelector
import java.security.MessageDigest

private val logger = KotlinLogging.logger("LibRulesTransport")

interface LibRulesTransport {
    suspend fun fetchManifest(): RulesManifestDto
    suspend fun downloadArtifact(
        relativePath: String,
        destFile: File,
        expectedSize: Long,
        expectedSha256: String,
    )
    suspend fun fetchLibraryDetailJson(uuid: String): String?
}

class KtorLibRulesTransport(
    private val io: CoroutineDispatcher,
) : LibRulesTransport {

    private fun createClient(): HttpClient = HttpClient(Apache5) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 1)
            exponentialDelay()
        }
        engine {
            customizeClient {
                setProxySelector(ProxySelector.getDefault())
            }
        }
    }

    override suspend fun fetchManifest(): RulesManifestDto = withContext(io) {
        val client = createClient()
        try {
            val response = client.get("${RULES_DATA_ROOT}manifest.json")
            require(response.status.isSuccess()) { "Manifest HTTP ${response.status.value}" }
            rulesJson.decodeFromString<RulesManifestDto>(response.bodyAsText())
        } finally {
            client.close()
        }
    }

    override suspend fun downloadArtifact(
        relativePath: String,
        destFile: File,
        expectedSize: Long,
        expectedSha256: String,
    ) = withContext(io) {
        val normalizedPath = relativePath.trimStart('/')
        require(".." !in normalizedPath.split('/')) { "Invalid artifact path: $relativePath" }
        destFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
        if (destFile.exists()) destFile.delete()

        val client = createClient()
        try {
            client.prepareGet("$RULES_DATA_ROOT$normalizedPath").execute { response ->
                require(response.status.isSuccess()) { "Artifact HTTP ${response.status.value}" }
                response.bodyAsChannel().copyAndClose(destFile.writeChannel())
            }
            require(destFile.isFile && destFile.length() == expectedSize) {
                "Artifact size mismatch: expected $expectedSize, got ${destFile.length()}"
            }
            val actualSha256 = sha256Hex(destFile)
            require(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "Artifact SHA-256 mismatch: expected $expectedSha256, got $actualSha256"
            }
        } catch (cancelled: CancellationException) {
            destFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            destFile.delete()
            throw error
        } finally {
            client.close()
        }
    }

    override suspend fun fetchLibraryDetailJson(uuid: String): String? = withContext(io) {
        val urls = listOf(
            "${GITHUB_DETAIL_ROOT}$uuid.json",
            "${GITLAB_DETAIL_ROOT}$uuid.json",
        )
        var lastError: Exception? = null
        var sawNotFound = false
        val client = createClient()
        try {
            for (url in urls) {
                try {
                    val response = client.get(url)
                    if (response.status == HttpStatusCode.NotFound) {
                        sawNotFound = true
                        continue
                    }
                    if (response.status.isSuccess()) {
                        return@withContext response.bodyAsText()
                    }
                    lastError = IllegalStateException("HTTP ${response.status.value} from $url")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    logger.warn(error) { "Detail fetch failed for $url" }
                    lastError = error
                }
            }
        } finally {
            client.close()
        }
        if (sawNotFound && lastError == null) return@withContext null
        if (lastError != null) throw lastError
        null
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return Hex.encodeHexString(digest.digest())
    }

    companion object {
        private const val RULES_DATA_ROOT =
            "https://raw.githubusercontent.com/LibChecker/LibChecker-Rules/rules-data/"
        private const val GITHUB_DETAIL_ROOT =
            "https://raw.githubusercontent.com/LibChecker/LibChecker-Rules/v4/libraries/"
        private const val GITLAB_DETAIL_ROOT =
            "https://gitlab.com/zhaobozhen/LibChecker-Rules/-/raw/v4/libraries/"
    }
}
