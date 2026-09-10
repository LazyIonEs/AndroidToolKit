package org.tool.kit.feature.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.usecase.DeleteBuildCachesUseCase
import org.tool.kit.domain.usecase.ScanBuildCachesUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.formatFileSize

/** 管理扫描、选择和删除的状态机，以操作版本隔离旧扫描、旧确认和迟到结果。 */
class CleanerViewModel(
    private val scan: ScanBuildCachesUseCase,
    private val delete: DeleteBuildCachesUseCase,
    private val storage: StorageRepository,
    private val effects: AppEffectSink,
    initialCapacity: StorageCapacity = StorageCapacity(0, 0),
) : ViewModel() {
    private val _uiState = MutableStateFlow(CleanerUiState(capacity = initialCapacity))
    val uiState = _uiState.asStateFlow()
    private val capacityRequest = LatestRequest(viewModelScope)
    private var operation: Job? = null
    private var operationId = 0L
    private var confirmationId: Long? = null
    private var closed = false

    init {
        addCloseable {
            closed = true; operationId++; confirmationId = null
            operation?.cancel(); capacityRequest.cancel()
            _uiState.update { it.copy(phase = CleanerPhase.Idle, deleteConfirmVisible = false) }
        }
    }

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: CleanerIntent) {
        if (closed) return
        if (intent == CleanerIntent.RefreshCapacity) { refreshCapacity(); return }
        if (_uiState.value.phase == CleanerPhase.Deleting) return
        when (intent) {
            is CleanerIntent.Rescan -> rescan(intent.root)
            is CleanerIntent.SortChanged -> _uiState.update { it.copy(sort = intent.sort, items = it.items.sortedBy(intent.sort)) }
            is CleanerIntent.ItemCheckedChanged -> _uiState.update { state ->
                state.copy(items = state.items.map { if (it.id == intent.id) it.copy(checked = intent.checked) else it })
            }
            CleanerIntent.ToggleAll -> _uiState.update { state -> state.copy(items = state.items.map { it.copy(checked = !state.allSelected) }) }
            CleanerIntent.CloseSelection -> {
                operationId++; operation?.cancel(); confirmationId = null
                _uiState.update { it.copy(items = emptyList(), phase = CleanerPhase.Idle, deleteConfirmVisible = false) }
            }
            CleanerIntent.RequestDelete -> if (_uiState.value.phase == CleanerPhase.Idle) {
                if (_uiState.value.checkedCount == 0) notifyNoSelection()
                else { confirmationId = operationId; _uiState.update { it.copy(deleteConfirmVisible = true) } }
            }
            CleanerIntent.DismissDelete -> { confirmationId = null; _uiState.update { it.copy(deleteConfirmVisible = false) } }
            CleanerIntent.ConfirmDelete -> confirmDelete()
            CleanerIntent.RefreshCapacity -> Unit
        }
    }

    /** 刷新磁盘容量；读取失败时保留上次展示值，不中断清理流程。 */
    private fun refreshCapacity() {
        capacityRequest.launch(block = {
            try { storage.readCapacity() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }) { capacity -> if (capacity != null) _uiState.update { it.copy(capacity = capacity) } }
    }

    private fun notifyNoSelection() {
        val id = operationId
        viewModelScope.launch { if (id == operationId) effects.send("cleaner", SnackbarMessage(UiMessage.Resource(Res.string.select_delete_director)), id) }
    }

    /** 取消上一轮扫描并清除旧选择，按当前排序持续合并本轮扫描项。 */
    private fun rescan(root: String) {
        val id = ++operationId
        operation?.cancel(); confirmationId = null
        _uiState.update { it.copy(scanRoot = root, items = emptyList(), phase = CleanerPhase.Scanning, deleteConfirmVisible = false) }
        operation = viewModelScope.launch {
            var failed = false
            try {
                scan(root).collect { directory ->
                    currentCoroutineContext().ensureActive()
                    if (id == operationId) _uiState.update { state ->
                        // Stable path identity; retain every distinct full path and sort each discovery using the current sort.
                        val items = state.items.filterNot { it.id == directory.path } + directory.toUi()
                        state.copy(items = items.sortedBy(state.sort))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally {
                if (id == operationId) {
                    _uiState.update { it.copy(phase = CleanerPhase.Idle) }
                    refreshCapacity()
                }
            }
            if (id == operationId && (failed || _uiState.value.items.isEmpty()))
                effects.send("cleaner", SnackbarMessage(UiMessage.Resource(Res.string.scanning_anomalies)), id)
        }
    }

    /** 只接受与当前扫描版本匹配的确认，固定选择后逐项删除并标记失败条目。 */
    private fun confirmDelete() {
        val state = _uiState.value
        // 重扫或关闭选择后，旧确认按钮即使迟到也不能删除新列表中的目录。
        if (!state.deleteConfirmVisible || confirmationId != operationId || state.phase != CleanerPhase.Idle) return
        confirmationId = null
        val selected = state.items.filter { it.checked }.map { it.toDirectory(state.scanRoot.orEmpty()) }
        _uiState.update { it.copy(deleteConfirmVisible = false) }
        if (selected.isEmpty()) { notifyNoSelection(); return }
        val id = ++operationId
        _uiState.update { it.copy(phase = CleanerPhase.Deleting) }
        operation = viewModelScope.launch {
            var releasedBytes = 0L
            var failures = 0
            // 记录尚未收到结果的项目；流中途失败时，这些项目仍需标为失败。
            val remaining = selected.map { it.path }.toMutableSet()
            try {
                delete(selected).collect { result ->
                    currentCoroutineContext().ensureActive()
                    if (id != operationId) return@collect
                    remaining.remove(result.directory.path)
                    if (result.deleted) releasedBytes += result.directory.bytes else failures++
                    _uiState.update { current -> current.copy(items = if (result.deleted) {
                        current.items.filterNot { it.id == result.directory.path }
                    } else current.items.map { item ->
                        if (item.id == result.directory.path) item.copy(deleteFailed = true, isDirectory = result.isDirectory, exists = result.exists) else item
                    }) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                failures += remaining.size
                if (id == operationId) _uiState.update { current ->
                    current.copy(items = current.items.map { if (it.id in remaining) it.copy(deleteFailed = true) else it })
                }
            } finally {
                if (id == operationId) {
                    _uiState.update { it.copy(phase = CleanerPhase.Idle) }
                    refreshCapacity()
                }
            }
            val message = if (failures == 0) getString(Res.string.cleanup_complete, releasedBytes.formatFileSize())
                else getString(Res.string.file_deletion_exception, failures)
            currentCoroutineContext().ensureActive()
            if (id == operationId) effects.send("cleaner", SnackbarMessage(UiMessage.Text(message)), id)
        }
    }
}
