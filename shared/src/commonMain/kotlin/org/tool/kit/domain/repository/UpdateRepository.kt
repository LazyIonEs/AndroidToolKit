package org.tool.kit.domain.repository

data class UpdateAsset(val name: String, val downloadUrl: String)
data class UpdateRelease(val version: String, val htmlUrl: String, val createdAt: String, val body: String?, val assets: List<UpdateAsset>)
enum class UpdateError { NETWORK, CONNECTION, RATE_LIMIT }
sealed interface UpdateCheckResult {
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data object Latest : UpdateCheckResult
    data class Failed(val error: UpdateError) : UpdateCheckResult
}
sealed interface UpdateDownloadResult {
    data class Downloaded(val path: String) : UpdateDownloadResult
    data class Failed(val error: UpdateError) : UpdateDownloadResult
}
interface UpdateRepository {
    /** 检查当前平台是否存在可下载的新版本，网络错误使用业务错误类型返回。 */
    suspend fun check(): UpdateCheckResult
    /** 将指定资源写入输出目录。progress 的两个参数依次为已下载字节数和总字节数；未知总量为 0。 */
    suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult
}
