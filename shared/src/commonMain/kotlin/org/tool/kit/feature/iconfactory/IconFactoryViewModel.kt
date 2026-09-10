package org.tool.kit.feature.iconfactory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.preferences.PreferenceChange
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.usecase.GenerateIconsUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.isImage

class IconFactoryViewModel(
    private val generate: GenerateIconsUseCase,
    private val preferences: PreferencesRepository,
    private val storage: StorageRepository,
    private val effects: AppEffectSink,
) : ViewModel() {
    private val initial = preferences.state.value
    private val _uiState = MutableStateFlow(IconFactoryUiState(
        IconFactoryForm(outputPath = initial.userData.defaultOutputPath), initial.iconFactoryData))
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val previews = LatestRequest(viewModelScope)
    private var outputVersion = initial.takeIf { it.ready }?.outputPathVersion
    private var operationId = 0L
    private var inputRevision = 0L

    init {
        viewModelScope.launch {
            preferences.state.filter { it.ready }.collect { snapshot ->
                _uiState.update { state -> state.copy(settings = snapshot.iconFactoryData,
                    draft = if (state.sheetOpen) state.draft else IconSettingsDraft.from(snapshot.iconFactoryData)) }
                if (snapshot.outputPathVersion != outputVersion) {
                    outputVersion = snapshot.outputPathVersion
                    onIntent(IconFactoryIntent.OutputPathChanged(snapshot.userData.defaultOutputPath))
                }
            }
        }
        addCloseable {
            operationId++; inputRevision++; previews.cancel()
            _uiState.update { it.copy(busy = false, sheetOpen = false) }
        }
    }

    fun onIntent(intent: IconFactoryIntent) {
        when (intent) {
            IconFactoryIntent.Submit -> submit()
            is IconFactoryIntent.FileSelected -> if (intent.path.isImage) onIntent(IconFactoryIntent.InputChanged(intent.path))
            is IconFactoryIntent.FilesDropped -> intent.paths.firstOrNull()?.let { onIntent(IconFactoryIntent.FileSelected(it)) }
            is IconFactoryIntent.InputChanged -> {
                inputRevision++; previews.cancel()
                _uiState.update { it.copy(form = IconFactoryFormReducer.field(it.form, intent), result = null) }
            }
            is IconFactoryIntent.OutputPathChanged, is IconFactoryIntent.FileDirChanged,
            is IconFactoryIntent.IconDirChanged, is IconFactoryIntent.IconNameChanged ->
                _uiState.update { it.copy(form = IconFactoryFormReducer.field(it.form, intent)) }
            IconFactoryIntent.SheetOpened -> openSheet()
            IconFactoryIntent.SheetClosed, IconFactoryIntent.PageLeft -> _uiState.update {
                it.copy(sheetOpen = false, draft = IconSettingsDraft.from(it.settings))
            }
            IconFactoryIntent.PageEntered -> refreshPreviews()
            IconFactoryIntent.CompressionEditorEntered -> _uiState.update {
                it.copy(draft = it.draft.copy(compressionSpeed = it.settings.percentage * 10f))
            }
            IconFactoryIntent.LossyEditorEntered -> _uiState.update {
                it.copy(draft = it.draft.copy(minimum = it.settings.minimum.toFloat(), target = it.settings.target.toFloat(), jpegQuality = it.settings.quality))
            }
            is IconFactoryIntent.CompressionSpeedChanged, is IconFactoryIntent.PngRangeChanged,
            is IconFactoryIntent.JpegQualityChanged -> _uiState.update { it.copy(draft = IconSettingsReducer.draft(it.draft, intent)) }
            is IconFactoryIntent.LosslessChanged, is IconFactoryIntent.PngAlgorithmChanged,
            is IconFactoryIntent.JpegAlgorithmChanged, IconFactoryIntent.CompressionSpeedCommitted,
            IconFactoryIntent.PngRangeCommitted, IconFactoryIntent.JpegQualityCommitted -> {
                val value = IconSettingsReducer.committed(preferences.state.value.iconFactoryData, _uiState.value.draft, intent)
                preferences.change(PreferenceChange.IconSettings(value))
                _uiState.update { it.copy(settings = value) }
            }
        }
    }

    private fun openSheet() {
        _uiState.update { if (it.sheetOpen) it else it.copy(sheetOpen = true, draft = IconSettingsDraft.from(it.settings)) }
    }

    private suspend fun preview(path: String): IconResultUi {
        val available = try { storage.inspectPath(path).let { it.isFile || it.isDirectory } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
        return IconResultUi(path, available)
    }

    private fun refreshPreviews() {
        val paths = _uiState.value.result?.map { it.path } ?: return
        previews.launch(block = { paths.map { preview(it) } }) { result -> _uiState.update { it.copy(result = result) } }
    }

    private fun submit() {
        val state = _uiState.value
        if (state.busy) return
        val form = state.form
        // Match the original button: iconDir is only an inline error, not a submit gate.
        if (form.outputPath.isBlank() || form.fileDir.isBlank() || form.iconName.isBlank()) {
            openSheet()
            viewModelScope.launch { effects.send("icon-factory", SnackbarMessage(UiMessage.Resource(Res.string.check_error))) }
            return
        }
        val input = form.inputPath ?: return
        val options = preferences.state.value.iconFactoryData
        val request = GenerateIconsRequest(input, form.outputPath, form.fileDir, form.iconDir, form.iconName,
            IconProcessingOptions(options.pngTypIdx.typIdx, options.jpegTypIdx.typIdx, options.lossless,
                options.minimum, options.target, options.speed, options.preset, options.quality))
        val id = ++operationId
        val revision = inputRevision
        previews.cancel()
        _uiState.update { it.copy(busy = true, result = null) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val outcome = generate(request)
                currentCoroutineContext().ensureActive()
                if (id != operationId || revision != inputRevision) return@launch
                val paths = when (outcome) {
                    is GenerateIconsOutcome.Success -> outcome.outputPaths
                    is GenerateIconsOutcome.Failure -> outcome.outputPaths
                    GenerateIconsOutcome.UnsupportedInput -> return@launch
                }
                val results = paths.map { preview(it) }
                currentCoroutineContext().ensureActive()
                if (id != operationId || revision != inputRevision) return@launch
                _uiState.update { it.copy(result = results) }
                val message = when (outcome) {
                    is GenerateIconsOutcome.Success -> SnackbarMessage(UiMessage.Text(getString(Res.string.icon_generation_completed)),
                        actionLabel = getString(Res.string.jump), withDismissAction = true,
                        action = SnackbarAction.OpenDirectory(outcome.outputDirectory))
                    is GenerateIconsOutcome.Failure -> SnackbarMessage(outcome.message?.let(UiMessage::Text)
                        ?: UiMessage.Resource(Res.string.icon_creation_failed))
                    GenerateIconsOutcome.UnsupportedInput -> return@launch
                }
                effects.send("icon-factory", message, id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                if (id == operationId && revision == inputRevision) {
                    _uiState.update { it.copy(result = emptyList()) }
                    effects.send("icon-factory", SnackbarMessage(error.message?.let(UiMessage::Text)
                        ?: UiMessage.Resource(Res.string.icon_creation_failed)), id)
                }
            } finally {
                if (id == operationId) _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
