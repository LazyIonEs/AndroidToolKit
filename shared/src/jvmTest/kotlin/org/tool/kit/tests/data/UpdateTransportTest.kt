package org.tool.kit.tests.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.update.*
import org.tool.kit.data.source.update.Asset
import org.tool.kit.shared.generated.resources.*

class UpdateTransportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun realDownloadKeepsKnownUnknownLengthAndFailureCleanup() = runBlocking {
        val payload = ByteArray(131_072) { (it % 251).toByte() }
        withServer { server ->
            server.createContext("/known") { exchange ->
                exchange.sendResponseHeaders(200, payload.size.toLong())
                exchange.responseBody.use { it.write(payload) }
            }
            server.createContext("/unknown") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.responseBody.use { it.write(payload) }
            }
            server.createContext("/broken") { exchange ->
                exchange.sendResponseHeaders(200, payload.size.toLong() * 2)
                try { exchange.responseBody.use { it.write(payload.copyOf(1024)) } } catch (_: Exception) { exchange.close() }
            }
            server.start()
            val root = temporary.root.resolve("nested output with spaces")
            val file = root.resolve("installer.bin")
            val known = mutableListOf<Pair<Long, Long>>()
            assertTrue(downloadUpdateFile(Dispatchers.IO, server.url("known"), file) { a, b -> known += a to b }.isSuccess)
            assertContentEquals(payload, file.readBytes())
            assertTrue(known.isNotEmpty())
            assertTrue(known.all { it.second == payload.size.toLong() })
            val unknown = mutableListOf<Pair<Long, Long>>()
            assertTrue(downloadUpdateFile(Dispatchers.IO, server.url("unknown"), file) { a, b -> unknown += a to b }.isSuccess)
            assertContentEquals(payload, file.readBytes())
            assertTrue(unknown.isNotEmpty())
            assertTrue(unknown.all { it == (0L to 0L) }, "Preserve the old transport's unknown-length counters")
            assertFalse(downloadUpdateFile(Dispatchers.IO, server.url("broken"), file) { _, _ -> }.isSuccess)
            assertFalse(file.exists(), "Failed partial output is deleted by the original error mapping")
        }
    }

    @Test fun realCancellationClosesOldTransferBeforeRetryReusesTheSameOutput() = runBlocking {
        withServer { server ->
            val block = ByteArray(32_768) { 7 }
            server.createContext("/slow") { exchange ->
                exchange.sendResponseHeaders(200, block.size.toLong() * 500)
                try {
                    exchange.responseBody.use { output -> repeat(500) { output.write(block); output.flush(); Thread.sleep(10) } }
                } catch (_: Exception) { exchange.close() }
            }
            val finalBytes = "successful retry".toByteArray()
            server.createContext("/retry") { exchange ->
                exchange.sendResponseHeaders(200, finalBytes.size.toLong())
                exchange.responseBody.use { it.write(finalBytes) }
            }
            server.start()
            val file = temporary.root.resolve("cancel retry.bin")
            val started = CompletableDeferred<Unit>()
            val job = launch {
                downloadUpdateFile(Dispatchers.IO, server.url("slow"), file) { downloaded, _ -> if (downloaded > 0) started.complete(Unit) }
                fail("Cancellation must propagate rather than becoming a network failure")
            }
            withTimeout(15_000) { started.await(); job.cancelAndJoin() }
            assertTrue(job.isCancelled)
            assertTrue(downloadUpdateFile(Dispatchers.IO, server.url("retry"), file) { _, _ -> }.isSuccess)
            assertContentEquals(finalBytes, file.readBytes())
        }
    }

    @Test fun checkTransportHandlesHeadersRateLimitsAndMalformedResponses() = runBlocking {
        withServer { server ->
            var receivedHeaders = emptyMap<String, List<String>>()
            server.createContext("/quota") { exchange ->
                receivedHeaders = exchange.requestHeaders.mapKeys { it.key.lowercase() }
                exchange.responseHeaders.add("x-ratelimit-remaining", "0")
                exchange.sendResponseHeaders(200, -1); exchange.close()
            }
            server.createContext("/malformed") { exchange ->
                val body = "not json".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
            }
            server.start()
            val quota = checkUpdateTransport(Dispatchers.IO, server.url("quota"))
            assertFalse(quota.isSuccess)
            assertEquals(Res.string.check_update_remaining_tips, quota.msg)
            // The io.ktor.http.headers builder constructs a detached Headers value.
            // Preserve the actual wire behavior; fixing the request headers is a separate change.
            assertEquals(listOf("application/json"), receivedHeaders["accept"])
            assertNull(receivedHeaders["x-github-api-version"])
            val malformed = checkUpdateTransport(Dispatchers.IO, server.url("malformed"))
            assertFalse(malformed.isSuccess)
            assertEquals(Res.string.network_error, malformed.msg)
        }
    }

    @Test fun assetSelectionPreservesOrderAndUsesCaseSensitiveArchitectureAndNumericVersions() {
        val names = listOf("tool-MACOS-arm64.dmg", "tool-macos-ARM64.dmg", "tool-macos-aarch64.zip", "tool-windows-x64.exe", "tool-linux-amd64.deb")
        val assets = names.map { name -> Asset("", "", 0, "", name, null, "", "", 0, null, 0, "", null) }
        assertEquals(listOf(names[0], names[2]), assets.filterByOS("Mac OS X", "aarch64")!!.map { it.name })
        assertEquals(listOf(names[3]), assets.filterByOS("Windows 11", "amd64")!!.map { it.name })
        assertEquals(listOf(names[4]), assets.filterByOS("Linux", "x86_64")!!.map { it.name })
        assertNull(assets.filterByOS("unsupported", "arm64"))
        assertTrue(" v2.10.0 ".isNewVersion("2.9"))
        assertFalse("2.0.0".isNewVersion("V2"))
        assertFalse("2.beta".isNewVersion("2.1"))
    }

    private suspend fun withServer(block: suspend (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        try { block(server) } finally { server.stop(0); executor.shutdownNow() }
    }
    private fun HttpServer.url(path: String) = "http://127.0.0.1:${address.port}/$path"
}
