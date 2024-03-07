package vm

import Page
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.ide.common.signing.KeystoreHelper
import database.DataBase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import model.ApkInformation
import model.ApkSignature
import model.SignatureEnum
import model.SignaturePolicy
import model.VerifierResult
import org.apache.commons.codec.digest.DigestUtils
import org.jetbrains.skia.Image
import utils.isMacos
import utils.isWindows
import utils.resourcesDir
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.zip.ZipFile

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

    // aapt路径
    var aapt by mutableStateOf(dataBase.getAaptPath())
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

    // keytool路径
    var keytool by mutableStateOf(dataBase.getKeytoolPath())
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

    // APK签名UI信息
    var apkSignatureState by mutableStateOf(ApkSignature(outPutPath = outputPath))
        private set

    // Apk签名UI状态
    var apkSignatureUIState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    // Apk信息UI状态
    var apkInformationState by mutableStateOf<UIState>(UIState.WAIT)
        private set

    /**
     * 修改ApkSignature
     * @param enum 需要更新的索引
     * @param value 需要更新的值
     */
    fun updateApkSignature(enum: SignatureEnum, value: Any) {
        val apkSignature = ApkSignature(apkSignatureState)
        when (enum) {
            SignatureEnum.APK_PATH -> {
                apkSignature.apkPath = value as String
                if (apkSignature.outPutPath.isBlank()) {
                    val file = File(value)
                    if (file.exists()) {
                        apkSignature.outPutPath = file.parentFile.path
                    }
                }
            }

            SignatureEnum.OUT_PUT_PATH -> apkSignature.outPutPath = value as String
            SignatureEnum.SIGNATURE_POLICY -> apkSignature.signaturePolicy = value as SignaturePolicy
            SignatureEnum.SIGNATURE_PATH -> {
                // 更换签名路径后清空签名信息
                if (apkSignature.signaturePath != value) {
                    apkSignature.signaturePassword = ""
                    apkSignature.signatureAlisa = ""
                    apkSignature.signatureAlisaPassword = ""
                }
                apkSignature.signaturePath = value as String
            }

            SignatureEnum.SIGNATURE_PASSWORD -> apkSignature.signaturePassword = value as String
            SignatureEnum.SIGNATURE_ALISA -> apkSignature.signatureAlisa = value as String
            SignatureEnum.SIGNATURE_ALISA_PASSWORD -> apkSignature.signatureAlisaPassword = value as String
        }
        apkSignatureState = apkSignature
    }

    /**
     * APK签名
     */
    fun apkSigner() = launch(Dispatchers.IO) {
        try {
            apkSignatureUIState = UIState.Loading
            val inputApk = File(apkSignatureState.apkPath)
            val outputApk = File(apkSignatureState.outPutPath, "${inputApk.nameWithoutExtension}${signerSuffix}.apk")
            if (outputApk.exists()) {
                if (flagDelete) {
                    outputApk.delete()
                } else {
                    throw Exception("输出文件已存在：${outputApk.name}")
                }
            }
            val key = File(apkSignatureState.signaturePath)
            val v1SigningEnabled = apkSignatureState.signaturePolicy == SignaturePolicy.V1 || apkSignatureState.signaturePolicy == SignaturePolicy.V2 || apkSignatureState.signaturePolicy == SignaturePolicy.V3
            val v2SigningEnabled = apkSignatureState.signaturePolicy == SignaturePolicy.V2 || apkSignatureState.signaturePolicy == SignaturePolicy.V2Only || apkSignatureState.signaturePolicy == SignaturePolicy.V3
            val v3SigningEnabled = apkSignatureState.signaturePolicy == SignaturePolicy.V3
            val certificateInfo = KeystoreHelper.getCertificateInfo(
                "JKS",
                key,
                apkSignatureState.signaturePassword,
                apkSignatureState.signatureAlisaPassword,
                apkSignatureState.signatureAlisa
            )
            val privateKey = certificateInfo.key
            val certificate = certificateInfo.certificate
            val signerConfig = ApkSigner.SignerConfig.Builder("CERT", privateKey, listOf(certificate)).build()
            val signerBuild = ApkSigner.Builder(listOf(signerConfig))
            val apkSigner = signerBuild
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setAlignFileSize(isAlignFileSize)
                .setV1SigningEnabled(v1SigningEnabled)
                .setV2SigningEnabled(v2SigningEnabled)
                .setV3SigningEnabled(v3SigningEnabled)
                .build()
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
        runBlocking {
            var process: Process? = null
            var inputStream: InputStream? = null
            var bufferedReader: BufferedReader? = null
            CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e ->
                apkInformationState = UIState.Error(e.message ?: "APK解析失败")
                e.printStackTrace()
            }).async {
                supervisorScope {
                    launch {
                        apkInformationState = UIState.Loading
                        if (aapt.isBlank()) {
                            throw Exception("请先在设置页设置aapt路径")
                        }
                        val builder = ProcessBuilder()
                        process = builder.command(aapt, "dump", "badging", input).start()
                        inputStream = process!!.inputStream
                        bufferedReader = BufferedReader(InputStreamReader(inputStream!!, "utf-8"))
                        var line: String?
                        val apkInformation = ApkInformation()
                        val apkFile = File(input)
                        apkInformation.size = apkFile.length()
                        apkInformation.md5 = DigestUtils.md5Hex(FileInputStream(apkFile))
                        while (bufferedReader!!.readLine().also { line = it } != null) {
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
                                    apkInformation.nativeCode = (it.split("native-code:").getOrNull(1) ?: "").trim().replace("'", "")
                                } else {

                                }
                            }
                        }
                        apkInformationState = UIState.Success(apkInformation)
                    }
                }
                kotlin.runCatching {
                    process?.destroy()
                    inputStream?.close()
                    bufferedReader?.close()
                }
                launch {
                    if (apkInformationState is UIState.Error) {
                        delay(1000)
                        apkInformationState = UIState.WAIT
                    }
                }
            }.await()
        }
    }

    /**
     * 生成签名
     */
    fun generateSignature() {
        // TODO: 生成签名
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
            val list = ArrayList<model.Verifier>()
            val keyStore = KeyStore.getInstance("PKCS12")
            fileInputStream = FileInputStream(inputFile)
            keyStore.load(fileInputStream, password.toCharArray())
            val cert = keyStore.getCertificate(alisa)
            if (cert.type == "X.509") {
                cert as X509Certificate
                val subject = cert.subjectX500Principal.name
                val validFrom = cert.notBefore.toString()
                val validUntil = cert.notAfter.toString()
                val publicKeyType = (cert.publicKey as? RSAPublicKey)?.algorithm ?: ""
                val modulus = (cert.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
                val signatureType = cert.sigAlgName
                val md5 = getThumbPrint(cert, "MD5") ?: ""
                val sha1 = getThumbPrint(cert, "SHA-1") ?: ""
                val sha256 = getThumbPrint(cert, "SHA-256") ?: ""
                val apkVerifier = model.Verifier(cert.version, subject, validFrom, validUntil, publicKeyType, modulus, signatureType, md5, sha1, sha256)
                list.add(apkVerifier)
                val apkVerifierResult = VerifierResult(isSuccess = true, isApk = false, path = input, name = inputFile.name, data = list)
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
        val list = ArrayList<model.Verifier>()
        val inputFile = File(input)
        val path = inputFile.path
        val name = inputFile.name
        val verifier: ApkVerifier = ApkVerifier.Builder(inputFile).build()
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
                        val subject = cert.subjectX500Principal.name
                        val validFrom = cert.notBefore.toString()
                        val validUntil = cert.notAfter.toString()
                        val publicKeyType = (cert.publicKey as? RSAPublicKey)?.algorithm ?: ""
                        val modulus = (cert.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
                        val signatureType = cert.sigAlgName
                        val md5 = getThumbPrint(cert, "MD5") ?: ""
                        val sha1 = getThumbPrint(cert, "SHA-1") ?: ""
                        val sha256 = getThumbPrint(cert, "SHA-256") ?: ""
                        val apkVerifier = model.Verifier(1, subject, validFrom, validUntil, publicKeyType, modulus, signatureType, md5, sha1, sha256)
                        list.add(apkVerifier)
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
                        val subject = cert.subjectX500Principal.name
                        val validFrom = cert.notBefore.toString()
                        val validUntil = cert.notAfter.toString()
                        val publicKeyType = (cert.publicKey as? RSAPublicKey)?.algorithm ?: ""
                        val modulus = (cert.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
                        val signatureType = cert.sigAlgName
                        val md5 = getThumbPrint(cert, "MD5") ?: ""
                        val sha1 = getThumbPrint(cert, "SHA-1") ?: ""
                        val sha256 = getThumbPrint(cert, "SHA-256") ?: ""
                        val apkVerifier = model.Verifier(2, subject, validFrom, validUntil, publicKeyType, modulus, signatureType, md5, sha1, sha256)
                        list.add(apkVerifier)
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
                        val subject = cert.subjectX500Principal.name
                        val validFrom = cert.notBefore.toString()
                        val validUntil = cert.notAfter.toString()
                        val publicKeyType = (cert.publicKey as? RSAPublicKey)?.algorithm ?: ""
                        val modulus = (cert.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
                        val signatureType = cert.sigAlgName
                        val md5 = getThumbPrint(cert, "MD5") ?: ""
                        val sha1 = getThumbPrint(cert, "SHA-1") ?: ""
                        val sha256 = getThumbPrint(cert, "SHA-256") ?: ""
                        val apkVerifier = model.Verifier(3, subject, validFrom, validUntil, publicKeyType, modulus, signatureType, md5, sha1, sha256)
                        list.add(apkVerifier)
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
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
     * 验证签名
     * @param path 签名路径
     * @param password 签名密码
     */
    fun verifyAlisa(path: String, password: String): String {
        var fileInputStream: FileInputStream? = null
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            fileInputStream = FileInputStream(path)
            keyStore.load(fileInputStream, password.toCharArray())
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                return aliases.nextElement()
            }
        } catch (_: Exception) {

        } finally {
            fileInputStream?.close()
        }
        return ""
    }

    /**
     * 验证别名密码
     */
    fun verifyAlisaPassword(): Boolean {
        var fileInputStream: FileInputStream? = null
        try {
            val keyStore = KeyStore.getInstance("PKCS12")
            fileInputStream = FileInputStream(apkSignatureState.signaturePath)
            keyStore.load(fileInputStream, apkSignatureState.signaturePassword.toCharArray())
            if (keyStore.containsAlias(apkSignatureState.signatureAlisa)) {
                val key = keyStore.getKey(apkSignatureState.signatureAlisa, apkSignatureState.signatureAlisaPassword.toCharArray())
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
    fun initInternal() = launch(Dispatchers.IO) {
        val resourcesDir = File(resourcesDir)
        val aaptFile = if (isWindows) {
            resourcesDir.resolve("aapt.exe")
        } else {
            resourcesDir.resolve("aapt")
        }
        val keytoolFile = if (isWindows) {
            resourcesDir.resolve("keytool.exe")
        } else {
            resourcesDir.resolve("keytool")
        }
        // 赋予可执行权限
        if (isMacos && (!aaptFile.canExecute() || !keytoolFile.canExecute())) {
            val builder = ProcessBuilder()
            builder.command("chmod", "+x", aaptFile.absolutePath).start()
            builder.command("chmod", "+x", keytoolFile.absolutePath).start()
        }
        if (aaptFile.exists() && keytoolFile.exists()) {
            dataBase.initInternal(aaptFile.absolutePath, keytoolFile.absolutePath)
            this@MainViewModel.aapt = dataBase.getAaptPath()
            this@MainViewModel.keytool = dataBase.getKeytoolPath()
        }
    }

    /**
     * 使用内置aapt
     */
    fun useInternalAaptPath() {
        val resourcesDir = File(resourcesDir)
        val aaptFile = if (isWindows) {
            resourcesDir.resolve("aapt.exe")
        } else {
            resourcesDir.resolve("aapt")
        }
        if (aaptFile.exists()) {
            dataBase.updateAaptPath(aaptFile.absolutePath)
        } else {
            dataBase.updateAaptPath("")
        }
        this.aapt = dataBase.getAaptPath()
    }

    /**
     * 更新aapt路径
     * @param aaptPath 路径
     */
    fun updateAaptPath(aaptPath: String) {
        dataBase.updateAaptPath(aaptPath)
        aapt = dataBase.getAaptPath()
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
        apkSignatureState.outPutPath = this.outputPath
    }

    /**
     * 更新文件对齐标识
     * @param isAlignFileSize 是否开启文件对齐
     */
    fun updateIsAlignFileSize(isAlignFileSize: Boolean) {
        dataBase.updateIsAlignFileSize(isAlignFileSize)
        this.isAlignFileSize = dataBase.getIsAlignFileSize()
    }

    private fun getThumbPrint(cert: X509Certificate?, type: String?): String? {
        val md = MessageDigest.getInstance(type) // lgtm [java/weak-cryptographic-algorithm]
        val der: ByteArray = cert?.encoded ?: return null
        md.update(der)
        val digest = md.digest()
        return hexify(digest)
    }

    private fun hexify(bytes: ByteArray): String {
        val hexDigits = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')
        val buf = StringBuilder(bytes.size * 3)
        for (aByte in bytes) {
            buf.append(hexDigits[aByte.toInt() and 0xf0 shr 4])
            buf.append(hexDigits[aByte.toInt() and 0x0f])
            buf.append(' ')
        }
        return buf.toString()
    }

    private fun extractValue(line: String, attribute: String): String {
        val pattern = Regex("$attribute='([^']*)'")
        val matchResult = pattern.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun extractVersion(line: String, attribute: String): String {
        val pattern = Regex("$attribute:'(\\d+)'")
        val matchResult = pattern.find(line)
        return matchResult?.groups?.get(1)?.value ?: ""
    }

    private fun extractIcon(apkPath: String, iconPath: String): ImageBitmap? {
        var inputStream: InputStream? = null
        var outputStream: ByteArrayOutputStream? = null
        try {
            ZipFile(apkPath).use { zipFile ->
                zipFile.getEntry(iconPath)?.let { entry ->
                    inputStream = zipFile.getInputStream(entry)
                    outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (inputStream!!.read(buffer).also { len = it } != -1) {
                        outputStream!!.write(buffer, 0, len)
                    }
                    val bytes: ByteArray = outputStream!!.toByteArray()
                    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                inputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            try {
                outputStream?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        return null
    }
}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
    data class Error(val msg: String) : UIState
}