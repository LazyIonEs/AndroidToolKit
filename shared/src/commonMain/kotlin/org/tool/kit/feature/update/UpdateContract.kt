package org.tool.kit.feature.update

import org.tool.kit.domain.repository.UpdateAsset
import org.tool.kit.domain.repository.UpdateRelease
import org.tool.kit.model.DownloadState

data class InstallRequest(val id: Long, val path: String)
data class UpdateUiState(
    val checking: Boolean = false,
    val visible: Boolean = false,
    val release: UpdateRelease? = null,
    val selectedAsset: UpdateAsset? = null,
    val downloadState: DownloadState = DownloadState.START,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloadedPath: String? = null,
    val installRequest: InstallRequest? = null,
)
sealed interface UpdateIntent {
    data class Check(val showMessage: Boolean = true) : UpdateIntent
    data class SelectAsset(val asset: UpdateAsset) : UpdateIntent
    data object Download : UpdateIntent
    data object Cancel : UpdateIntent
    data object Dismiss : UpdateIntent
    data object Install : UpdateIntent
    data class InstallHandled(val requestId: Long) : UpdateIntent
}
