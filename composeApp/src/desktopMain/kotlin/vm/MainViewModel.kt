package vm

import Page
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.ide.common.signing.KeystoreHelper
import database.DataBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.ApkInformation
import model.ApkSignature
import model.JunkCodeEnum
import model.JunkCodeInfo
import model.KeyStoreEnum
import model.KeyStoreInfo
import model.SignatureEnum
import model.SignaturePolicy
import model.StoreSize
import model.StoreType
import model.Verifier
import model.VerifierResult
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.skia.Image
import utils.AndroidJunkGenerator
import utils.extractIcon
import utils.extractValue
import utils.extractVersion
import utils.formatFileSize
import utils.getDownloadDirectory
import utils.getVerifier
import utils.isMac
import utils.isWindows
import utils.resourcesDir
import utils.resourcesDirWithOs
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
class MainViewModel : CoroutineScope by CoroutineScope(Dispatchers.Default) {

    // 数据库
    private val dataBase = DataBase()

    // 暗色模式
    var darkMode by mutableStateOf(dataBase.getDarkMode())
        private set

    // 删除标识
    var flagDelete by mutableStateOf(dataBase.getFlagDelete())
        private set

    // 签名后缀
    var signerSuffix by mutableStateOf(dataBase.getSignerSuffix())
        private set

    // 默认输出路径
    var outputPath by mutableStateOf(dataBase.getOutputPath())
        private set

    // 文件对齐标识
    var isAlignFileSize by mutableStateOf(dataBase.getIsAlignFileSize())
        private set

    // 目标密钥类型
    var destStoreType by mutableStateOf(dataBase.getDestStoreType())
        private set

    // 目标密钥大小
    var destStoreSize by mutableStateOf(dataBase.getDestStoreSize())
        private set

    // 主页选中下标
    var uiPageIndex by mutableStateOf(Page.SIGNATURE_INFORMATION)
        private set

    fun updateUiState(page: Page) {
        uiPageIndex = page
    }

    // 签名信息UI状态
    var verifierState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    // APK签名信息
    var apkSignatureState by mutableStateOf(ApkSignature(outputPath = outputPath))
        private set

    // Apk签名UI状态
    var apkSignatureUIState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    // 签名生成信息
    var keyStoreInfoState by mutableStateOf(KeyStoreInfo(keyStorePath = outputPath))
        private set

    // 签名生成UI状态
    var keyStoreInfoUIState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    // Apk信息UI状态
    var apkInformationState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    // 垃圾代码生成信息
    var junkCodeInfoState by mutableStateOf(JunkCodeInfo(outputPath = outputPath))
        private set

    // 垃圾代码生成UI状态
    var junkCodeUIState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    /**
     * 修改ApkSignature
     * @param enum 需要更新的索引
     * @param value 需要更新的值
     */
    fun updateApkSignature(enum: SignatureEnum, value: Any?) {
        val apkSignature = ApkSignature(apkSignatureState)
        when (enum) {
            SignatureEnum.APK_PATH -> {
                apkSignature.apkPath = value as String
                if (apkSignature.outputPath.isBlank()) {
                    val file = File(value)
                    if (file.exists()) {
                        apkSignature.outputPath = file.parentFile.path
                    }
                }
            }

            SignatureEnum.OUTPUT_PATH -> apkSignature.outputPath = value as String
            SignatureEnum.KEY_STORE_POLICY -> apkSignature.keyStorePolicy = value as SignaturePolicy

            SignatureEnum.KEY_STORE_PATH -> {
                // 更换签名路径后清空签名信息
                if (apkSignature.keyStorePath != value) {
                    apkSignature.keyStorePassword = ""
                    apkSignature.keyStoreAlisaList = null
                    apkSignature.keyStoreAlisaIndex = 0
                    apkSignature.keyStoreAlisaPassword = ""
                }
                apkSignature.keyStorePath = value as String
            }

            SignatureEnum.KEY_STORE_PASSWORD -> apkSignature.keyStorePassword = value as String
            SignatureEnum.KEY_STORE_ALISA_LIST -> apkSignature.keyStoreAlisaList = value as? ArrayList<String>

            SignatureEnum.KEY_STORE_ALISA_INDEX -> apkSignature.keyStoreAlisaIndex = value as Int
            SignatureEnum.KEY_STORE_ALISA_PASSWORD -> apkSignature.keyStoreAlisaPassword = value as String
        }
        apkSignatureState = apkSignature
    }

    /**
     * 修改SignatureGenerate
     * @param enum 需要更新的索引
     * @param value 需要更新的值
     */
    fun updateSignatureGenerate(enum: KeyStoreEnum, value: Any) {
        val keyStoreInfo = KeyStoreInfo(keyStoreInfoState)
        when (enum) {
            KeyStoreEnum.KEY_STORE_PATH -> keyStoreInfo.keyStorePath = value as String
            KeyStoreEnum.KEY_STORE_NAME -> keyStoreInfo.keyStoreName = value as String
            KeyStoreEnum.KEY_STORE_PASSWORD -> keyStoreInfo.keyStorePassword = value as String
            KeyStoreEnum.KEY_STORE_CONFIRM_PASSWORD -> keyStoreInfo.keyStoreConfirmPassword = value as String

            KeyStoreEnum.KEY_STORE_ALISA -> keyStoreInfo.keyStoreAlisa = value as String
            KeyStoreEnum.KEY_STORE_ALISA_PASSWORD -> keyStoreInfo.keyStoreAlisaPassword = value as String

            KeyStoreEnum.KEY_STORE_ALISA_CONFIRM_PASSWORD -> keyStoreInfo.keyStoreAlisaConfirmPassword = value as String

            KeyStoreEnum.VALIDITY_PERIOD -> keyStoreInfo.validityPeriod = value as String
            KeyStoreEnum.AUTHOR_NAME -> keyStoreInfo.authorName = value as String
            KeyStoreEnum.ORGANIZATIONAL_UNIT -> keyStoreInfo.organizationalUnit = value as String

            KeyStoreEnum.ORGANIZATIONAL -> keyStoreInfo.organizational = value as String
            KeyStoreEnum.CITY -> keyStoreInfo.city = value as String
            KeyStoreEnum.PROVINCE -> keyStoreInfo.province = value as String
            KeyStoreEnum.COUNTRY_CODE -> keyStoreInfo.countryCode = value as String
        }
        keyStoreInfoState = keyStoreInfo
    }

    /**
     * 修改JunkCodeInfo
     * @param enum 需要更新的索引
     * @param value 需要更新的值
     */
    fun updateJunkCodeInfo(enum: JunkCodeEnum, value: Any) {
        val junkCodeInfo = JunkCodeInfo(junkCodeInfoState)
        when (enum) {
            JunkCodeEnum.OUTPUT_PATH -> junkCodeInfo.outputPath = value as String
            JunkCodeEnum.PACKAGE_NAME -> {
                junkCodeInfo.packageName = value as String
                junkCodeInfo.aarName = "junk_" + junkCodeInfo.packageName.replace(
                    ".", "_"
                ) + "_" + junkCodeInfo.suffix + "_TT2.0.0.aar"
            }

            JunkCodeEnum.SUFFIX -> {
                junkCodeInfo.suffix = value as String
                junkCodeInfo.aarName = "junk_" + junkCodeInfo.packageName.replace(
                    ".", "_"
                ) + "_" + junkCodeInfo.suffix + "_TT2.0.0.aar"
            }

            JunkCodeEnum.PACKAGE_COUNT -> junkCodeInfo.packageCount = value as String
            JunkCodeEnum.ACTIVITY_COUNT_PER_PACKAGE -> junkCodeInfo.activityCountPerPackage = value as String

            JunkCodeEnum.RES_PREFIX -> junkCodeInfo.resPrefix = value as String
        }
        junkCodeInfoState = junkCodeInfo
    }

    /**
     * APK签名
     */
    fun apkSigner() = launch(Dispatchers.IO) {
        try {
            apkSignatureUIState = UIState.Loading
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
            apkSignatureUIState = UIState.Success("签名成功")
        } catch (e: Exception) {
            e.printStackTrace()
            apkSignatureUIState = UIState.Error(e.message ?: "签名失败，请联系开发者排查问题")
        }
        delay(500)
        apkSignatureUIState = UIState.WAIT
    }

    /**
     * APK信息
     * @param input 输入APK路径
     */
    fun apkInformation(input: String) = launch(Dispatchers.IO) {
        var process: Process? = null
        var inputStream: InputStream? = null
        var bufferedReader: BufferedReader? = null
        try {
            val aapt = File(resourcesDirWithOs, if (isWindows) {
                "aapt2.exe"
            } else if (isMac) {
                "aapt2"
            } else {
                "aapt2"
            })
            if (!aapt.canExecute()) {
                aapt.setExecutable(true)
            }
            apkInformationState = UIState.Loading
            val builder = ProcessBuilder()
            process = builder.command(aapt.absolutePath, "dump", "badging", input).start()
            inputStream = process!!.inputStream

            launch(Dispatchers.IO) {
                process.errorStream.use { stream ->
                    BufferedReader(InputStreamReader(stream, "utf-8")).use { reader ->
                        reader.readLines().forEach {
                            println("errorStream =================> $it")
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
            apkInformationState = UIState.Success(apkInformation)
        } catch (e: Exception) {
            apkInformationState = UIState.Error(e.message ?: "APK解析失败")
            e.printStackTrace()
        } finally {
            process?.destroy()
            inputStream?.close()
            bufferedReader?.close()
        }
        if (apkInformationState is UIState.Error) {
            delay(1000)
            apkInformationState = UIState.WAIT
        }
    }

    /**
     * 生成签名
     */
    fun createSignature() = launch(Dispatchers.IO) {
        try {
            keyStoreInfoUIState = UIState.Loading
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
            keyStoreInfoUIState = if (result) {
                UIState.Success("签名制作完成")
            } else {
                UIState.Success("签名制作失败，请检查输入项是否合法。")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            keyStoreInfoUIState = UIState.Error(e.message ?: "签名制作失败，请检查输入项是否合法。")
        }
        delay(500)
        keyStoreInfoUIState = UIState.WAIT
    }

    /**
     * 签名信息
     * @param input 输入签名的路径
     * @param password 签名密码
     * @param alisa 签名别名
     */
    fun signerVerifier(input: String, password: String, alisa: String) = launch(Dispatchers.IO) {
        verifierState = UIState.Loading
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
                verifierState = UIState.Success(apkVerifierResult)
            } else {
                throw Exception("Key Certificate Type Is Not X509Certificate")
            }
        } catch (e: Exception) {
            verifierState = UIState.Error(e.message ?: "签名验证失败")
            e.printStackTrace()
        } finally {
            fileInputStream?.close()
        }
        if (verifierState is UIState.Error) {
            delay(1000)
            verifierState = UIState.WAIT
        }
    }

    /**
     * APK签名信息
     * @param input 输入APK的路径
     */
    fun apkVerifier(input: String) = launch(Dispatchers.IO) {
        verifierState = UIState.Loading
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
                verifierState = UIState.Success(apkVerifierResult)
            } else {
                if (error.isBlank()) {
                    error = "APK签名验证失败"
                }
                verifierState = UIState.Error(error)
            }
        } catch (e: Exception) {
            verifierState = UIState.Error(e.message ?: "APK签名验证失败")
            e.printStackTrace()
        }
        if (verifierState is UIState.Error) {
            delay(1000)
            verifierState = UIState.WAIT
        }
    }

    /**
     * 更改间隔符号
     */
    fun changeSeparatorSign() {
        if (verifierState is UIState.Success) {
            val result = (verifierState as? UIState.Success)?.result as? VerifierResult ?: return
            result.data.forEach {
                val contains = it.md5.contains(':')
                val oldChar = if (contains) ':' else ' '
                val newChar = if (contains) ' ' else ':'
                it.md5 = it.md5.replace(oldChar, newChar)
                it.sha1 = it.sha1.replace(oldChar, newChar)
                it.sha256 = it.sha256.replace(oldChar, newChar)
            }
            verifierState = UIState.WAIT
            verifierState = UIState.Success(result)
        }
    }

    /**
     * 生成垃圾代码 aar
     */
    fun generateJunkCode() = launch(Dispatchers.IO) {
        junkCodeUIState = UIState.Loading
        try {
            val dir = resourcesDir
            val output = junkCodeInfoState.outputPath
            val appPackageName = junkCodeInfoState.packageName + "." + junkCodeInfoState.suffix
            val packageCount = junkCodeInfoState.packageCount.toInt()
            val activityCountPerPackage = junkCodeInfoState.activityCountPerPackage.toInt()
            val resPrefix = junkCodeInfoState.resPrefix
            val androidJunkGenerator = AndroidJunkGenerator(
                dir, output, appPackageName, packageCount, activityCountPerPackage, resPrefix
            )
            val file = androidJunkGenerator.startGenerate()
            junkCodeUIState = UIState.Success("构建结束：成功，文件大小：${formatFileSize(file.length(), 2, true)}")
        } catch (e: Exception) {
            junkCodeUIState = UIState.Error(e.message ?: "构建失败")
            e.printStackTrace()
        }
        delay(1000)
        junkCodeUIState = UIState.WAIT
    }

    /**
     * 图标生成
     * @param path 图标路径
     */
    fun iconGeneration(path: String) {
        val densities = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
        val scales = listOf(1.0, 0.75, 0.5, 0.375, 0.25)
        val inputFile = File(path)
        val outputDir = File(outputPath, "app")

        // 读取原始图片
        val image = Image.makeFromEncoded(inputFile.readBytes())

//        for ((index, density) in densities.withIndex()) {
//            val scale = scales[index]
//            val scaledWidth = (image.width * scale).toInt()
//            val scaledHeight = (image.height * scale).toInt()
//            val outputFile = File(outputDir, "mipmap-${density}/${inputFile.name}")
//            outputFile.parentFile.mkdirs()
//            val options = Options()
//                .with("method", "fit")
//                .with("width", scaledWidth)
//                .with("height", scaledHeight)
//            Tinify.fromFile(path)
//                .resize(options)
//                .toFile(outputFile.path)
//            image.scale(scale)?.let { bufferedImage ->
//                ImageIO.write(bufferedImage, "png", outputFile)
//            }
//        }
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
        this.flagDelete = dataBase.getFlagDelete()
    }

    /**
     * 更新签名后缀
     * @param signerSuffix 后缀
     */
    fun updateSignerSuffix(signerSuffix: String) {
        dataBase.updateSignerSuffix(signerSuffix)
        this.signerSuffix = dataBase.getSignerSuffix()
    }

    /**
     * 更新暗色模式
     * @param darkMode 0：自动 1：浅色 2：暗色
     */
    fun updateDarkMode(darkMode: Long) {
        dataBase.updateDarkMode(darkMode)
        this.darkMode = dataBase.getDarkMode()
    }

    /**
     * 更新默认输出路径
     * @param outputPath 路径
     */
    fun updateOutputPath(outputPath: String) {
        dataBase.updateOutputPath(outputPath)
        this.outputPath = dataBase.getOutputPath()
        apkSignatureState.outputPath = this.outputPath
        keyStoreInfoState.keyStorePath = this.outputPath
        junkCodeInfoState.outputPath = this.outputPath
    }

    /**
     * 更新文件对齐标识
     * @param isAlignFileSize 是否开启文件对齐
     */
    fun updateIsAlignFileSize(isAlignFileSize: Boolean) {
        dataBase.updateIsAlignFileSize(isAlignFileSize)
        this.isAlignFileSize = dataBase.getIsAlignFileSize()
    }

    /**
     * 更新目标密钥类型
     * @param type JKS or PKCS12
     */
    fun updateDestStoreType(type: StoreType) {
        dataBase.updateDestStoreType(type.value)
        destStoreType = dataBase.getDestStoreType()
    }

    /**
     * 更新目标密钥大小
     * @param type 1024 or 2048
     */
    fun updateDestStoreSize(type: StoreSize) {
        dataBase.updateDestStoreSize(type.value.toLong())
        destStoreSize = dataBase.getDestStoreSize()
    }
}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
    data class Error(val msg: String) : UIState
}