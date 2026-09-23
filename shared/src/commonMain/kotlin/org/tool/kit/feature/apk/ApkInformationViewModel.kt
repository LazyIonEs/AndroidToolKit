package org.tool.kit.feature.apk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.apk.ApkCommandFailed
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.app.ClipboardWriter
import org.tool.kit.feature.app.SnackbarMessage
import org.tool.kit.feature.app.UiMessage
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_parsing_failed
import org.tool.kit.shared.generated.resources.copied_to_clipboard
import org.tool.kit.shared.generated.resources.exec_command_error

/** 管理 APK 信息读取和复制；更换文件后仅接受最新一次读取结果。 */
class ApkInformationViewModel(
    private val read: ReadApkInformationUseCase,
    private val decoder: ApkIconDecoder,
    private val clipboard: ClipboardWriter,
    private val effects: AppEffectSink,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApkInformationUiState())
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val request = LatestRequest(viewModelScope)
    private var operationId = 0L

    init {
        addCloseable {
            request.cancel()
            _uiState.update { if (it.busy) it.copy(phase = ApkInformationPhase.Idle) else it }
        }
    }

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: ApkInformationIntent) {
        when (intent) {
            is ApkInformationIntent.ReadApk -> {
                val id = ++operationId
                _uiState.value = ApkInformationUiState(ApkInformationPhase.Loading, intent.path)
                request.launch(block = {
                    try {
                        val data = read(intent.path).getOrThrow()
                        Result.success(
                            ApkInformationResultUi(
                                label = data.label,
                                icon = data.icon?.let { decoder.decode(it) },
                                size = data.size,
                                md5 = data.md5,
                                packageName = data.packageName,
                                versionCode = data.versionCode,
                                versionName = data.versionName,
                                compileSdkVersion = data.compileSdkVersion,
                                minSdkVersion = data.minSdkVersion,
                                targetSdkVersion = data.targetSdkVersion,
                                usesPermissionList = data.usesPermissionList?.toList(),
                                nativeCode = data.nativeCode,
                                channel = data.channel,
                                sha256 = data.sha256,
                                launchableActivity = data.launchableActivity,
                                components = data.components,
                                archive = data.archive
                            )
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }) { outcome ->
                    outcome.fold(onSuccess = { result ->
                        _uiState.update {
                            it.copy(
                                phase = ApkInformationPhase.Result,
                                result = result
                            )
                        }
                    }, onFailure = { error ->
                        _uiState.update { it.copy(phase = ApkInformationPhase.Idle, result = null) }
                        val message =
                            if (error is ApkCommandFailed) UiMessage.Resource(Res.string.exec_command_error)
                            else error.message?.let(UiMessage::Text)
                                ?: UiMessage.Resource(Res.string.apk_parsing_failed)
                        viewModelScope.launch {
                            effects.send(
                                "apk-information",
                                SnackbarMessage(message),
                                id
                            )
                        }
                    })
                }
            }

            is ApkInformationIntent.CopyText -> {
                val id = ++operationId
                viewModelScope.launch {
                    try {
                        clipboard.write(intent.value)
                        currentCoroutineContext().ensureActive()
                        effects.send(
                            "apk-information",
                            SnackbarMessage(UiMessage.Resource(Res.string.copied_to_clipboard)),
                            id
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        io.github.oshai.kotlinlogging.KotlinLogging.logger("ApkInformation")
                            .error(error) { "Clipboard write failed" }
                    }
                }
            }
        }
    }
}
