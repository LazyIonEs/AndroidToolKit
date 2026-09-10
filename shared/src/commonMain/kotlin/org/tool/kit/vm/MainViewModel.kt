package org.tool.kit.vm

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.tool.kit.domain.preferences.PreferenceChange
import org.tool.kit.domain.preferences.JunkPreference
import org.tool.kit.feature.app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.JunkCodeInfo
import org.tool.kit.model.JunkMode
import org.tool.kit.model.PendingDeletionFile
import org.tool.kit.model.Sequence
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.build_end
import org.tool.kit.shared.generated.resources.build_failure
import org.tool.kit.shared.generated.resources.cleanup_complete
import org.tool.kit.shared.generated.resources.file_deletion_exception
import org.tool.kit.shared.generated.resources.jump
import org.tool.kit.shared.generated.resources.scanning_anomalies
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.getFileLength
import org.tool.kit.utils.update
import java.io.File

private val logger = KotlinLogging.logger("MainViewModel")

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/1/31 14:45
 * @Description : MainViewModel
 * @Version     : 1.0
 */
class MainViewModel(
    private val preferences: org.tool.kit.domain.preferences.PreferencesRepository,
    private val storage: StorageRepository,
    private val effects: org.tool.kit.feature.app.AppEffectSink,
    private val generateJunk: org.tool.kit.domain.usecase.GenerateJunkCodeUseCase,
    initialCapacity: StorageCapacity = StorageCapacity(0, 0),
) :
    ViewModel() {

    // Read-only legacy projections; remove each when its last feature migrates.
    val themeConfig = preferences.state.map { DarkThemeConfig.valueOf(it.themeConfig.name) }.stateIn(viewModelScope, Eagerly, DarkThemeConfig.valueOf(preferences.state.value.themeConfig.name))
    val userData = preferences.state.map { it.userData }.stateIn(viewModelScope, Eagerly, preferences.state.value.userData)
    val isHuaweiAlignFileSize = preferences.state.map { it.isHuaweiAlignFileSize }.stateIn(viewModelScope, Eagerly, preferences.state.value.isHuaweiAlignFileSize)

    // 垃圾代码生成信息
    private val _junkCodeInfoState = mutableStateOf(JunkCodeInfo())
    val junkCodeInfoState by _junkCodeInfoState

    // 垃圾代码生成UI状态
    private val _junkCodeUIState = mutableStateOf<UIState>(UIState.WAIT)
    val junkCodeUIState by _junkCodeUIState

    val junkMode = preferences.state.map { JunkMode.valueOf(it.junkMode.name) }.stateIn(viewModelScope, Eagerly, JunkMode.valueOf(preferences.state.value.junkMode.name))

    // 扫描的文件列表
    private val _pendingDeletionFileList = mutableStateListOf<PendingDeletionFile>()
    val pendingDeletionFileList: List<PendingDeletionFile> = _pendingDeletionFileList

    // 文件清理UI状态
    private val _fileClearUIState = mutableStateOf<UIState>(UIState.WAIT)
    val fileClearUIState by _fileClearUIState

    // 是否在清理中
    var isClearing = false

    // 文件列表排序
    private var _currentFileSequence = mutableStateOf<Sequence>(Sequence.SIZE_LARGE_TO_SMALL)
    val currentFileSequence by _currentFileSequence

    private val pathChecks = LegacyPathChecks(viewModelScope, storage)
    val pathValidation = pathChecks.state
    private val capacityRequest = LatestRequest(viewModelScope)
    private val _storageCapacity = MutableStateFlow(initialCapacity)
    val storageCapacity = _storageCapacity.asStateFlow()

    init {
        // Compatibility bridge: remove one branch per Phase 4A/5/6/7/8 cutover.
        // Only the junk-code form remains here; migrated features own their subscriptions.
        viewModelScope.launch {
            preferences.state.filter { it.ready }
                .map { it.outputPathVersion to it.userData.defaultOutputPath }
                .distinctUntilChanged().collect { (_, path) ->
                    updateJunkCodeInfo(junkCodeInfoState.copy(outputPath = path))
                }
        }
    }

    fun refreshStorageCapacity() {
        capacityRequest.launch(block = { storage.readCapacity() }) { _storageCapacity.value = it }
    }

    fun hasPendingPathChecks(vararg fields: LegacyPathField): Boolean =
        fields.any { pathValidation.value[it]?.pending == true }

    fun refreshPathChecks(vararg fields: LegacyPathField) = pathChecks.refresh(*fields)

    private fun updateSnackbarVisuals(value: SnackbarMessage) {
        viewModelScope.launch {
            if (!effects.send("legacy", value)) logger.debug { "Window effect sink closed" }
        }
    }
    fun updateSnackbarVisuals(value: String) = updateSnackbarVisuals(SnackbarMessage(UiMessage.Text(value)))
    fun updateSnackbarVisuals(resource: StringResource) = updateSnackbarVisuals(SnackbarMessage(UiMessage.Resource(resource)))

    /**
     * 修改JunkCodeInfo
     * @param junkCodeInfo JunkCodeInfo
     * @see JunkCodeInfo
     */
    fun updateJunkCodeInfo(junkCodeInfo: JunkCodeInfo) {
        _junkCodeInfoState.update { junkCodeInfo }
        pathChecks.validate(LegacyPathField.JUNK_OUTPUT, junkCodeInfo.outputPath, PathKind.DIRECTORY)
    }

    /**
     * 更新垃圾代码模式
     */
    fun saveJunkMode(junkMode: JunkMode) {
        preferences.change(PreferenceChange.JunkModeChanged(JunkPreference.valueOf(junkMode.name)))
    }

    /**
     * 生成垃圾代码 aar
     */
    fun generateJunkCode() {
        if (junkCodeUIState == UIState.Loading) return
        val form = junkCodeInfoState.copy()
        val config = if (junkMode.value == JunkMode.MULTI) {
            org.tool.kit.domain.junk.JunkConfiguration.Multi(form.outputDir, form.aarCount.toIntOrNull() ?: 0,
                form.leastPackageCount.toIntOrNull() ?: 0, form.maximumPackageCount.toIntOrNull() ?: 0,
                form.leastActivityCountPerPackage.toIntOrNull() ?: 0, form.maximumActivityCountPerPackage.toIntOrNull() ?: 0)
        } else org.tool.kit.domain.junk.JunkConfiguration.Single(form.packageName + "." + form.suffix,
            form.packageCount.toIntOrNull() ?: 0, form.activityCountPerPackage.toIntOrNull() ?: 0, form.resPrefix)
        val request = org.tool.kit.domain.junk.GenerateJunkCodeRequest(form.outputPath, config)
        _junkCodeUIState.update { UIState.Loading }
        viewModelScope.launch {
            try {
                when (val outcome = generateJunk(request)) {
                    is org.tool.kit.domain.junk.GenerateJunkCodeOutcome.Success -> updateSnackbarVisuals(SnackbarMessage(
                        UiMessage.Text(getString(Res.string.build_end, outcome.result.totalBytes.formatFileSize())),
                        actionLabel = getString(Res.string.jump), withDismissAction = true, duration = SnackbarDuration.Short,
                        action = SnackbarAction.OpenDirectory(outcome.result.outputPath)))
                    is org.tool.kit.domain.junk.GenerateJunkCodeOutcome.Failure -> updateSnackbarVisuals(outcome.message ?: getString(Res.string.build_failure))
                }
            } finally { _junkCodeUIState.update { UIState.WAIT } }
        }
    }

    /**
     * 扫描自定义文件夹
     */
    fun scanPendingDeletionFileList(directory: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            logger.info { "scanPendingDeletionFileList 扫描自定义文件夹开始" }
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.Loading }
            }
            _pendingDeletionFileList.clear()
            // 文件总大小
            var totalLength = 0L
            directory.walk()
                .maxDepth(10)
                // 如果父目录是缓存目录，不再继续遍历此目录下的文件
                .onEnter { file -> file.parentFile?.nameWithoutExtension != "build" }
                .filter { file -> file.isDirectory && file.nameWithoutExtension == "build" }
                .forEach { file ->
                    val length = file.getFileLength()
                    withContext(Dispatchers.Main) {
                        _pendingDeletionFileList.add(
                            PendingDeletionFile(
                                directoryPath = directory.absolutePath,
                                file = file,
                                filePath = file.absolutePath,
                                fileLastModified = file.lastModified(),
                                fileLength = length
                            )
                        )
                        totalLength += length
                        updateFileSort()
                    }
                }
            logger.info { "scanPendingDeletionFileList 扫描自定义文件夹结束, 耗时: ${System.currentTimeMillis() - start}ms, 扫描目录数: ${_pendingDeletionFileList.size}, 扫描文件总大小: ${totalLength.formatFileSize()}" }
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.WAIT }
                refreshStorageCapacity()
                if (_pendingDeletionFileList.isEmpty()) {
                    updateSnackbarVisuals(Res.string.scanning_anomalies)
                }
            }
        }
    }

    /**
     * 更改文件排序方式
     */
    fun updateFileSort(sequence: Sequence = currentFileSequence) {
        _currentFileSequence.update { sequence }
        when (currentFileSequence) {
            Sequence.DATE_NEW_TO_OLD -> {
                _pendingDeletionFileList.sortByDescending { it.fileLastModified }
            }

            Sequence.DATE_OLD_TO_NEW -> {
                _pendingDeletionFileList.sortBy { it.fileLastModified }
            }

            Sequence.SIZE_LARGE_TO_SMALL -> {
                _pendingDeletionFileList.sortByDescending { it.fileLength }
            }

            Sequence.SIZE_SMALL_TO_LARGE -> {
                _pendingDeletionFileList.sortBy { it.fileLength }
            }

            Sequence.NAME_A_TO_Z -> {
                _pendingDeletionFileList.sortBy { it.filePath }
            }

            Sequence.NAME_Z_TO_A -> {
                _pendingDeletionFileList.sortByDescending { it.filePath }
            }
        }
    }

    /**
     * 改变文件选中状态
     */
    fun changeFileChecked(pendingDeletionFile: PendingDeletionFile, check: Boolean) {
        _pendingDeletionFileList.find { file -> file.file == pendingDeletionFile.file }?.checked =
            check
    }

    /**
     * 关闭文件选择
     */
    fun closeFileCheck() {
        _pendingDeletionFileList.clear()
    }

    /**
     * 全选或取消全选
     */
    fun changeFileAllChecked() {
        val isAllCheck = _pendingDeletionFileList.none { file -> !file.checked }
        _pendingDeletionFileList.forEach { file ->
            if (file.checked == !isAllCheck) return@forEach
            changeFileChecked(file, !isAllCheck)
        }
    }

    /**
     * 移除选中的文件
     */
    fun removeFileChecked() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            logger.info { "removeFileChecked 删除文件夹开始" }
            withContext(Dispatchers.Main) {
                isClearing = true
                _fileClearUIState.update { UIState.Loading }
            }
            val resultList = mutableListOf<Boolean>()
            val fileIterator = _pendingDeletionFileList.iterator()
            var clearLength = 0L
            while (fileIterator.hasNext()) {
                val pendingDeletionFile = fileIterator.next()
                if (pendingDeletionFile.checked) {
                    val result = pendingDeletionFile.file.deleteRecursively()
                    if (result) {
                        clearLength += pendingDeletionFile.fileLength
                        withContext(Dispatchers.Main) {
                            fileIterator.remove()
                        }
                    } else {
                        pendingDeletionFile.exception = true
                    }
                    resultList.add(result)
                }
            }
            val successCount = resultList.filter { it }.size
            val errorCount = resultList.size - successCount
            logger.info { "removeFileChecked 删除文件夹结束, 耗时: ${System.currentTimeMillis() - start}ms, 删除文件数: ${resultList.size}, 删除成功数: $successCount, 删除失败数: $errorCount, 删除文件总大小: ${clearLength.formatFileSize()}" }
            withContext(Dispatchers.Main) {
                isClearing = false
                _fileClearUIState.update { UIState.WAIT }
                refreshStorageCapacity()
                val message = if (errorCount == 0) {
                    // 全部删除成功
                    getString(Res.string.cleanup_complete, clearLength.formatFileSize())
                } else {
                    getString(Res.string.file_deletion_exception, errorCount)
                }
                updateSnackbarVisuals(message)
            }
        }
    }

    /**
     * 当前是否没有选中文件
     */
    fun isAllFileUnchecked(): Boolean = _pendingDeletionFileList.none { file -> file.checked }


}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
}
