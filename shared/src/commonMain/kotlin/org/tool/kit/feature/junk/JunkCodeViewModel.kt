package org.tool.kit.feature.junk

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.getString
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.*
import org.tool.kit.feature.app.*
import org.tool.kit.model.JunkMode
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.formatFileSize

/** 管理单个与批量 AAR 草稿，随字段变化计算体积估计，提交时固定所选模式的参数。 */
class JunkCodeViewModel(
    private val generate: GenerateJunkCodeUseCase,
    private val estimate: EstimateJunkSizeUseCase,
    private val tokens: JunkTokenGenerator,
    private val preferences: PreferencesRepository,
    storage: StorageRepository,
    private val effects: AppEffectSink,
) : ViewModel() {
    private val initial = preferences.state.value
    private val _uiState = MutableStateFlow(withEstimate(JunkCodeUiState(initial.userData.defaultOutputPath, JunkMode.valueOf(initial.junkMode.name))))
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val output = JunkOutputValidation(viewModelScope, storage)
    private var outputVersion = initial.takeIf { it.ready }?.outputPathVersion
    // 标识本页面接受的操作；关闭或替换操作后，旧任务不能再发布结果。
    private var operationId = 0L

    init {
        viewModelScope.launch { output.state.collect { value -> _uiState.update { it.copy(outputValidation = value) } } }
        output.validate(initial.userData.defaultOutputPath)
        viewModelScope.launch {
            preferences.state.filter { it.ready }.collect { snapshot ->
                val mode = JunkMode.valueOf(snapshot.junkMode.name)
                if (_uiState.value.mode != mode) setState(_uiState.value.copy(mode = mode))
                if (snapshot.outputPathVersion != outputVersion) {
                    outputVersion = snapshot.outputPathVersion
                    onIntent(JunkCodeIntent.OutputPathChanged(snapshot.userData.defaultOutputPath))
                }
            }
        }
        addCloseable { operationId++; output.close(); _uiState.update { it.copy(busy = false) } }
    }

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: JunkCodeIntent) {
        when (intent) {
            JunkCodeIntent.Submit -> submit()
            JunkCodeIntent.Refresh -> output.refresh()
            JunkCodeIntent.RandomSuffix -> onIntent(JunkCodeIntent.SuffixChanged(tokens.generate(3, 8)))
            JunkCodeIntent.RandomPrefix -> onIntent(JunkCodeIntent.ResPrefixChanged(tokens.generate(2, 6) + "_"))
            is JunkCodeIntent.ModeChanged -> {
                preferences.change(PreferenceChange.JunkModeChanged(JunkPreference.valueOf(intent.value.name)))
                setState(_uiState.value.copy(mode = intent.value))
            }
            is JunkCodeIntent.OutputPathChanged -> {
                setState(_uiState.value.copy(outputPath = intent.value))
                output.validate(intent.value)
            }
            else -> {
                val state = _uiState.value
                setState(state.copy(single = JunkFormReducer.single(state.single, intent), multi = JunkFormReducer.multi(state.multi, intent)))
            }
        }
    }

    /** 根据当前模式的数量配置重新生成大小提示，不启动实际文件生成。 */
    private fun withEstimate(state: JunkCodeUiState): JunkCodeUiState {
        val size = estimate(state.configuration())
        val text = size.minimum.formatFileSize(scale = 1) + (size.maximum?.let { " ~ ${it.formatFileSize(scale = 1)}" } ?: "")
        return state.copy(estimatedSize = text)
    }
    private fun setState(state: JunkCodeUiState) { _uiState.value = withEstimate(state) }

    /** 从校验所有者读取最新路径状态，并捕获当前模式配置，避免流收集延迟放行无效路径。 */
    private fun submit() {
        val state = _uiState.value
        if (state.busy) return
        // Read the validation owner synchronously: a just-edited path may not have reached the UI collector yet.
        val check = output.state.value
        val invalid = when {
            check.pending || check.isError -> Res.string.check_error
            state.hasMissingFields() -> Res.string.check_empty
            else -> null
        }
        if (invalid != null) {
            viewModelScope.launch { effects.send("junk-code", SnackbarMessage(UiMessage.Resource(invalid))) }
            return
        }
        val request = GenerateJunkCodeRequest(state.outputPath, state.configuration())
        val id = ++operationId
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val outcome = generate(request)
                currentCoroutineContext().ensureActive()
                if (id != operationId) return@launch
                val message = when (outcome) {
                    is GenerateJunkCodeOutcome.Success -> SnackbarMessage(
                        UiMessage.Text(getString(Res.string.build_end, outcome.result.totalBytes.formatFileSize())),
                        actionLabel = getString(Res.string.jump), withDismissAction = true, duration = SnackbarDuration.Short,
                        action = SnackbarAction.OpenDirectory(outcome.result.outputPath))
                    is GenerateJunkCodeOutcome.Failure -> SnackbarMessage(outcome.message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.build_failure))
                }
                effects.send("junk-code", message, id)
            } finally { if (id == operationId) _uiState.update { it.copy(busy = false) } }
        }
    }
}
