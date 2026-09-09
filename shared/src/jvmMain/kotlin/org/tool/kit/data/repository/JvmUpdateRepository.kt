package org.tool.kit.data.repository

import org.tool.kit.BuildConfig
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.source.*
import org.tool.kit.domain.repository.*
import org.tool.kit.shared.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import java.io.File

class JvmUpdateRepository(private val dispatchers: AppDispatchers) : UpdateRepository {
    override suspend fun check(): UpdateCheckResult {
        val result = checkUpdateTransport(dispatchers.io)
        if (!result.isSuccess) return UpdateCheckResult.Failed(error(result.msg))
        val latest = checkNotNull(result.data)
        if (!latest.tagName.isNewVersion(BuildConfig.APP_VERSION)) return UpdateCheckResult.Latest
        val assets = latest.assets.filterByOS().orEmpty().map { UpdateAsset(it.name, it.browserDownloadUrl) }
        if (assets.isEmpty()) return UpdateCheckResult.Latest
        return UpdateCheckResult.Available(UpdateRelease(latest.tagName, latest.htmlUrl, latest.createdAt, latest.body, assets))
    }
    override suspend fun download(asset: UpdateAsset, outputDirectory: String, progress: suspend (Long, Long) -> Unit): UpdateDownloadResult {
        val file = File(outputDirectory, asset.name)
        val result = downloadUpdateFile(dispatchers.io, asset.downloadUrl, file, progress)
        return if (result.isSuccess) UpdateDownloadResult.Downloaded(file.path) else UpdateDownloadResult.Failed(error(result.msg))
    }
    private fun error(resource: StringResource?): UpdateError = when (resource) {
        Res.string.network_connection_error -> UpdateError.CONNECTION
        Res.string.check_update_remaining_tips -> UpdateError.RATE_LIMIT
        else -> UpdateError.NETWORK
    }
}
