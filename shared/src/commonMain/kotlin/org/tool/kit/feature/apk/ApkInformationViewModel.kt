package org.tool.kit.feature.apk

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import org.tool.kit.domain.apk.ApkArchiveEntryContent
import org.tool.kit.domain.apk.ApkCommandFailed
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.ApkRuleUpdateStatus
import org.tool.kit.domain.repository.LibRulesRepository
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.app.ClipboardWriter
import org.tool.kit.feature.app.DesktopActionHandler
import org.tool.kit.feature.app.SnackbarMessage
import org.tool.kit.feature.app.UiMessage
import org.tool.kit.platform.isChineseLocale
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_parsing_failed
import org.tool.kit.shared.generated.resources.copied_to_clipboard
import org.tool.kit.shared.generated.resources.exec_command_error
import org.tool.kit.shared.generated.resources.lib_rules_up_to_date
import org.tool.kit.shared.generated.resources.lib_rules_update_failed
import org.tool.kit.shared.generated.resources.lib_rules_update_success

/** 管理 APK 信息读取、规则详情按需加载与复制；更换文件后仅接受最新一次读取结果。 */
class ApkInformationViewModel(
    private val read: ReadApkInformationUseCase,
    private val decoder: ApkIconDecoder,
    private val clipboard: ClipboardWriter,
    private val effects: AppEffectSink,
    private val libRules: LibRulesRepository? = null,
    private val ruleIcons: LibRuleIconDecoder? = null,
    private val actions: DesktopActionHandler? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApkInformationUiState())
    val uiState = _uiState.asStateFlow()
    val busy = uiState.map { it.busy }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val request = LatestRequest(viewModelScope)
    private val detailJobs = mutableMapOf<String, Job>()
    private var manifestJob: Job? = null
    private var entryPreviewJob: Job? = null
    private var operationId = 0L

    init {
        addCloseable {
            request.cancel()
            detailJobs.values.forEach { it.cancel() }
            detailJobs.clear()
            manifestJob?.cancel()
            manifestJob = null
            entryPreviewJob?.cancel()
            entryPreviewJob = null
            _uiState.update { if (it.busy) it.copy(phase = ApkInformationPhase.Idle) else it }
        }
    }

    private data class LoadedApkOutcome(
        val result: ApkInformationResultUi,
        val ruleIcons: Map<String, ImageBitmap>,
        val rulesMetadata: ApkLibRulesMetadata?,
    )

    /** 在主线程处理页面事件，先更新本地状态，再触发相应的校验或业务操作。 */
    fun onIntent(intent: ApkInformationIntent) {
        when (intent) {
            is ApkInformationIntent.ReadApk -> {
                val id = ++operationId
                detailJobs.values.forEach { it.cancel() }
                detailJobs.clear()
                manifestJob?.cancel()
                manifestJob = null
                entryPreviewJob?.cancel()
                entryPreviewJob = null
                _uiState.update {
                    it.copy(
                        phase = ApkInformationPhase.Loading,
                        inputFile = intent.path,
                        result = null,
                        ruleIcons = emptyMap(),
                        ruleDetails = emptyMap(),
                        manifestPreview = ManifestPreviewState.Idle,
                        entryPreview = ArchiveEntryPreviewState.Idle,
                    )
                }
                request.launch(block = {
                    try {
                        val data = read(intent.path).getOrThrow()
                        val appIcon = data.icon?.let { decoder.decode(it) }
                        val iconIds = buildSet {
                            data.ruleAnalysis?.sdks?.mapNotNullTo(this) { it.iconId }
                            data.components?.mapNotNullTo(this) { it.rule?.iconId }
                            data.archive?.nativeLibraries?.mapNotNullTo(this) { it.rule?.iconId }
                        }
                        val decodedRuleIcons = if (ruleIcons == null) {
                            emptyMap()
                        } else {
                            buildMap {
                                for (iconId in iconIds) {
                                    currentCoroutineContext().ensureActive()
                                    val bitmap = try {
                                        ruleIcons.decode(iconId)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        null
                                    }
                                    if (bitmap != null) put(iconId, bitmap)
                                }
                            }
                        }
                        val metadata = if (libRules == null) {
                            null
                        } else {
                            try {
                                libRules.metadata()
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                null
                            }
                        }
                        Result.success(
                            LoadedApkOutcome(
                                result = ApkInformationResultUi(
                                    label = data.label,
                                    icon = appIcon,
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
                                    archive = data.archive,
                                    ruleAnalysis = data.ruleAnalysis,
                                ),
                                ruleIcons = decodedRuleIcons,
                                rulesMetadata = metadata,
                            )
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                }) { outcome ->
                    outcome.fold(onSuccess = { loaded ->
                        _uiState.update {
                            it.copy(
                                phase = ApkInformationPhase.Result,
                                result = loaded.result,
                                ruleIcons = loaded.ruleIcons,
                                ruleDetails = emptyMap(),
                                rulesMetadata = loaded.rulesMetadata ?: it.rulesMetadata,
                            )
                        }
                    }, onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                phase = ApkInformationPhase.Idle,
                                result = null,
                                ruleIcons = emptyMap(),
                                ruleDetails = emptyMap(),
                            )
                        }
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

            is ApkInformationIntent.LoadRuleDetail -> {
                val repo = libRules ?: return
                val key = ruleDetailKey(intent.uuid, intent.ruleId)
                val current = _uiState.value.ruleDetails[key]
                if (!intent.force && (current is RuleDetailState.Loading || current is RuleDetailState.Loaded)) {
                    return
                }
                val targetFile = _uiState.value.inputFile
                detailJobs.remove(key)?.cancel()
                _uiState.update { it.copy(ruleDetails = it.ruleDetails + (key to RuleDetailState.Loading)) }
                detailJobs[key] = viewModelScope.launch {
                    val outcome = repo.detail(intent.uuid, intent.ruleId, isChineseLocale())
                    if (_uiState.value.inputFile != targetFile) return@launch
                    val nextState = outcome.fold(
                        onSuccess = { detail ->
                            if (detail != null) RuleDetailState.Loaded(detail)
                            else RuleDetailState.Unavailable
                        },
                        onFailure = { RuleDetailState.Error },
                    )
                    _uiState.update { it.copy(ruleDetails = it.ruleDetails + (key to nextState)) }
                }
            }

            is ApkInformationIntent.OpenUrl -> {
                val handler = actions ?: return
                val normalized = intent.url.trim()
                if (!normalized.startsWith("https://", ignoreCase = true) &&
                    !normalized.startsWith("http://", ignoreCase = true)
                ) return
                try {
                    handler.browse(normalized)
                } catch (error: Exception) {
                    io.github.oshai.kotlinlogging.KotlinLogging.logger("ApkInformation")
                        .warn(error) { "Failed to open URL: $normalized" }
                }
            }

            ApkInformationIntent.UpdateLibRules -> {
                val repo = libRules ?: return
                if (_uiState.value.updatingRules) return
                val id = ++operationId
                _uiState.update { it.copy(updatingRules = true) }
                viewModelScope.launch {
                    val updateResult = try {
                        repo.checkAndUpdate()
                    } catch (cancelled: CancellationException) {
                        _uiState.update { it.copy(updatingRules = false) }
                        throw cancelled
                    }
                    _uiState.update {
                        it.copy(
                            updatingRules = false,
                            rulesMetadata = updateResult.metadata ?: it.rulesMetadata,
                        )
                    }
                    val message = when (updateResult.status) {
                        ApkRuleUpdateStatus.Updated -> Res.string.lib_rules_update_success
                        ApkRuleUpdateStatus.AlreadyUpToDate -> Res.string.lib_rules_up_to_date
                        ApkRuleUpdateStatus.Failed -> Res.string.lib_rules_update_failed
                    }
                    effects.send(
                        "apk-information",
                        SnackbarMessage(UiMessage.Resource(message)),
                        id
                    )
                    if (updateResult.status == ApkRuleUpdateStatus.Updated) {
                        val currentState = _uiState.value
                        val currentFile = currentState.inputFile
                        if (currentState.phase == ApkInformationPhase.Result && currentFile != null) {
                            onIntent(ApkInformationIntent.ReadApk(currentFile))
                        }
                    }
                }
            }

            ApkInformationIntent.LoadManifestPreview -> {
                val targetFile = _uiState.value.inputFile ?: return
                val current = _uiState.value.manifestPreview
                if (current is ManifestPreviewState.Loading || current is ManifestPreviewState.Loaded) return
                manifestJob?.cancel()
                _uiState.update { it.copy(manifestPreview = ManifestPreviewState.Loading) }
                manifestJob = viewModelScope.launch {
                    val xml = try {
                        read.readManifestXml(targetFile)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    if (_uiState.value.inputFile != targetFile) return@launch
                    val next = if (!xml.isNullOrBlank()) {
                        ManifestPreviewState.Loaded(xml)
                    } else {
                        ManifestPreviewState.Unavailable
                    }
                    _uiState.update { it.copy(manifestPreview = next) }
                }
            }

            is ApkInformationIntent.PreviewArchiveEntry -> {
                val targetFile = _uiState.value.inputFile ?: return
                entryPreviewJob?.cancel()
                _uiState.update {
                    it.copy(entryPreview = ArchiveEntryPreviewState.Loading(intent.entryPath, intent.kind))
                }
                entryPreviewJob = viewModelScope.launch {
                    val next = try {
                        when (val content = read.readArchiveEntryPreview(targetFile, intent.entryPath)) {
                            is ApkArchiveEntryContent.Xml ->
                                ArchiveEntryPreviewState.XmlLoaded(intent.entryPath, content.content)
                            is ApkArchiveEntryContent.Image ->
                                ArchiveEntryPreviewState.ImageLoaded(intent.entryPath, decoder.decode(content.source))
                            null -> ArchiveEntryPreviewState.Unavailable(intent.entryPath, intent.kind)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        ArchiveEntryPreviewState.Unavailable(intent.entryPath, intent.kind)
                    }
                    if (_uiState.value.inputFile != targetFile) return@launch
                    _uiState.update { it.copy(entryPreview = next) }
                }
            }

            ApkInformationIntent.CloseArchiveEntryPreview -> {
                entryPreviewJob?.cancel()
                entryPreviewJob = null
                _uiState.update { it.copy(entryPreview = ArchiveEntryPreviewState.Idle) }
            }
        }
    }
}

internal fun ruleDetailKey(uuid: String, ruleId: Int): String = "$uuid:$ruleId"
