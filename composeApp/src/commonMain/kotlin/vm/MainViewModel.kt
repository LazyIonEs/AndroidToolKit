package vm

import Page
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.ide.common.signing.KeystoreHelper
import com.intellij.openapi.util.text.StringUtil
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.coroutines.FlowSettings
import constant.ConfigConstant
import database.PreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.ApkInformation
import model.ApkSignature
import model.DarkThemeConfig
import model.IconFactoryData
import model.IconFactoryInfo
import model.JunkCodeInfo
import model.KeyStoreInfo
import model.PendingDeletionFile
import model.Sequence
import model.SignaturePolicy
import model.SnackbarVisualsData
import model.UserData
import model.Verifier
import model.VerifierResult
import org.apache.commons.codec.digest.DigestUtils
import platform.RustException
import platform.mozJpeg
import platform.oxipng
import platform.quantize
import platform.resizeFir
import platform.resizePng
import utils.AndroidJunkGenerator
import utils.ExternalCommand
import utils.WhileUiSubscribed
import utils.browseFileDirectory
import utils.extractIcon
import utils.extractValue
import utils.extractVersion
import utils.formatFileSize
import utils.getFileLength
import utils.getVerifier
import utils.isJPEG
import utils.isJPG
import utils.isMac
import utils.isPng
import utils.isWindows
import utils.resourcesDir
import utils.resourcesDirWithOs
import utils.update
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/1/31 14:45
 * @Description : MainViewModel
 * @Version     : 1.0
 */
class MainViewModel @OptIn(ExperimentalSettingsApi::class) constructor(settings: FlowSettings) : ViewModel() {

    // 数据存储
    @OptIn(ExperimentalSettingsApi::class)
    private val preferences = PreferencesDataSource(settings)

    // 偏好设置
    val themeConfig = preferences.themeConfig.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = PreferencesDataSource.DEFAULT_THEME_CONFIG
    )

    // 偏好设置
    val userData = preferences.userData.stateIn(
        scope = viewModelScope, started = WhileUiSubscribed, initialValue = PreferencesDataSource.DEFAULT_USER_DATA
    )

    // 图标生成偏好设置
    val iconFactoryData = preferences.iconFactoryData.stateIn(
        scope = viewModelScope,
        started = Eagerly,
        initialValue = PreferencesDataSource.DEFAULT_ICON_FACTORY_DATA
    )

    // 偏好设置
    val junkCode = preferences.junkCode

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

    // 文件列表排序
    private var _currentFileSequence = mutableStateOf<Sequence>(Sequence.SIZE_LARGE_TO_SMALL)
    val currentFileSequence by _currentFileSequence

    // 通知
    private val _snackbarVisuals = MutableStateFlow(SnackbarVisualsData())
    val snackbarVisuals = _snackbarVisuals.asStateFlow()

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
            updateSnackbarVisuals("重启后生效")
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
     * @see model.SnackbarVisualsData
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
     * 修改ApkSignature
     * @param apkSignature ApkSignature
     * @see model.ApkSignature
     */
    fun updateApkSignature(apkSignature: ApkSignature) {
        _apkSignatureState.update { apkSignature }
    }

    /**
     * 修改SignatureGenerate
     * @param keyStoreInfo KeyStoreInfo
     * @see model.KeyStoreInfo
     */
    fun updateSignatureGenerate(keyStoreInfo: KeyStoreInfo) {
        _keyStoreInfoState.update { keyStoreInfo }
    }

    /**
     * 修改JunkCodeInfo
     * @param junkCodeInfo JunkCodeInfo
     * @see model.JunkCodeInfo
     */
    fun updateJunkCodeInfo(junkCodeInfo: JunkCodeInfo) {
        _junkCodeInfoState.update { junkCodeInfo }
    }

    /**
     * 修改IconFactoryInfo
     * @param iconFactoryInfo IconFactoryInfo
     * @see model.IconFactoryInfo
     */
    fun updateIconFactoryInfo(iconFactoryInfo: IconFactoryInfo) {
        _iconFactoryInfoState.update { iconFactoryInfo }
    }

    /**
     * APK签名
     */
    fun apkSigner() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val signerSuffix = userData.value.defaultSignerSuffix
            val flagDelete = userData.value.duplicateFileRemoval
            val isAlignFileSize = userData.value.alignFileSize
            _apkSignatureUIState.update { UIState.Loading }
            val inputApk = File(apkSignatureState.apkPath)
            val outputApk = File(
                apkSignatureState.outputPath, "${inputApk.nameWithoutExtension}${signerSuffix}.apk"
            )
            if (outputApk.exists()) {
                if (flagDelete) {
                    outputApk.delete()
                } else {
                    throw Exception("输出文件已存在：${outputApk.name}")
                }
            }
            val key = File(apkSignatureState.keyStorePath)
            val v1SigningEnabled =
                apkSignatureState.keyStorePolicy == SignaturePolicy.V1 || apkSignatureState.keyStorePolicy == SignaturePolicy.V2 || apkSignatureState.keyStorePolicy == SignaturePolicy.V3
            val v2SigningEnabled =
                apkSignatureState.keyStorePolicy == SignaturePolicy.V2 || apkSignatureState.keyStorePolicy == SignaturePolicy.V2Only || apkSignatureState.keyStorePolicy == SignaturePolicy.V3
            val v3SigningEnabled = apkSignatureState.keyStorePolicy == SignaturePolicy.V3
            val alisa = apkSignatureState.keyStoreAlisaList?.getOrNull(apkSignatureState.keyStoreAlisaIndex)
            val certificateInfo = KeystoreHelper.getCertificateInfo(
                "JKS", key, apkSignatureState.keyStorePassword, apkSignatureState.keyStoreAlisaPassword, alisa
            )
            val privateKey = certificateInfo.key
            val certificate = certificateInfo.certificate
            val keyConfig = KeyConfig.Jca(privateKey)
            val signerConfig = ApkSigner.SignerConfig.Builder("CERT", keyConfig, listOf(certificate)).build()
            val signerBuild = ApkSigner.Builder(listOf(signerConfig))
            val apkSigner = signerBuild.setInputApk(inputApk).setOutputApk(outputApk).setAlignFileSize(isAlignFileSize)
                .setV1SigningEnabled(v1SigningEnabled).setV2SigningEnabled(v2SigningEnabled)
                .setV3SigningEnabled(v3SigningEnabled).setAlignmentPreserved(!isAlignFileSize).build()
            apkSigner.sign()
            val snackbarVisualsData = SnackbarVisualsData(
                message = "APK签名成功，点击跳转至已签名文件",
                actionLabel = "跳转",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(outputApk)
                })
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "签名失败，请联系开发者排查问题")
        }
        _apkSignatureUIState.update { UIState.WAIT }
    }

    /**
     * APK信息
     * @param input 输入APK路径
     */
    fun apkInformation(input: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
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

            if (exitValue != 0) {
                // 执行命令出现错误
                throw InterruptedException("执行命令出现错误")
            }

            val converted = StringUtil.convertLineSeparators(stdoutStream.toString("UTF-8"))
            val lines = StringUtil.split(converted, "\n", true, true)

            val apkInformation = ApkInformation()
            val apkFile = File(input)
            apkInformation.size = apkFile.length()
            apkInformation.md5 = DigestUtils.md5Hex(FileInputStream(apkFile))

            lines.forEach { line ->
                if (line.startsWith("application-icon-640:")) {
                    val path = (line.split("application-icon-640:").getOrNull(1) ?: "").trim().replace("'", "")
                    apkInformation.icon = extractIcon(aapt, input, path)
                } else if (line.startsWith("application:")) {
                    apkInformation.label = extractValue(line, "label")
                    val iconPath = extractValue(line, "icon")
                    if (apkInformation.icon == null && !iconPath.endsWith(".xml")) {
                        apkInformation.icon = extractIcon(aapt, input, iconPath)
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

            if (apkInformation.isBlank()) {
                updateSnackbarVisuals("APK解析失败")
                _apkInformationState.update { UIState.WAIT }
            } else {
                _apkInformationState.update { UIState.Success(apkInformation) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "APK解析失败")
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
            if (result) {
                val snackbarVisualsData = SnackbarVisualsData(
                    message = "创建签名成功，点击跳转至签名文件",
                    actionLabel = "跳转",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                    action = {
                        browseFileDirectory(outputFile)
                    })
                updateSnackbarVisuals(snackbarVisualsData)
            } else {
                updateSnackbarVisuals("签名制作失败，请检查输入项是否合法。")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "签名制作失败，请检查输入项是否合法。")
        }
        _keyStoreInfoUIState.update { UIState.WAIT }
    }

    /**
     * 签名信息
     * @param input 输入签名的路径
     * @param password 签名密码
     * @param alisa 签名别名
     */
    fun signerVerifier(input: String, password: String, alisa: String) = viewModelScope.launch(Dispatchers.IO) {
        _verifierState.update { UIState.Loading }
        var fileInputStream: FileInputStream? = null
        val inputFile = File(input)
        try {
            val list = ArrayList<Verifier>()
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            fileInputStream = FileInputStream(inputFile)
            keyStore.load(fileInputStream, password.toCharArray())
            val cert = keyStore.getCertificate(alisa)
            if (cert.type == "X.509") {
                cert as X509Certificate
                list.add(cert.getVerifier(cert.version))
                val apkVerifierResult = VerifierResult(
                    isSuccess = true, isApk = false, path = input, name = inputFile.name, data = list
                )
                _verifierState.update { UIState.Success(apkVerifierResult) }
            } else {
                throw Exception("Key Certificate Type Is Not X509Certificate")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "签名验证失败")
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
        try {
            val result = verifier.verify()
            var error = ""
            val isSuccess = result.isVerified

            result.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }.forEach {
                error += it.toString() + "\n"
            }

            if (result.v1SchemeSigners.isNotEmpty()) {
                for (signer in result.v1SchemeSigners) {
                    val cert = signer.certificate ?: continue
                    if (signer.certificate.type == "X.509") {
                        list.add(cert.getVerifier(1))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }.forEach {
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
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }.forEach {
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
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }.forEach {
                        error += it.toString() + "\n"
                    }
                }
            }

            if (isSuccess || list.isNotEmpty()) {
                val apkVerifierResult = VerifierResult(isSuccess, true, path, name, list)
                _verifierState.update { UIState.Success(apkVerifierResult) }
            } else {
                if (error.isBlank()) {
                    error = "APK签名验证失败"
                }
                updateSnackbarVisuals(error)
                _verifierState.update { UIState.WAIT }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "APK签名验证失败")
            _verifierState.update { UIState.WAIT }
        }
    }

    /**
     * 生成垃圾代码 aar
     */
    fun generateJunkCode() = viewModelScope.launch(Dispatchers.IO) {
        _junkCodeUIState.update { UIState.Loading }
        try {
            val dir = resourcesDir
            val output = junkCodeInfoState.outputPath
            val appPackageName = junkCodeInfoState.packageName + "." + junkCodeInfoState.suffix
            val packageCount = junkCodeInfoState.packageCount.toInt()
            val activityCountPerPackage = junkCodeInfoState.activityCountPerPackage.toInt()
            val resPrefix = junkCodeInfoState.resPrefix
            val androidJunkGenerator =
                AndroidJunkGenerator(dir, output, appPackageName, packageCount, activityCountPerPackage, resPrefix)
            val file = androidJunkGenerator.startGenerate()
            val snackbarVisualsData = SnackbarVisualsData(
                message = "构建结束：成功，文件大小：${
                    file.length().formatFileSize()
                }, 点击跳转至构建文件",
                actionLabel = "跳转",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(file)
                })
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "构建失败")
        }
        _junkCodeUIState.update { UIState.WAIT }
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
        var isSuccess = true
        var error = ""
        val result = mutableListOf<File>()
        for ((index, density) in densities.withIndex()) {
            val size = sizes[index]
            val outputFile =
                File(outputDir, "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}${suffix}")
            val outputSizeFile = File(
                outputDir, "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}_resize${suffix}"
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
                result.add(outputFile)
            } catch (e: RustException) {
                isSuccess = false
                error = e.message ?: "图标制作失败"
                break
            } catch (e: Exception) {
                e.printStackTrace()
                isSuccess = false
                error = e.message ?: "图标制作失败"
                break
            }
            outputSizeFile.delete()
        }
        updateIconFactoryInfo(iconFactoryInfoState.copy(result = result))
        _iconFactoryUIState.update { UIState.WAIT }
        if (isSuccess) {
            val snackbarVisualsData = SnackbarVisualsData(
                message = "图标生成完成。点击跳转至输出目录",
                actionLabel = "跳转",
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
        } catch (_: Exception) {

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
            val alisa = apkSignatureState.keyStoreAlisaList?.getOrNull(apkSignatureState.keyStoreAlisaIndex)
            if (keyStore.containsAlias(alisa)) {
                val key = keyStore.getKey(alisa, apkSignatureState.keyStoreAlisaPassword.toCharArray())
                return key != null
            }
        } catch (_: Exception) {
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
                .filter { file -> file.isDirectory == true && file.nameWithoutExtension == "build" }
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
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.WAIT }
                if (_pendingDeletionFileList.isEmpty()) {
                    updateSnackbarVisuals("未扫描到缓存目录")
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
        _pendingDeletionFileList.find { file -> file.file == pendingDeletionFile.file }?.checked = check
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
            withContext(Dispatchers.Main) {
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
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.WAIT }
                val message = if (errorCount == 0) {
                    // 全部删除成功
                    "清理完成，已为您清理${clearLength.formatFileSize()}"
                } else {
                    "${errorCount}个文件删除异常"
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