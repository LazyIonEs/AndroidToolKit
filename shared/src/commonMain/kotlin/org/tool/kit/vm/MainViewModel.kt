package org.tool.kit.vm

import androidtoolkit.shared.generated.resources.Res
import androidtoolkit.shared.generated.resources.apk_is_signed_successfully
import androidtoolkit.shared.generated.resources.apk_parsing_failed
import androidtoolkit.shared.generated.resources.apk_signature_verification_failed
import androidtoolkit.shared.generated.resources.build_end
import androidtoolkit.shared.generated.resources.build_failure
import androidtoolkit.shared.generated.resources.check_update_error
import androidtoolkit.shared.generated.resources.cleanup_complete
import androidtoolkit.shared.generated.resources.create_signature_successfully
import androidtoolkit.shared.generated.resources.exec_command_error
import androidtoolkit.shared.generated.resources.file_deletion_exception
import androidtoolkit.shared.generated.resources.icon_creation_failed
import androidtoolkit.shared.generated.resources.icon_generation_completed
import androidtoolkit.shared.generated.resources.it_s_the_latest_version
import androidtoolkit.shared.generated.resources.jump
import androidtoolkit.shared.generated.resources.output_file_already_exists
import androidtoolkit.shared.generated.resources.scanning_anomalies
import androidtoolkit.shared.generated.resources.signature_creation_failed
import androidtoolkit.shared.generated.resources.signature_failed
import androidtoolkit.shared.generated.resources.signature_verification_failed
import androidtoolkit.shared.generated.resources.toolkit_extension_mode_is_enabled
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.res.xml.ResXmlUtils
import brut.directory.ExtFile
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.ide.common.signing.KeystoreHelper
import com.intellij.openapi.util.text.StringUtil
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.tool.kit.BuildConfig
import org.tool.kit.Page
import org.tool.kit.constant.ConfigConstant
import org.tool.kit.database.PreferencesDataSource
import org.tool.kit.model.ApkInformation
import org.tool.kit.model.ApkSignature
import org.tool.kit.model.ApkToolInfo
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.IconFactoryData
import org.tool.kit.model.IconFactoryInfo
import org.tool.kit.model.JunkCodeInfo
import org.tool.kit.model.KeyStoreInfo
import org.tool.kit.model.PendingDeletionFile
import org.tool.kit.model.Sequence
import org.tool.kit.model.SignaturePolicy
import org.tool.kit.model.SnackbarVisualsData
import org.tool.kit.model.Update
import org.tool.kit.model.UserData
import org.tool.kit.model.Verifier
import org.tool.kit.model.VerifierResult
import org.tool.kit.platform.RustException
import org.tool.kit.platform.mozJpeg
import org.tool.kit.platform.oxipng
import org.tool.kit.platform.quantize
import org.tool.kit.platform.resizeFir
import org.tool.kit.platform.resizePng
import org.tool.kit.utils.AndroidJunkGenerator
import org.tool.kit.utils.ExternalCommand
import org.tool.kit.utils.WhileUiSubscribed
import org.tool.kit.utils.browseFileDirectory
import org.tool.kit.utils.extractAndroidManifest
import org.tool.kit.utils.extractChannel
import org.tool.kit.utils.extractIcon
import org.tool.kit.utils.extractValue
import org.tool.kit.utils.extractVersion
import org.tool.kit.utils.filterByOS
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.getFileLength
import org.tool.kit.utils.getVerifier
import org.tool.kit.utils.isJPEG
import org.tool.kit.utils.isJPG
import org.tool.kit.utils.isMac
import org.tool.kit.utils.isNewVersion
import org.tool.kit.utils.isPng
import org.tool.kit.utils.isWindows
import org.tool.kit.utils.renameManifestPackage
import org.tool.kit.utils.renameValueAppName
import org.tool.kit.utils.resourcesDir
import org.tool.kit.utils.resourcesDirWithOs
import org.tool.kit.utils.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import kotlin.coroutines.resume

private val logger = KotlinLogging.logger("MainViewModel")

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/1/31 14:45
 * @Description : MainViewModel
 * @Version     : 1.0
 */
class MainViewModel @OptIn(ExperimentalSettingsApi::class) constructor(settings: FlowSettings) :
    ViewModel() {

    // 数据存储
    @OptIn(ExperimentalSettingsApi::class)
    private val preferences = PreferencesDataSource(settings)

    // 偏好设置
    val themeConfig = preferences.themeConfig.stateIn(
        scope = viewModelScope,
        started = WhileUiSubscribed,
        initialValue = PreferencesDataSource.Companion.DEFAULT_THEME_CONFIG
    )

    // 偏好设置
    val userData = preferences.userData.stateIn(
        scope = viewModelScope,
        started = Eagerly,
        initialValue = PreferencesDataSource.Companion.DEFAULT_USER_DATA
    )

    // 图标生成偏好设置
    val iconFactoryData = preferences.iconFactoryData.stateIn(
        scope = viewModelScope,
        started = Eagerly,
        initialValue = PreferencesDataSource.Companion.DEFAULT_ICON_FACTORY_DATA
    )

    // 偏好设置
    val isShowJunkCode = preferences.isShowJunkCode.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isHuaweiAlignFileSize = preferences.isHuaweiAlignFileSize.stateIn(
        scope = viewModelScope, started = Eagerly, initialValue = false
    )

    val isAlwaysShowLabel = preferences.isAlwaysShowLabel.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isShowSignatureGeneration = preferences.isShowSignatureGeneration.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isShowApktool = preferences.isShowApktool.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isShowIconFactory = preferences.isShowIconFactory.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isShowClearBuild = preferences.isShowClearBuild.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isEnableDeveloperMode = preferences.isEnableDeveloperMode.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = false
    )

    val isStartCheckUpdate = preferences.isStartCheckUpdate.stateIn(
        scope = viewModelScope, started = Eagerly, initialValue = false
    )

    // 主页选中下标
    private val _uiPageIndex = mutableStateOf(Page.SIGNATURE_INFORMATION)
    val uiPageIndex by _uiPageIndex

    fun updateUiState(page: Page) {
        _uiPageIndex.update { page }
    }

    // 签名信息UI状态
    private val _verifierState = mutableStateOf<UIState>(UIState.WAIT)
    val verifierState by _verifierState

    // APK签名信息
    private val _apkSignatureState = mutableStateOf(ApkSignature())
    val apkSignatureState by _apkSignatureState

    // Apk签名UI状态
    private val _apkSignatureUIState = mutableStateOf<UIState>(UIState.WAIT)
    val apkSignatureUIState by _apkSignatureUIState

    // 签名生成信息
    private val _keyStoreInfoState = mutableStateOf(KeyStoreInfo())
    val keyStoreInfoState by _keyStoreInfoState

    // 签名生成UI状态
    private val _keyStoreInfoUIState = mutableStateOf<UIState>(UIState.WAIT)
    val keyStoreInfoUIState by _keyStoreInfoUIState

    // Apk信息UI状态
    private val _apkInformationState = mutableStateOf<UIState>(UIState.WAIT)
    val apkInformationState by _apkInformationState

    // 垃圾代码生成信息
    private val _junkCodeInfoState = mutableStateOf(JunkCodeInfo())
    val junkCodeInfoState by _junkCodeInfoState

    // 垃圾代码生成UI状态
    private val _junkCodeUIState = mutableStateOf<UIState>(UIState.WAIT)
    val junkCodeUIState by _junkCodeUIState

    // 图标工厂信息
    private val _iconFactoryInfoState = mutableStateOf(IconFactoryInfo())
    val iconFactoryInfoState by _iconFactoryInfoState

    // 图标工厂UI状态
    private val _iconFactoryUIState = mutableStateOf<UIState>(UIState.WAIT)
    val iconFactoryUIState by _iconFactoryUIState

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

    // 空包生成信息
    private val _apkToolInfoState = mutableStateOf(ApkToolInfo())
    val apkToolInfoState by _apkToolInfoState

    // 空包生成UI状态
    private val _apkToolInfoUIState = mutableStateOf<UIState>(UIState.WAIT)
    val apkToolInfoUIState by _apkToolInfoUIState

    // 通知
    private val _snackbarVisuals = MutableStateFlow(SnackbarVisualsData())
    val snackbarVisuals = _snackbarVisuals.asStateFlow()

    // 检查更新
    private val _checkUpdateState = MutableStateFlow(false)
    val checkUpdateState = _checkUpdateState.asStateFlow()

    // 检查更新结果
    private val _checkUpdateResult = MutableStateFlow<Update?>(null)
    val checkUpdateResult = _checkUpdateResult.asStateFlow()

    /**
     * 更新主题
     */
    fun saveThemeConfig(themeConfig: DarkThemeConfig) {
        viewModelScope.launch {
            preferences.saveThemeConfig(themeConfig)
        }
    }

    /**
     * 更新用户偏好
     */
    fun saveUserData(userData: UserData) {
        viewModelScope.launch {
            preferences.saveUserData(userData)
        }
    }

    fun saveJunkCode(show: Boolean) {
        viewModelScope.launch {
            preferences.saveJunkCode(show)
        }
    }

    fun saveApkTool(show: Boolean) {
        viewModelScope.launch {
            preferences.saveApkTool(show)
        }
    }

    fun saveSignatureGeneration(show: Boolean) {
        viewModelScope.launch {
            preferences.saveSignatureGeneration(show)
        }
    }

    fun saveIsAlwaysShowLabel(show: Boolean) {
        viewModelScope.launch {
            preferences.saveIsAlwaysShowLabel(show)
        }
    }

    fun saveIsHuaweiAlignFileSize(show: Boolean) {
        viewModelScope.launch {
            preferences.saveIsHuaweiAlignFileSize(show)
        }
    }

    fun saveIconFactory(show: Boolean) {
        viewModelScope.launch {
            preferences.saveIconFactory(show)
        }
    }

    fun saveClearBuild(show: Boolean) {
        viewModelScope.launch {
            preferences.saveClearBuild(show)
        }
    }

    fun saveStartCheckUpdate(show: Boolean) {
        viewModelScope.launch {
            preferences.saveStartCheckUpdate(show)
        }
    }

    fun saveDeveloperMode(show: Boolean) {
        if (show == isEnableDeveloperMode.value) return
        viewModelScope.launch {
            preferences.saveDeveloperMode(show)
            if (show) {
                updateSnackbarVisuals(Res.string.toolkit_extension_mode_is_enabled)
            }
        }
    }

    /**
     * 更新图标生成偏好
     */
    fun saveIconFactoryData(iconFactoryData: IconFactoryData) {
        viewModelScope.launch {
            preferences.saveIconFactoryData(iconFactoryData)
        }
    }

    /**
     * 显示快捷信息栏
     * @param value SnackbarVisualsData
     * @see SnackbarVisualsData
     */
    private fun updateSnackbarVisuals(value: SnackbarVisualsData) {
        _snackbarVisuals.update { value }
    }

    /**
     * 显示快捷信息栏
     */
    fun updateSnackbarVisuals(value: String) {
        _snackbarVisuals.update { currentState ->
            currentState.copy(message = value).reset()
        }
    }

    /**
     * 显示快捷信息栏
     */
    fun updateSnackbarVisuals(resource: StringResource) {
        viewModelScope.launch(Dispatchers.Main) {
            val string = getString(resource)
            _snackbarVisuals.update { currentState ->
                currentState.copy(message = string).reset()
            }
        }
    }

    /**
     * 修改ApkSignature
     * @param apkSignature ApkSignature
     * @see ApkSignature
     */
    fun updateApkSignature(apkSignature: ApkSignature) {
        _apkSignatureState.update { apkSignature }
    }

    /**
     * 修改SignatureGenerate
     * @param keyStoreInfo KeyStoreInfo
     * @see KeyStoreInfo
     */
    fun updateSignatureGenerate(keyStoreInfo: KeyStoreInfo) {
        _keyStoreInfoState.update { keyStoreInfo }
    }

    /**
     * 修改JunkCodeInfo
     * @param junkCodeInfo JunkCodeInfo
     * @see JunkCodeInfo
     */
    fun updateJunkCodeInfo(junkCodeInfo: JunkCodeInfo) {
        _junkCodeInfoState.update { junkCodeInfo }
    }

    /**
     * 修改IconFactoryInfo
     * @param iconFactoryInfo IconFactoryInfo
     * @see IconFactoryInfo
     */
    fun updateIconFactoryInfo(iconFactoryInfo: IconFactoryInfo) {
        _iconFactoryInfoState.update { iconFactoryInfo }
    }

    /**
     * 修改ApkToolInfo
     * @param apkToolInfo ApkToolInfo
     * @see ApkToolInfo
     */
    fun updateApkToolInfo(apkToolInfo: ApkToolInfo) {
        _apkToolInfoState.update { apkToolInfo }
    }

    /**
     * APK签名
     */
    fun apkSigner() {
        logger.info { "apkSigner 进行APK签名, 签名信息: $apkSignatureState" }
        if (apkSignatureState.apkPath == ConfigConstant.APK.All.path) {
            apksSigner()
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                suspendApkSigner()
            }
        }
    }

    /**
     * 多APK签名
     */
    private fun apksSigner() = viewModelScope.launch(Dispatchers.IO) {
        logger.info { "apksSigner 多APK签名开始" }
        try {
            val apks =
                ConfigConstant.APK.entries.filter { it.title != ConfigConstant.APK.All.title }
                    .map { it.path }
            _apkSignatureUIState.update { UIState.Loading }
            val resultList = apks.map { path ->
                async { suspendApkSigner(path = path, showUiState = false) }
            }.awaitAll()
            val result = resultList.none { it == null || !it.exists() }
            logger.info { "apksSigner 多APK签名结束, 结果: $result" }
            if (result) {
                val outputApk = resultList.last()
                val snackbarVisualsData = SnackbarVisualsData(
                    message = getString(Res.string.apk_is_signed_successfully),
                    actionLabel = getString(Res.string.jump),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                    action = {
                        browseFileDirectory(outputApk)
                    })
                updateSnackbarVisuals(snackbarVisualsData)
            } else {
                updateSnackbarVisuals(getString(Res.string.signature_failed))
            }
        } catch (e: Exception) {
            logger.error(e) { "apksSigner 多APK签名异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.signature_failed))
        } finally {
            _apkSignatureUIState.update { UIState.WAIT }
        }
    }

    /**
     * APK签名
     */
    suspend fun suspendApkSigner(
        path: String = apkSignatureState.apkPath,
        showUiState: Boolean = true
    ) =
        suspendCancellableCoroutine { coroutine ->
            viewModelScope.launch(Dispatchers.IO) {
                var resultFile: File? = null
                try {
                    val signerSuffix = userData.value.defaultSignerSuffix
                    val flagDelete = userData.value.duplicateFileRemoval
                    val isAlignFileSize = if (isHuaweiAlignFileSize.value)
                        userData.value.alignFileSize && path != ConfigConstant.APK.Huawei.path
                    else
                        userData.value.alignFileSize
                    logger.info { "suspendApkSigner APK签名开始, APK文件路径: $path 开启文件对齐: $isAlignFileSize" }
                    if (showUiState) {
                        _apkSignatureUIState.update { UIState.Loading }
                    }
                    val inputApk = File(path)
                    val outputPrefix = apkSignatureState.outputPrefix
                    val outputApk = if (outputPrefix.isNotBlank()) {
                        File(
                            apkSignatureState.outputPath,
                            "${outputPrefix}-${inputApk.nameWithoutExtension}${signerSuffix}.apk"
                        )
                    } else {
                        File(
                            apkSignatureState.outputPath,
                            "${inputApk.nameWithoutExtension}${signerSuffix}.apk"
                        )
                    }

                    if (outputApk.exists()) {
                        if (flagDelete) {
                            outputApk.delete()
                        } else {
                            val message =
                                getString(Res.string.output_file_already_exists, outputApk.name)
                            throw Exception(message)
                        }
                    }
                    val key = File(apkSignatureState.keyStorePath)
                    val v1SigningEnabled =
                        apkSignatureState.keyStorePolicy == SignaturePolicy.V1 || apkSignatureState.keyStorePolicy == SignaturePolicy.V2 || apkSignatureState.keyStorePolicy == SignaturePolicy.V3
                    val v2SigningEnabled =
                        apkSignatureState.keyStorePolicy == SignaturePolicy.V2 || apkSignatureState.keyStorePolicy == SignaturePolicy.V2Only || apkSignatureState.keyStorePolicy == SignaturePolicy.V3
                    val v3SigningEnabled = apkSignatureState.keyStorePolicy == SignaturePolicy.V3
                    val alisa =
                        apkSignatureState.keyStoreAlisaList?.getOrNull(apkSignatureState.keyStoreAlisaIndex)
                    val certificateInfo = KeystoreHelper.getCertificateInfo(
                        "JKS",
                        key,
                        apkSignatureState.keyStorePassword,
                        apkSignatureState.keyStoreAlisaPassword,
                        alisa
                    )
                    val privateKey = certificateInfo.key
                    val certificate = certificateInfo.certificate
                    val keyConfig = KeyConfig.Jca(privateKey)
                    val signerConfig =
                        ApkSigner.SignerConfig.Builder("CERT", keyConfig, listOf(certificate))
                            .build()
                    val signerBuild = ApkSigner.Builder(listOf(signerConfig))
                    val apkSigner =
                        signerBuild.setInputApk(inputApk).setOutputApk(outputApk)
                            .setAlignFileSize(isAlignFileSize)
                            .setV1SigningEnabled(v1SigningEnabled)
                            .setV2SigningEnabled(v2SigningEnabled)
                            .setV3SigningEnabled(v3SigningEnabled)
                            .setAlignmentPreserved(!isAlignFileSize).build()
                    apkSigner.sign()
                    if (showUiState) {
                        val snackbarVisualsData = SnackbarVisualsData(
                            message = getString(Res.string.apk_is_signed_successfully),
                            actionLabel = getString(Res.string.jump),
                            withDismissAction = true,
                            duration = SnackbarDuration.Short,
                            action = {
                                browseFileDirectory(outputApk)
                            })
                        updateSnackbarVisuals(snackbarVisualsData)
                    }
                    if (outputApk.exists()) {
                        resultFile = outputApk
                    }
                    logger.info { "suspendApkSigner APK签名结束, APK输出文件路径: ${resultFile?.absolutePath}" }
                } catch (e: Exception) {
                    logger.error(e) { "suspendApkSigner APK签名异常, 异常信息: ${e.message}" }
                    if (showUiState) {
                        updateSnackbarVisuals(e.message ?: getString(Res.string.signature_failed))
                    }
                    resultFile = null
                } finally {
                    if (showUiState) {
                        _apkSignatureUIState.update { UIState.WAIT }
                    }
                    coroutine.resume(resultFile)
                }
            }
        }

    /**
     * APK信息
     * @param input 输入APK路径
     */
    fun apkInformation(input: String) = viewModelScope.launch(Dispatchers.IO) {
        logger.info { "apkInformation 获取APK信息开始, APK文件路径: $input" }
        try {
            val aapt = getAapt2File()
            _apkInformationState.update { UIState.Loading }

            val stdinStream = "".byteInputStream()
            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()

            val exitValue = withContext(Dispatchers.IO) {
                ExternalCommand(aapt.absolutePath).execute(
                    listOf("dump", "badging", input),
                    stdinStream, stdoutStream, stderrStream
                )
            }

            logger.info { "apkInformation 获取APK信息中, 执行命令结束, 退出码: $exitValue" }

            if (exitValue != 0) {
                // 执行命令出现错误
                throw InterruptedException(getString(Res.string.exec_command_error))
            }

            val converted = StringUtil.convertLineSeparators(stdoutStream.toString("UTF-8"))

            logger.info { "apkInformation 获取APK信息中, 获取结果: $converted" }

            val lines = StringUtil.split(converted, "\n", true, true)

            val apkInformation = ApkInformation()
            val apkFile = File(input)
            apkInformation.size = apkFile.length()
            apkInformation.md5 = DigestUtils.md5Hex(FileInputStream(apkFile))

            val androidManifest = extractAndroidManifest(aapt, input)

            lines.forEach { line ->
                if (line.startsWith("application-icon-640:")) {
                    val path = (line.split("application-icon-640:").getOrNull(1) ?: "").trim()
                        .replace("'", "")
                    apkInformation.icon = extractIcon(androidManifest, input, path)
                } else if (line.startsWith("application:")) {
                    apkInformation.label = extractValue(line, "label")
                    val iconPath = extractValue(line, "icon")
                    if (apkInformation.icon == null && !iconPath.endsWith(".xml")) {
                        apkInformation.icon = extractIcon(androidManifest, input, iconPath)
                    }
                } else if (line.startsWith("package:")) {
                    apkInformation.packageName = extractValue(line, "name")
                    apkInformation.versionCode = extractValue(line, "versionCode")
                    apkInformation.versionName = extractValue(line, "versionName")
                    apkInformation.compileSdkVersion = extractValue(line, "compileSdkVersion")
                } else if (line.startsWith("targetSdkVersion:")) {
                    apkInformation.targetSdkVersion = extractVersion(line, "targetSdkVersion")
                } else if (line.startsWith("sdkVersion:")) {
                    apkInformation.minSdkVersion = extractVersion(line, "sdkVersion")
                } else if (line.startsWith("uses-permission:")) {
                    if (apkInformation.usesPermissionList == null) {
                        apkInformation.usesPermissionList = ArrayList()
                    }
                    apkInformation.usesPermissionList?.add(extractValue(line, "name"))
                } else if (line.startsWith("native-code:")) {
                    apkInformation.nativeCode =
                        (line.split("native-code:").getOrNull(1) ?: "").trim().replace("'", "")
                }
            }

            val channel = extractChannel(androidManifest)
            apkInformation.channel = channel

            logger.info { "apkInformation 获取APK信息结束, APK信息: $apkInformation" }

            if (apkInformation.isBlank()) {
                updateSnackbarVisuals(Res.string.apk_parsing_failed)
                _apkInformationState.update { UIState.WAIT }
            } else {
                _apkInformationState.update { UIState.Success(apkInformation) }
            }
        } catch (e: Exception) {
            logger.error(e) { "apkInformation 获取APK信息异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.apk_parsing_failed))
            _apkInformationState.update { UIState.WAIT }
        }
    }

    /**
     * 生成签名
     */
    fun createSignature() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val destStoreType = userData.value.destStoreType
            val destStoreSize = userData.value.destStoreSize.size
            logger.info { "createSignature 生成签名开始, 签名信息: $keyStoreInfoState 签名类型: ${destStoreType.name} 密钥大小: $destStoreSize" }
            _keyStoreInfoUIState.update { UIState.Loading }
            val outputFile = File(keyStoreInfoState.keyStorePath, keyStoreInfoState.keyStoreName)
            val result = KeystoreHelper.createNewStore(
                destStoreType.name,
                outputFile,
                keyStoreInfoState.keyStorePassword,
                keyStoreInfoState.keyStoreAlisaPassword,
                keyStoreInfoState.keyStoreAlisa,
                "CN=${keyStoreInfoState.authorName},OU=${keyStoreInfoState.organizationalUnit},O=${keyStoreInfoState.organizational},L=${keyStoreInfoState.city},S=${keyStoreInfoState.province}, C=${keyStoreInfoState.countryCode}",
                keyStoreInfoState.validityPeriod.toInt(),
                destStoreSize
            )
            logger.info { "createSignature 生成签名结束, 结果: $result" }
            if (result) {
                val snackbarVisualsData = SnackbarVisualsData(
                    message = getString(Res.string.create_signature_successfully),
                    actionLabel = getString(Res.string.jump),
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                    action = {
                        browseFileDirectory(outputFile)
                    })
                updateSnackbarVisuals(snackbarVisualsData)
            } else {
                updateSnackbarVisuals(Res.string.signature_creation_failed)
            }
        } catch (e: Exception) {
            logger.error(e) { "createSignature 生成签名异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.signature_creation_failed))
        }
        _keyStoreInfoUIState.update { UIState.WAIT }
    }

    /**
     * 签名信息
     * @param input 输入签名的路径
     * @param password 签名密码
     * @param alisa 签名别名
     */
    fun signerVerifier(input: String, password: String, alisa: String) =
        viewModelScope.launch(Dispatchers.IO) {
            logger.info { "signerVerifier 获取签名信息开始, 签名文件路径: $input 签名密码: $password 签名别名: $alisa" }
            _verifierState.update { UIState.Loading }
            var fileInputStream: FileInputStream? = null
            val inputFile = File(input)
            try {
                val list = ArrayList<Verifier>()
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                fileInputStream = FileInputStream(inputFile)
                keyStore.load(fileInputStream, password.toCharArray())
                val cert = keyStore.getCertificate(alisa)
                logger.error { "signerVerifier 获取签名信息结束, 判断是否是X509Certificate类型: ${cert.type}" }
                if (cert.type == "X.509") {
                    cert as X509Certificate
                    list.add(cert.getVerifier(cert.version))
                    val apkVerifierResult = VerifierResult(
                        isSuccess = true,
                        isApk = false,
                        path = input,
                        name = inputFile.name,
                        data = list
                    )
                    logger.error { "signerVerifier 获取签名信息结束, 结果: $apkVerifierResult" }
                    _verifierState.update { UIState.Success(apkVerifierResult) }
                } else {
                    throw Exception("Key Certificate Type Is Not X509Certificate")
                }
            } catch (e: Exception) {
                logger.error { "signerVerifier 获取签名信息异常, 异常信息: ${e.message}" }
                updateSnackbarVisuals(
                    e.message ?: getString(Res.string.signature_verification_failed)
                )
                _verifierState.update { UIState.WAIT }
            } finally {
                fileInputStream?.close()
            }
        }

    /**
     * APK签名信息
     * @param input 输入APK的路径
     */
    fun apkVerifier(input: String) = viewModelScope.launch(Dispatchers.IO) {
        _verifierState.update { UIState.Loading }
        val list = ArrayList<Verifier>()
        val inputFile = File(input)
        val path = inputFile.path
        val name = inputFile.name
        val verifier: ApkVerifier = ApkVerifier.Builder(inputFile).build()
        logger.info { "apkVerifier 获取APK签名信息开始, APK文件路径: $input" }
        try {
            val result = verifier.verify()
            var error = ""
            val isSuccess = result.isVerified

            result.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }

            if (result.v1SchemeSigners.isNotEmpty()) {
                for (signer in result.v1SchemeSigners) {
                    val cert = signer.certificate ?: continue
                    if (signer.certificate.type == "X.509") {
                        list.add(cert.getVerifier(1))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
                            error += it.toString() + "\n"
                        }
                }
            }

            if (result.v2SchemeSigners.isNotEmpty()) {
                for (signer in result.v2SchemeSigners) {
                    val cert = signer.certificate ?: continue
                    if (signer.certificate.type == "X.509") {
                        list.add(cert.getVerifier(2))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
                            error += it.toString() + "\n"
                        }
                }
            }

            if (result.v3SchemeSigners.isNotEmpty()) {
                for (signer in result.v3SchemeSigners) {
                    val cert = signer.certificate ?: continue
                    if (signer.certificate.type == "X.509") {
                        list.add(cert.getVerifier(3))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
                            error += it.toString() + "\n"
                        }
                }
            }

            if (result.v4SchemeSigners.isNotEmpty()) {
                for (signer in result.v3SchemeSigners) {
                    val cert = signer.certificate ?: continue
                    if (signer.certificate.type == "X.509") {
                        list.add(cert.getVerifier(4))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
                            error += it.toString() + "\n"
                        }
                }
            }

            if (isSuccess || list.isNotEmpty()) {
                val apkVerifierResult = VerifierResult(isSuccess, true, path, name, list)
                _verifierState.update { UIState.Success(apkVerifierResult) }
                logger.info { "apkVerifier 获取APK签名信息结束, 结果: $apkVerifierResult" }
            } else {
                if (error.isBlank()) {
                    error = getString(Res.string.apk_signature_verification_failed)
                }
                updateSnackbarVisuals(error)
                _verifierState.update { UIState.WAIT }
                logger.error { "apkVerifier 获取APK签名信息异常, 异常信息: $error" }
            }
        } catch (e: Exception) {
            logger.error(e) { "apkVerifier 获取APK签名信息异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(
                e.message ?: getString(Res.string.apk_signature_verification_failed)
            )
            _verifierState.update { UIState.WAIT }
        }
    }

    /**
     * 生成垃圾代码 aar
     */
    fun generateJunkCode() = viewModelScope.launch(Dispatchers.IO) {
        _junkCodeUIState.update { UIState.Loading }
        logger.info { "generateJunkCode 生成垃圾代码开始, 垃圾代码生成信息: $junkCodeInfoState" }
        try {
            val dir = resourcesDir
            val output = junkCodeInfoState.outputPath
            val appPackageName = junkCodeInfoState.packageName + "." + junkCodeInfoState.suffix
            val packageCount = junkCodeInfoState.packageCount.toInt()
            val activityCountPerPackage = junkCodeInfoState.activityCountPerPackage.toInt()
            val resPrefix = junkCodeInfoState.resPrefix
            val androidJunkGenerator =
                AndroidJunkGenerator(
                    dir,
                    output,
                    appPackageName,
                    packageCount,
                    activityCountPerPackage,
                    resPrefix
                )
            val file = androidJunkGenerator.startGenerate()
            logger.info { "generateJunkCode 生成垃圾代码结束, 输出路径: ${file.absolutePath}" }
            val snackbarVisualsData = SnackbarVisualsData(
                message = getString(Res.string.build_end, file.length().formatFileSize()),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(file)
                })
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            logger.error(e) { "generateJunkCode 生成垃圾代码异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.build_failure))
        } finally {
            _junkCodeUIState.update { UIState.WAIT }
        }
    }

    /**
     * 生成自定义空包
     */
    fun generateApktool() = viewModelScope.launch(Dispatchers.IO) {
        _apkToolInfoUIState.update { UIState.Loading }
        val outApktoolCacheDir = File(resourcesDir, "apktool")
        try {
            logger.info { "generateApktool 生成空包开始, 空包信息: $apkToolInfoState" }
            val outApktoolFile = File(apkToolInfoState.outputPath, ConfigConstant.APKTOOL_FILE.name)
            val apkIcon = apkToolInfoState.icon
            val packageName = apkToolInfoState.packageName
            val targetSdkVersion = apkToolInfoState.targetSdkVersion
            val minSdkVersion = apkToolInfoState.minSdkVersion
            val versionCode = apkToolInfoState.versionCode
            val versionName = apkToolInfoState.versionName
            val appName = apkToolInfoState.appName
            val apkFile = ExtFile(ConfigConstant.APKTOOL_FILE)
            val config = Config()
            config.isAnalysisMode = true
            config.isForced = true
            config.isDebugMode = true
            logger.info { "generateApktool 开始解包" }
            // 开始解包
            val apkDecoder = ApkDecoder(apkFile, config)
            val apkInfo = apkDecoder.decode(outApktoolCacheDir)
            // 解包完成
            logger.info { "generateApktool 解包完成, ApkInfo: $apkInfo" }
            val apktoolYmlFile = File(outApktoolCacheDir, "apktool.yml")
            val androidManifestXmlFile = File(outApktoolCacheDir, "AndroidManifest.xml")
            // 删除Manifest中versionCode和versionName
            ResXmlUtils.removeManifestVersions(androidManifestXmlFile)
            logger.info { "generateApktool 删除Manifest中versionCode和versionName" }
            // 替换Manifest中minSdkVersion和targetSdkVersion
            renameManifestPackage(androidManifestXmlFile, minSdkVersion, targetSdkVersion)
            logger.info { "generateApktool 替换Manifest中minSdkVersion和targetSdkVersion" }
            // 替换strings中app_name的值
            val stringsFile = File(outApktoolCacheDir, "res/values/strings.xml")
            renameValueAppName(stringsFile, appName)
            logger.info { "generateApktool 替换strings中app_name的值" }
            // 替换icon
            if (apkIcon.isNotBlank()) {
                val apkIconFile = File(apkIcon)
                val suffix = apkIconFile.extension
                val densities = ConfigConstant.ICON_FILE_LIST
                for (density in densities.withIndex()) {
                    val targetFolderFile = File(outApktoolCacheDir, "res/mipmap-${density.value}")
                    targetFolderFile.deleteRecursively()
                    val targetFile = File(targetFolderFile, "ic_launcher.${suffix}")
                    apkIconFile.copyTo(targetFile, overwrite = true)
                }
                logger.info { "generateApktool 替换icon" }
            }
            // 替换部分信息到apktool.yml
            apkInfo.versionInfo.versionCode = versionCode
            apkInfo.versionInfo.versionName = versionName
            apkInfo.sdkInfo.minSdkVersion = minSdkVersion
            apkInfo.sdkInfo.targetSdkVersion = targetSdkVersion
            apkInfo.packageInfo.renameManifestPackage = packageName
            apkInfo.save(apktoolYmlFile)
            logger.info { "generateApktool 替换部分信息到apktool.yml" }
            // 开始打包
            logger.info { "generateApktool 开始打包" }
            val outApktoolCacheDirExt = ExtFile(outApktoolCacheDir)
            val apkBuilder = ApkBuilder(outApktoolCacheDirExt, config)
            apkBuilder.build(outApktoolFile)
            logger.info { "generateApktool 打包完成" }
            val snackbarVisualsData = SnackbarVisualsData(
                message = getString(Res.string.build_end, outApktoolFile.length().formatFileSize()),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(outApktoolFile)
                })
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            logger.error(e) { "generateApktool 生成空包异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.build_failure))
        } finally {
            outApktoolCacheDir.deleteRecursively()
            _apkToolInfoUIState.update { UIState.WAIT }
        }
    }

    /**
     * 图标生成
     * @param path 图标路径
     */
    fun iconGeneration(path: String) = viewModelScope.launch(Dispatchers.IO) {
        _iconFactoryUIState.update { UIState.Loading }
        val iconFactory = iconFactoryData.value
        val densities = ConfigConstant.ICON_FILE_LIST
        val sizes = ConfigConstant.ICON_SIZE_LIST
        val inputFile = File(path)
        val outputDir = File(iconFactoryInfoState.outputPath, iconFactoryInfoState.fileDir)

        updateIconFactoryInfo(iconFactoryInfoState.copy(result = null))

        val suffix = if (path.isPng) {
            ".png"
        } else if (path.isJPG) {
            ".jpg"
        } else if (path.isJPEG) {
            ".jpeg"
        } else {
            return@launch
        }

        logger.info { "iconGeneration 图标生成开始, 图标文件路径: $path" }

        var isSuccess = true
        var error = ""
        val result = mutableListOf<File>()
        for ((index, density) in densities.withIndex()) {
            val size = sizes[index]
            val outputFile =
                File(
                    outputDir,
                    "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}${suffix}"
                )
            val outputSizeFile = File(
                outputDir,
                "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}_resize${suffix}"
            )
            outputFile.parentFile.mkdirs()
            outputFile.delete()
            outputSizeFile.delete()
            try {
                if (path.isPng) {
                    resizePng(
                        inputPath = inputFile.absolutePath,
                        outputPath = outputSizeFile.absolutePath,
                        width = size,
                        height = size,
                        typIdx = iconFactory.pngTypIdx.typIdx.toUByte()
                    )
                    if (iconFactory.lossless) {
                        oxipng(
                            inputPath = outputSizeFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            preset = iconFactory.preset
                        )
                    } else {
                        quantize(
                            inputPath = outputSizeFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            minimum = iconFactory.minimum,
                            target = iconFactory.target,
                            speed = iconFactory.speed,
                            preset = iconFactory.preset
                        )
                    }
                } else if (path.isJPG || path.isJPEG) {
                    resizeFir(
                        inputPath = inputFile.absolutePath,
                        outputPath = outputSizeFile.absolutePath,
                        width = size,
                        height = size,
                        typIdx = iconFactory.jpegTypIdx.typIdx.toUByte()
                    )
                    mozJpeg(
                        inputPath = outputSizeFile.absolutePath,
                        outputPath = outputFile.absolutePath,
                        quality = if (iconFactory.lossless) 100f else iconFactory.quality
                    )
                }
                logger.info { "iconGeneration 图标生成完成, 任务索引: $index, 图标大小: $size, 输出文件路径: ${outputFile.absolutePath}" }
                result.add(outputFile)
            } catch (e: RustException) {
                logger.error(e) { "iconGeneration 图标生成异常, 异常信息: ${e.message}" }
                isSuccess = false
                error = e.message ?: getString(Res.string.icon_creation_failed)
                break
            } catch (e: Exception) {
                logger.error(e) { "iconGeneration 图标生成异常, 异常信息: ${e.message}" }
                isSuccess = false
                error = e.message ?: getString(Res.string.icon_creation_failed)
                break
            }
            outputSizeFile.delete()
        }
        updateIconFactoryInfo(iconFactoryInfoState.copy(result = result))
        _iconFactoryUIState.update { UIState.WAIT }
        if (isSuccess) {
            val snackbarVisualsData = SnackbarVisualsData(
                message = getString(Res.string.icon_generation_completed),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(outputDir)
                })
            updateSnackbarVisuals(snackbarVisualsData)
        } else {
            updateSnackbarVisuals(error)
        }
    }

    /**
     * 验证签名
     * @param path 签名路径
     * @param password 签名密码
     */
    fun verifyAlisa(path: String, password: String): ArrayList<String>? {
        var fileInputStream: FileInputStream? = null
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            fileInputStream = FileInputStream(path)
            keyStore.load(fileInputStream, password.toCharArray())
            val aliases = keyStore.aliases()
            val list = ArrayList<String>()
            while (aliases.hasMoreElements()) {
                list.add(aliases.nextElement())
            }
            return list
        } catch (e: Exception) {
            logger.error(e) { "verifyAlisa 验证签名异常, 异常信息: ${e.message}" }
        } finally {
            fileInputStream?.close()
        }
        return null
    }

    /**
     * 验证别名密码
     */
    fun verifyAlisaPassword(): Boolean {
        var fileInputStream: FileInputStream? = null
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            fileInputStream = FileInputStream(apkSignatureState.keyStorePath)
            keyStore.load(fileInputStream, apkSignatureState.keyStorePassword.toCharArray())
            val alisa =
                apkSignatureState.keyStoreAlisaList?.getOrNull(apkSignatureState.keyStoreAlisaIndex)
            if (keyStore.containsAlias(alisa)) {
                val key =
                    keyStore.getKey(alisa, apkSignatureState.keyStoreAlisaPassword.toCharArray())
                return key != null
            }
        } catch (e: Exception) {
            logger.error(e) { "verifyAlisaPassword 验证别名密码异常, 异常信息: ${e.message}" }
            return false
        } finally {
            fileInputStream?.close()
        }
        return false
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

    /**
     * 获取aapt2文件路径
     */
    private fun getAapt2File(): File {
        val aapt = File(
            resourcesDirWithOs, if (isWindows) {
                "aapt2.exe"
            } else if (isMac) {
                "aapt2"
            } else {
                "aapt2"
            }
        )
        if (!aapt.canExecute()) {
            aapt.setExecutable(true)
        }
        logger.info { "getAapt2File 获取aapt2文件路径: ${aapt.absolutePath}, aapt2可执行: ${aapt.canExecute()}" }
        return aapt
    }

    /**
     * 检查更新
     */
    fun checkUpdate(showMessage: Boolean = true) {
        viewModelScope.launch {
            val start = System.currentTimeMillis()
            logger.info { "checkUpdate 检查更新开始" }
            _checkUpdateState.update { true }
            _checkUpdateResult.update { null }
            val result = org.tool.kit.utils.checkUpdate()
            _checkUpdateState.update { false }
            logger.info { "checkUpdate 检查更新结束, 耗时: ${System.currentTimeMillis() - start}ms, 检查更新结果: $result" }
            if (result.isSuccess) {
                val githubRestLatestResult = result.data!!
                val isHaveNewVersion =
                    githubRestLatestResult.tagName.isNewVersion(BuildConfig.APP_VERSION)
                logger.info { "checkUpdate 检查更新结果对比: 现版本: ${BuildConfig.APP_VERSION} 新版本: ${githubRestLatestResult.tagName}" }
                if (isHaveNewVersion) {
                    val list = githubRestLatestResult.assets.filterByOS()
                    if (list?.isNotEmpty() == true) {
                        logger.info { "checkUpdate 展示更新弹窗" }
                        val version = githubRestLatestResult.tagName
                        val htmlUrl = githubRestLatestResult.htmlUrl
                        val createdAt = githubRestLatestResult.createdAt
                        val body = githubRestLatestResult.body
                        val update = Update(version, htmlUrl, createdAt, body, list)
                        _checkUpdateResult.update { update }
                    } else {
                        if (showMessage) {
                            updateSnackbarVisuals(Res.string.it_s_the_latest_version)
                        }
                    }
                } else {
                    if (showMessage) {
                        updateSnackbarVisuals(Res.string.it_s_the_latest_version)
                    }
                }
            } else {
                if (showMessage) {
                    updateSnackbarVisuals(result.msg ?: Res.string.check_update_error)
                }
            }
        }
    }

    /**
     * 取消更新
     */
    fun cancelUpdate() {
        viewModelScope.launch {
            _checkUpdateResult.update { null }
        }
    }
}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
}