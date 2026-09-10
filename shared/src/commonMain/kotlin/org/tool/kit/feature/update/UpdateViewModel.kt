package org.tool.kit.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.*

/** 管理检查、下载和安装请求状态；下载版本号拦截取消后仍返回的进度与结果。 */
class UpdateViewModel(private val repository: UpdateRepository, private val preferences: PreferencesRepository,
    private val effects: AppEffectSink, private val dispatchers: AppDispatchers) : ViewModel() {
    private val _uiState = MutableStateFlow(UpdateUiState())
    val uiState = _uiState.asStateFlow()
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var generation = 0L
    private var installSequence = 0L
    // Held through transport finally/stream cleanup, including cancellation, before path reuse.
    private val downloadLock = Mutex()

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: UpdateIntent) {
        when (intent) {
            is UpdateIntent.Check -> check(intent.showMessage)
            is UpdateIntent.SelectAsset -> if (_uiState.value.downloadState == DownloadState.START && intent.asset in _uiState.value.release?.assets.orEmpty()) {
                _uiState.value = _uiState.value.copy(selectedAsset = intent.asset)
            }
            UpdateIntent.Download -> download()
            UpdateIntent.Cancel -> if (_uiState.value.downloadState == DownloadState.DOWNLOADING) {
                generation++
                downloadJob?.cancel()
                _uiState.value = _uiState.value.copy(downloadState = DownloadState.START, progress = 0f,
                    downloadedBytes = 0, totalBytes = 0, downloadedPath = null)
            } else dismiss()
            UpdateIntent.Dismiss -> if (_uiState.value.downloadState != DownloadState.DOWNLOADING) dismiss()
            UpdateIntent.Install -> {
                val path = _uiState.value.downloadedPath
                if (_uiState.value.downloadState == DownloadState.FINISH && path != null && _uiState.value.installRequest == null) {
                    _uiState.value = _uiState.value.copy(visible = false, installRequest = InstallRequest(++installSequence, path))
                }
            }
            is UpdateIntent.InstallHandled -> if (_uiState.value.installRequest?.id == intent.requestId) {
                _uiState.value = UpdateUiState(checking = _uiState.value.checking)
            }
        }
    }
    private fun dismiss() { _uiState.value = UpdateUiState(checking = _uiState.value.checking) }
    /** 合并重复检查；showMessage 控制最新版本或失败提示，启动静默检查仍可展示可用更新。 */
    private fun check(showMessage: Boolean) {
        if (checkJob?.isActive == true || _uiState.value.downloadState == DownloadState.DOWNLOADING) return
        _uiState.value = UpdateUiState(checking = true)
        checkJob = viewModelScope.launch {
            try {
                when (val result = repository.check()) {
                    is UpdateCheckResult.Available -> {
                        val release = result.release.copy(assets = result.release.assets.toList())
                        _uiState.value = UpdateUiState(visible = release.assets.isNotEmpty(), release = release, selectedAsset = release.assets.firstOrNull())
                    }
                    UpdateCheckResult.Latest -> if (showMessage) notify(UiMessage.Resource(Res.string.it_s_the_latest_version))
                    is UpdateCheckResult.Failed -> if (showMessage) notify(errorMessage(result.error))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (showMessage) notify(errorMessage(UpdateError.NETWORK)) }
            finally { _uiState.value = _uiState.value.copy(checking = false) }
        }
    }
    /** 固定资源和输出目录，等上一轮下载清理完毕后再开始写入，并过滤过期进度。 */
    private fun download() {
        val old = _uiState.value
        val asset = old.selectedAsset ?: return
        if (!old.visible || old.downloadState != DownloadState.START) return
        // 新下载取得独立版本，旧传输即使在取消后回调也无法修改这一轮状态。
        val id = ++generation
        val outputDirectory = preferences.state.value.userData.defaultOutputPath
        _uiState.value = old.copy(downloadState = DownloadState.DOWNLOADING, downloadedPath = null)
        downloadJob = viewModelScope.launch {
            try {
                // 持锁覆盖传输层的 finally，确保取消后的流关闭完成后才能复用目标路径。
                downloadLock.withLock {
                    val result = repository.download(asset, outputDirectory) { downloaded, total ->
                        withContext(dispatchers.main) {
                            if (id == generation) _uiState.value = _uiState.value.copy(downloadedBytes = downloaded, totalBytes = total,
                                progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f)
                        }
                    }
                    ensureActive()
                    if (id != generation) return@withLock
                    when (result) {
                        is UpdateDownloadResult.Downloaded -> _uiState.value = _uiState.value.copy(downloadState = DownloadState.FINISH, downloadedPath = result.path)
                        is UpdateDownloadResult.Failed -> {
                            _uiState.value = _uiState.value.copy(downloadState = DownloadState.START)
                            notify(errorMessage(result.error))
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (id == generation) {
                    _uiState.value = _uiState.value.copy(downloadState = DownloadState.START)
                    notify(errorMessage(UpdateError.NETWORK))
                }
            }
        }
    }
    private suspend fun notify(message: UiMessage) {
        if (!effects.send("update", SnackbarMessage(message), generation)) return
    }
    /** 将网络层业务错误映射为可本地化的页面提示。 */
    private fun errorMessage(error: UpdateError) = UiMessage.Resource(when (error) {
        UpdateError.NETWORK -> Res.string.network_error
        UpdateError.CONNECTION -> Res.string.network_connection_error
        UpdateError.RATE_LIMIT -> Res.string.check_update_remaining_tips
    })
}
