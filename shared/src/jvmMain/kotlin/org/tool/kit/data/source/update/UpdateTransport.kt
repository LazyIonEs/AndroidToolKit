package org.tool.kit.data.source.update

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.hc.core5.http.ConnectionClosedException
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.check_update_remaining_tips
import org.tool.kit.shared.generated.resources.network_connection_error
import org.tool.kit.shared.generated.resources.network_error
import java.io.File
import java.net.ProxySelector
import kotlin.time.Clock
import kotlin.time.ExperimentalTime



private val logger = KotlinLogging.logger("Utils")

private const val TIME_TO_TRIGGER_PROGRESS = 50

/**
 * 下载更新包到目标文件，进度回调按至少 50 毫秒的间隔节流。
 * 普通下载错误会删除目标文件；取消继续传播，并始终关闭 HTTP 客户端。
 * 总长度未知时进度总量为 0，由页面显示不确定进度。
 */
@OptIn(ExperimentalTime::class)
internal suspend fun downloadUpdateFile(
    io: kotlinx.coroutines.CoroutineDispatcher,
    url: String,
    destFile: File,
    onProgress: suspend (downloaded: Long, total: Long) -> Unit
) = withContext(io) {
    logger.info { "downloadFile 开始下载, url: $url, destFile: $destFile" }
    destFile.parentFile?.let { parent ->
        if (!parent.exists()) parent.mkdirs()
    }
    if (destFile.exists()) destFile.delete()

    val client = HttpClient(Apache5) {
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            customizeClient {
                setProxySelector(ProxySelector.getDefault())
            }
        }
    }

    var lastProgressTime = 0L
    try {
        client.prepareGet(url) {
            onDownload { bytesSentTotal: Long, contentLength: Long? ->
                val currentTime = Clock.System.now().toEpochMilliseconds()
                if (currentTime - lastProgressTime >= TIME_TO_TRIGGER_PROGRESS) {
                    onProgress(minOf(bytesSentTotal, contentLength ?: 0L), contentLength ?: 0)
                    lastProgressTime = currentTime
                }
            }
        }.execute { response ->
            response.bodyAsChannel().copyAndClose(destFile.writeChannel())
        }
        DownloadResult(true, null, null)
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (e: ConnectionClosedException) {
        logger.error(e) { "downloadFile 下载异常, 异常信息: ${e.message}" }
        destFile.delete()
        DownloadResult(false, Res.string.network_connection_error, destFile)
    } catch (e: Exception) {
        logger.error(e) { "downloadFile 下载异常, 异常信息: ${e.message}" }
        destFile.delete()
        DownloadResult(false, Res.string.network_error, null)
    } finally {
        client.close()
    }
}

/**
 * 请求 GitHub 最新发布，识别额度耗尽并区分连接中断与其他网络错误。
 * 客户端仅属于本次请求，在成功、失败或取消后关闭。
 */
internal suspend fun checkUpdateTransport(io: kotlinx.coroutines.CoroutineDispatcher, url: String = "https://api.github.com/repos/LazyIonEs/AndroidToolKit/releases/latest") = withContext(io) {
    logger.info { "checkUpdate 开始检查更新" }
    val client = HttpClient(Apache5) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 1)
            exponentialDelay()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
                allowSpecialFloatingPointValues = true
                allowStructuredMapKeys = true
                prettyPrint = false
                useArrayPolymorphism = false
            })
        }
        engine {
            customizeClient {
                setProxySelector(ProxySelector.getDefault())
            }
        }
    }
    try {
        val response: HttpResponse = client.get(url) {
            contentType(ContentType.Application.Json)
            headers {
                append(HttpHeaders.Accept, "application/vnd.github+json")
                append("X-GitHub-Api-Version", "2022-11-28")
            }
        }
        val remaining = response.headers["x-ratelimit-remaining"]
        if (remaining == "0") {
            GithubRestResult(false, Res.string.check_update_remaining_tips, null)
        } else {
            val result: GithubRestLatestResult = response.body()
            GithubRestResult(true, null, result)
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (e: ConnectionClosedException) {
        logger.error(e) { "checkUpdate 检查更新异常, 异常信息: ${e.message}" }
        GithubRestResult(false, Res.string.network_connection_error, null)
    } catch (e: Exception) {
        logger.error(e) { "checkUpdate 检查更新异常, 异常信息: ${e.message}" }
        GithubRestResult(false, Res.string.network_error, null)
    } finally {
        client.close()
    }
}

/**
 * 按点分隔的数字段比较版本，忽略开头的 v/V，缺失或非数字段按 0 处理。
 * 这是项目使用的数字版本比较规则，不解析预发布标签的优先级。
 */
internal fun String.isNewVersion(other: String): Boolean {
    fun normalize(version: String) = version.trim().removePrefix("v").removePrefix("V")
    val parts1 = normalize(this).split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = normalize(other).split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val diff = parts1.getOrElse(i) { 0 } - parts2.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}

/** 根据安装包文件名中的系统和架构标记筛选资源；不支持的系统返回 null。 */
internal fun List<Asset>.filterByOS(osName: String = System.getProperty("os.name"), arch: String = System.getProperty("os.arch")): List<Asset>? {
    val isArm = arch.contains("aarch64", true) || arch.contains("arm64", true)
    val targetArchKeywords = if (isArm) {
        listOf("arm64", "aarch64")
    } else {
        listOf("x64", "x86_64", "amd64")
    }
    val targetOsKeyword = when {
        osName.startsWith("Mac") -> "macos"
        osName.startsWith("Linux") -> "linux"
        osName.startsWith("Win") -> "windows"
        else -> return null
    }
    return this.filter { asset ->
        val osMatches = asset.name.contains(targetOsKeyword, true)
        val archMatches = targetArchKeywords.any { keyword ->
            asset.name.contains(keyword)
        }
        osMatches && archMatches
    }
}

