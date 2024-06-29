package vm

import Page
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.ide.common.signing.KeystoreHelper
import constant.ConfigConstant
import database.DataBase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import model.ApkInformation
import model.ApkSignature
import model.IconFactoryInfo
import model.JunkCodeInfo
import model.KeyStoreInfo
import model.SignaturePolicy
import model.SnackbarVisualsData
import model.StoreSize
import model.StoreType
import model.Verifier
import model.VerifierResult
import org.apache.commons.codec.digest.DigestUtils
import uniffi.toolkit.ToolKitRustException
import uniffi.toolkit.mozjpeg
import uniffi.toolkit.quantize
import uniffi.toolkit.resize
import uniffi.toolkit.resizePng
import utils.AndroidJunkGenerator
import utils.browseFileDirectory
import utils.extractIcon
import utils.extractValue
import utils.extractVersion
import utils.formatFileSize
import utils.getDownloadDirectory
import utils.getVerifier
import utils.isJPEG
import utils.isJPG
import utils.isMac
import utils.isPng
import utils.isWindows
import utils.resourcesDir
import utils.resourcesDirWithOs
import utils.update
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/1/31 14:45
 * @Description : MainViewModel
 * @Version     : 1.0
 */
class MainViewModel : ViewModel() {

    // 数据库
    private val dataBase = DataBase()

    // 暗色模式
    private val _darkMode = mutableStateOf(dataBase.getDarkMode())
    val darkMode by _darkMode

    // 删除标识
    private val _flagDelete = mutableStateOf(dataBase.getFlagDelete())
    val flagDelete by _flagDelete

    // 签名后缀
    private val _signerSuffix = mutableStateOf(dataBase.getSignerSuffix())
    val signerSuffix by _signerSuffix

    // 默认输出路径
    private val _outputPath = mutableStateOf(dataBase.getOutputPath())
    val outputPath by _outputPath

    // 文件对齐标识
    private val _isAlignFileSize = mutableStateOf(dataBase.getIsAlignFileSize())
    val isAlignFileSize by _isAlignFileSize

    // 目标密钥类型
    private val _destStoreType = mutableStateOf(dataBase.getDestStoreType())
    val destStoreType by _destStoreType

    // 目标密钥大小
    private val _destStoreSize = mutableStateOf(dataBase.getDestStoreSize())
    val destStoreSize by _destStoreSize

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
    private val _apkSignatureState = mutableStateOf(ApkSignature(outputPath = outputPath))
    val apkSignatureState by _apkSignatureState

    // Apk签名UI状态
    private val _apkSignatureUIState = mutableStateOf<UIState>(UIState.WAIT)
    val apkSignatureUIState by _apkSignatureUIState

    // 签名生成信息
    private val _keyStoreInfoState = mutableStateOf(KeyStoreInfo(keyStorePath = outputPath))
    val keyStoreInfoState by _keyStoreInfoState

    // 签名生成UI状态
    private val _keyStoreInfoUIState = mutableStateOf<UIState>(UIState.WAIT)
    val keyStoreInfoUIState by _keyStoreInfoUIState

    // Apk信息UI状态
    private val _apkInformationState = mutableStateOf<UIState>(UIState.WAIT)
    val apkInformationState by _apkInformationState

    // 垃圾代码生成信息
    private val _junkCodeInfoState = mutableStateOf(JunkCodeInfo(outputPath = outputPath))
    val junkCodeInfoState by _junkCodeInfoState

    // 垃圾代码生成UI状态
    private val _junkCodeUIState = mutableStateOf<UIState>(UIState.WAIT)
    val junkCodeUIState by _junkCodeUIState

    // 图标工厂信息
    private val _iconFactoryInfoState = mutableStateOf(IconFactoryInfo(outputPath = outputPath))
    val iconFactoryInfoState by _iconFactoryInfoState

    // 图标工厂UI状态
    private val _iconFactoryUIState = mutableStateOf<UIState>(UIState.WAIT)
    val iconFactoryUIState by _iconFactoryUIState

    private val _snackbarVisuals = MutableStateFlow(SnackbarVisualsData())
    val snackbarVisuals = _snackbarVisuals.asStateFlow()

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
            val signerConfig = ApkSigner.SignerConfig.Builder("CERT", privateKey, listOf(certificate)).build()
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
                }
            )
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
        var process: Process? = null
        var inputStream: InputStream? = null
        var bufferedReader: BufferedReader? = null
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
            val builder = ProcessBuilder()
            process = builder.command(aapt.absolutePath, "dump", "badging", input).start()
            inputStream = process!!.inputStream

            var errors = ""

            viewModelScope.launch(Dispatchers.IO) {
                process.errorStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, "utf-8")).use { reader ->
                        reader.readLines().forEach {
                            errors += it
                        }
                    }
                }
            }

            bufferedReader = BufferedReader(InputStreamReader(inputStream!!, "utf-8"))
            var line: String?
            val apkInformation = ApkInformation()
            val apkFile = File(input)
            apkInformation.size = apkFile.length()
            apkInformation.md5 = DigestUtils.md5Hex(FileInputStream(apkFile))
            while (bufferedReader.readLine().also { line = it } != null) {
                line?.let {
                    if (it.startsWith("application:")) {
                        apkInformation.label = extractValue(it, "label")
                        apkInformation.icon = extractIcon(input, extractValue(it, "icon"))
                    } else if (it.startsWith("package:")) {
                        apkInformation.packageName = extractValue(it, "name")
                        apkInformation.versionCode = extractValue(it, "versionCode")
                        apkInformation.versionName = extractValue(it, "versionName")
                        apkInformation.compileSdkVersion = extractValue(it, "compileSdkVersion")
                    } else if (it.startsWith("targetSdkVersion:")) {
                        apkInformation.targetSdkVersion = extractVersion(it, "targetSdkVersion")
                    } else if (it.startsWith("sdkVersion:")) {
                        apkInformation.minSdkVersion = extractVersion(it, "sdkVersion")
                    } else if (it.startsWith("uses-permission:")) {
                        if (apkInformation.usesPermissionList == null) {
                            apkInformation.usesPermissionList = ArrayList()
                        }
                        apkInformation.usesPermissionList?.add(extractValue(it, "name"))
                    } else if (it.startsWith("native-code:")) {
                        apkInformation.nativeCode =
                            (it.split("native-code:").getOrNull(1) ?: "").trim().replace("'", "")
                    } else {

                    }
                }
            }
            if (apkInformation.isBlank()) {
                updateSnackbarVisuals(errors)
                _apkInformationState.update { UIState.WAIT }
            } else {
                _apkInformationState.update { UIState.Success(apkInformation) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            updateSnackbarVisuals(e.message ?: "APK解析失败")
            _apkInformationState.update { UIState.WAIT }
        } finally {
            process?.destroy()
            inputStream?.close()
            bufferedReader?.close()
        }
    }

    /**
     * 生成签名
     */
    fun createSignature() = viewModelScope.launch(Dispatchers.IO) {
        try {
            _keyStoreInfoUIState.update { UIState.Loading }
            val outputFile = File(keyStoreInfoState.keyStorePath, keyStoreInfoState.keyStoreName)
            val result = KeystoreHelper.createNewStore(
                destStoreType,
                outputFile,
                keyStoreInfoState.keyStorePassword,
                keyStoreInfoState.keyStoreAlisaPassword,
                keyStoreInfoState.keyStoreAlisa,
                "CN=${keyStoreInfoState.authorName},OU=${keyStoreInfoState.organizationalUnit},O=${keyStoreInfoState.organizational},L=${keyStoreInfoState.city},S=${keyStoreInfoState.province}, C=${keyStoreInfoState.countryCode}",
                keyStoreInfoState.validityPeriod.toInt(),
                destStoreSize.toInt()
            )
            if (result) {
                val snackbarVisualsData = SnackbarVisualsData(
                    message = "创建签名成功，点击跳转至签名文件",
                    actionLabel = "跳转",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                    action = {
                        browseFileDirectory(outputFile)
                    }
                )
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
                    formatFileSize(
                        file.length(),
                        2,
                        true
                    )
                }, 点击跳转至构建文件",
                actionLabel = "跳转",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(file)
                }
            )
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
        for ((index, density) in densities.withIndex()) {
            val size = sizes[index]
            val outputFile =
                File(outputDir, "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}${suffix}")
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
                        dstWidth = size,
                        dstHeight = size,
                        typIdx = 3.toUByte()
                    )
                    quantize(inputPath = outputSizeFile.absolutePath, outputPath = outputFile.absolutePath)
                } else if (path.isJPG || path.isJPEG) {
                    resize(
                        inputPath = inputFile.absolutePath,
                        outputPath = outputSizeFile.absolutePath,
                        dstWidth = size,
                        dstHeight = size
                    )
                    mozjpeg(inputPath = outputSizeFile.absolutePath, outputPath = outputFile.absolutePath)
                }
                val infoState = iconFactoryInfoState.copy()
                infoState.result?.apply {
                    add(outputFile)
                } ?: let {
                    infoState.result = mutableListOf(outputFile)
                }
                updateIconFactoryInfo(infoState)
            } catch (e: ToolKitRustException) {
                e.printStackTrace()
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
        _iconFactoryUIState.update { UIState.WAIT }
        if (isSuccess) {
            val snackbarVisualsData = SnackbarVisualsData(
                message = "图标生成完成。点击跳转至输出目录",
                actionLabel = "跳转",
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = {
                    browseFileDirectory(outputDir)
                }
            )
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
        } catch (e: Exception) {
            return false
        } finally {
            fileInputStream?.close()
        }
        return false
    }

    /**
     * 初始化内置
     * 如果为空，填充内置路径
     */
    fun initInternal() {
        if (outputPath.isBlank()) {
            val file = File(getDownloadDirectory())
            if (file.exists()) {
                updateOutputPath(file.absolutePath)
            }
        }
    }

    /**
     * 更新删除标识
     * @param flagDelete 是否删除
     */
    fun updateFlagDelete(flagDelete: Boolean) {
        dataBase.updateFlagDelete(flagDelete)
        _flagDelete.update { dataBase.getFlagDelete() }
    }

    /**
     * 更新签名后缀
     * @param signerSuffix 后缀
     */
    fun updateSignerSuffix(signerSuffix: String) {
        dataBase.updateSignerSuffix(signerSuffix)
        _signerSuffix.update { dataBase.getSignerSuffix() }
    }

    /**
     * 更新暗色模式
     * @param darkMode 0：自动 1：浅色 2：暗色
     */
    fun updateDarkMode(darkMode: Long) {
        updateSnackbarVisuals("")
        dataBase.updateDarkMode(darkMode)
        _darkMode.update { dataBase.getDarkMode() }
    }

    /**
     * 更新默认输出路径
     * @param outputPath 路径
     */
    fun updateOutputPath(outputPath: String) {
        dataBase.updateOutputPath(outputPath)
        _outputPath.update { dataBase.getOutputPath() }
        apkSignatureState.outputPath = this.outputPath
        keyStoreInfoState.keyStorePath = this.outputPath
        junkCodeInfoState.outputPath = this.outputPath
        iconFactoryInfoState.outputPath = this.outputPath
    }

    /**
     * 更新文件对齐标识
     * @param isAlignFileSize 是否开启文件对齐
     */
    fun updateIsAlignFileSize(isAlignFileSize: Boolean) {
        dataBase.updateIsAlignFileSize(isAlignFileSize)
        _isAlignFileSize.update { dataBase.getIsAlignFileSize() }
    }

    /**
     * 更新目标密钥类型
     * @param type JKS or PKCS12
     */
    fun updateDestStoreType(type: StoreType) {
        dataBase.updateDestStoreType(type.value)
        _destStoreType.update { dataBase.getDestStoreType() }
    }

    /**
     * 更新目标密钥大小
     * @param type 1024 or 2048
     */
    fun updateDestStoreSize(type: StoreSize) {
        dataBase.updateDestStoreSize(type.value.toLong())
        _destStoreSize.update { dataBase.getDestStoreSize() }
    }
}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
}