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
    suspend fun check(): UpdateCheckResult
    suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult
}
