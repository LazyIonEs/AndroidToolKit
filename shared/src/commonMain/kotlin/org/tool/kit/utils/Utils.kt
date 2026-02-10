package org.tool.kit.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import brut.xml.XmlUtils
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.FileAppender
import com.google.devrel.gmscore.tools.apk.arsc.ArscBlamer
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.downloadDir
import io.github.vinceglb.filekit.path
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.apache.hc.core5.http.ConnectionClosedException
import org.jetbrains.skia.Image
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tool.kit.model.Asset
import org.tool.kit.model.DownloadResult
import org.tool.kit.model.FileSelectorType
import org.tool.kit.model.GithubRestLatestResult
import org.tool.kit.model.GithubRestResult
import org.tool.kit.model.Verifier
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.check_update_remaining_tips
import org.tool.kit.shared.generated.resources.network_connection_error
import org.tool.kit.shared.generated.resources.network_error
import org.w3c.dom.Node
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.ProxySelector
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.zip.ZipFile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/1 09:02
 * @Description : 工具类
 * @Version     : 1.0
 */

private val logger = KotlinLogging.logger("Utils")

/**
 * 获取下载目录
 */
fun getDownloadDirectory() = FileKit.downloadDir.path

val isWindows = System.getProperty("os.name").startsWith("Win")

val isLinux = System.getProperty("os.name").startsWith("Linux")

val isMac = System.getProperty("os.name").startsWith("Mac")

val String.isApk: Boolean
    get() = this.endsWith(".apk")

val String.isKey: Boolean
    get() = this.endsWith(".jks") || this.endsWith(".keystore")

val String.isImage: Boolean
    get() = this.endsWith(".png") || this.endsWith(".jpg") || this.endsWith(".jpeg")

val String.isPng: Boolean
    get() = this.endsWith(".png")

val String.isJPG: Boolean
    get() = this.endsWith(".jpg")

val String.isJPEG: Boolean
    get() = this.endsWith(".jpeg")

fun <T> Array<out T>.toFileExtensions(): List<String> {
    val list = mutableListOf<String>()
    for (type in this) {
        when (type) {
            FileSelectorType.APK -> list.add("apk")
            FileSelectorType.KEY -> {
                list.add("jks")
                list.add("keystore")
            }

            FileSelectorType.EXECUTE -> list.add("exe")
            FileSelectorType.IMAGE -> {
                list.add("png")
                list.add("jpg")
                list.add("jpeg")
                list.add("webp")
            }
        }
    }
    return list
}

fun <T> Array<out T>.checkFile(path: String?): Boolean {
    if (path.isNullOrBlank()) return false
    val file = File(path)
    for (type in this) {
        val isConform = when (type) {
            FileSelectorType.APK -> file.name.isApk
            FileSelectorType.KEY -> file.name.isKey
            FileSelectorType.EXECUTE -> file.canExecute()
            FileSelectorType.IMAGE -> file.name.isImage
            else -> false
        }
        if (isConform) return true
    }
    return false
}

val resourcesDir: String = System.getProperty("compose.application.resources.dir")
    ?: File(System.getProperty("user.dir"), "resources").absolutePath

val resourcesDirWithOs: String = System.getProperty("compose.application.resources.dir")
    ?: File(File(System.getProperty("user.dir"), "resources"), appInternalResourcesDir).absolutePath

val resourcesDirWithCommon: String = System.getProperty("compose.application.resources.dir")
    ?: File(File(System.getProperty("user.dir"), "resources"), "common").absolutePath

fun X509Certificate.getVerifier(version: String): Verifier {
    val subject = this.subjectX500Principal.name
    val validFrom = this.notBefore.toString()
    val validUntil = this.notAfter.toString()
    val publicKeyType = (this.publicKey as? RSAPublicKey)?.algorithm ?: ""
    val modulus = (this.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
    val signatureType = this.sigAlgName
    val md5 = getThumbPrint(this, "MD5") ?: ""
    val sha1 = getThumbPrint(this, "SHA-1") ?: ""
    val sha256 = getThumbPrint(this, "SHA-256") ?: ""
    val apkVerifier = Verifier(
        version,
        subject,
        validFrom,
        validUntil,
        publicKeyType,
        modulus,
        signatureType,
        md5,
        sha1,
        sha256
    )
    return apkVerifier
}

fun getThumbPrint(cert: X509Certificate?, type: String?): String? {
    val md = MessageDigest.getInstance(type) // lgtm [java/weak-cryptographic-algorithm]
    val der: ByteArray = cert?.encoded ?: return null
    md.update(der)
    val digest = md.digest()
    return hexify(digest)
}

private fun hexify(bytes: ByteArray): String {
    val hexDigits = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )
    val buf = StringBuilder(bytes.size * 3)
    for (aByte in bytes) {
        buf.append(hexDigits[aByte.toInt() and 0xf0 shr 4])
        buf.append(hexDigits[aByte.toInt() and 0x0f])
        buf.append(' ')
    }
    return buf.toString().trim().replace(' ', ':')
}

fun extractValue(line: String, attribute: String): String {
    val pattern = Regex("$attribute='([^']*)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

fun extractVersion(line: String, attribute: String): String {
    val pattern = Regex("$attribute:'(\\d+)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

suspend fun extractAndroidManifest(aapt: File, apkPath: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val stdinStream = "".byteInputStream()
            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()

            val exitValue = withContext(Dispatchers.IO) {
                ExternalCommand(aapt.absolutePath).execute(
                    listOf("dump", "xmltree", apkPath, "--file", "AndroidManifest.xml"),
                    stdinStream, stdoutStream, stderrStream
                )
            }
            if (exitValue != 0) {
                // 执行命令出现错误
                return@withContext null
            }
            val result = stdoutStream.toString("UTF-8").trimIndent()
            return@withContext result
        } catch (e: Exception) {
            logger.error(e) { "extractAndroidManifest 提取AndroidManifest异常, 异常信息: ${e.message}" }
        }
        return@withContext null
    }

fun extractChannel(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val regex =
        """A: http://schemas\.android\.com/apk/res/android:name\(0x[0-9a-fA-F]{8}\)="UMENG_CHANNEL" \(Raw: "UMENG_CHANNEL"\)\s+A: http://schemas\.android\.com/apk/res/android:value\(0x[0-9a-fA-F]{8}\)="([^"]+)"""".toRegex()
    return regex.find(text)?.groupValues?.getOrNull(1)
}

suspend fun extractIcon(text: String?, apkPath: String, iconPath: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        try {
            if (iconPath.endsWith(".xml")) {
                if (text == null) {
                    return@withContext null
                }
                // 正则表达式匹配 "A: http://schemas.android.com/apk/res/android:icon" 后面的十六进制值
                val regex =
                    """A: http://schemas.android.com/apk/res/android:icon\(0x[0-9a-fA-F]+\)=@0x([0-9a-fA-F]+)""".toRegex()
                // 查找匹配
                regex.find(text)?.let { matchResult ->
                    val resourceId =
                        matchResult.groupValues[1].toIntOrNull(16) ?: return@withContext null
                    return@withContext extractBitmapFromResourceTable(apkPath, resourceId)
                }
            } else {
                return@withContext processIconFromZip(apkPath, iconPath)
            }
        } catch (e: Exception) {
            logger.error(e) { "extractIcon 提取图标异常, 异常信息: ${e.message}" }
        }
        return@withContext null
    }

private fun extractBitmapFromResourceTable(apkPath: String, resourceId: Int): ImageBitmap? {
    val binaryResourceIdentifier = BinaryResourceIdentifier.create(resourceId)

    ZipFile(apkPath).use { zipFile ->
        val inputStream = zipFile.getZipFileInputStream("resources.arsc") ?: return null
        val resourceFile = BinaryResourceFile.fromInputStream(inputStream)

        val resourceTable = resourceFile.chunks.firstOrNull() as? ResourceTableChunk ?: return null

        val blamer = ArscBlamer(resourceTable).apply { blame() }

        val matchingTypeChunk = blamer.typeChunks.lastOrNull { typeChunk ->
            typeChunk.containsResource(binaryResourceIdentifier) &&
                    typeChunk.configuration.density() in listOf(160, 240, 320, 480, 640)
        } ?: return null

        val entry = matchingTypeChunk.entries[binaryResourceIdentifier.entryId()] ?: return null
        if (entry.isComplex) return null

        val resourcePath = resourceTable.stringPool.getString(entry.value().data())
        val resourceBytes = zipFile.getZipFileData(resourcePath) ?: return null

        return Image.makeFromEncoded(resourceBytes).toComposeImageBitmap()
    }
}

private fun processIconFromZip(apkPath: String, iconPath: String): ImageBitmap? {
    ZipFile(apkPath).use { zipFile ->
        return zipFile.getZipFileData(iconPath)?.let { bytes ->
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    }
}

fun ZipFile.getZipFileData(path: String): ByteArray? {
    val zipEntry = this.getEntry(path) ?: return null
    val inputStream = this.getInputStream(zipEntry)
    inputStream.use {
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(1024 * 8)
        var len: Int
        while (inputStream!!.read(buffer).also { len = it } != -1) {
            outputStream.write(buffer, 0, len)
        }
        return outputStream.toByteArray()
    }
}

fun ZipFile.getZipFileInputStream(path: String): InputStream? {
    val zipEntry = this.getEntry(path) ?: return null
    return this.getInputStream(zipEntry)
}

private val appInternalResourcesDir: String
    get() {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> {
                "windows"
            }

            os.contains("mac") -> {
                when (val osArch = System.getProperty("os.arch")) {
                    "x86_64", "amd64" -> "macos-x64"
                    "aarch64" -> "macos-arm64"
                    else -> error("Unsupported arch: $osArch")
                }
            }

            else -> error("Unsupported operating system")
        }
    }

private enum class FileSizeType(val unit: String) {
    SIZE_TYPE_B("B"), SIZE_TYPE_KB("KB"), SIZE_TYPE_MB("MB"), SIZE_TYPE_GB("GB"), SIZE_TYPE_TB("TB")
}

fun File.getFileLength(): Long {
    if (this.isDirectory) {
        var sum = 0L
        this.walk()
            .forEach { file -> sum += file.length() }
        return sum
    } else {
        return this.length()
    }
}

/**
 * @param scale 精确到小数点以后几位 (Accurate to a few decimal places)
 */
fun Long.formatFileSize(
    scale: Int = 2,
    withUnit: Boolean = true,
    withInterval: Boolean = false
): String {
    val divisor = if (isMac) { //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
        1000L
    } else {
        1024L
    }
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(
            BigDecimal.valueOf(this),
            scale,
            FileSizeType.SIZE_TYPE_B,
            divisor
        )
    val interval = if (withInterval) " " else ""
    if (kiloByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_B.unit else ""}"
    } //KB
    val megaByte = formatSizeByTypeWithDivisor(kiloByte, scale, FileSizeType.SIZE_TYPE_KB, divisor)
    if (megaByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_KB.unit else ""}"
    } //M
    val gigaByte = formatSizeByTypeWithDivisor(megaByte, scale, FileSizeType.SIZE_TYPE_MB, divisor)
    if (gigaByte.toDouble() < 1) {
        return "${megaByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_MB.unit else ""}"
    } //GB
    val teraBytes = formatSizeByTypeWithDivisor(gigaByte, scale, FileSizeType.SIZE_TYPE_GB, divisor)
    if (teraBytes.toDouble() < 1) {
        return "${gigaByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_GB.unit else ""}"
    } //TB
    return "${teraBytes.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_TB.unit else ""}"
}

fun Long.formatFileUnit(): String {
    val divisor = if (isMac) { //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
        1000L
    } else {
        1024L
    }
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(BigDecimal.valueOf(this), 2, FileSizeType.SIZE_TYPE_B, divisor)
    if (kiloByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_B.unit
    } //KB
    val megaByte = formatSizeByTypeWithDivisor(kiloByte, 2, FileSizeType.SIZE_TYPE_KB, divisor)
    if (megaByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_KB.unit
    } //M
    val gigaByte = formatSizeByTypeWithDivisor(megaByte, 2, FileSizeType.SIZE_TYPE_MB, divisor)
    if (gigaByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_MB.unit
    } //GB
    val teraBytes = formatSizeByTypeWithDivisor(gigaByte, 2, FileSizeType.SIZE_TYPE_GB, divisor)
    if (teraBytes.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_GB.unit
    } //TB
    return FileSizeType.SIZE_TYPE_TB.unit
}

private fun formatSizeByTypeWithDivisor(
    size: BigDecimal, scale: Int, sizeType: FileSizeType, divisor: Long
): BigDecimal = size.divide(
    BigDecimal.valueOf(divisor),
    scale,
    if (sizeType == FileSizeType.SIZE_TYPE_B) RoundingMode.DOWN else RoundingMode.HALF_UP
)

/**
 * 在文件夹中打开
 */
fun browseFileDirectory(file: File?) {
    if (file == null || !file.exists()) return
    if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
        Desktop.getDesktop().browseFileDirectory(file)
    } else {
        if (isWindows) {
            Runtime.getRuntime().exec(arrayOf("explorer", "/select,", file.absolutePath))
        } else if (isMac) {
            Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
        } else if (isLinux) {
            if (runCommand(arrayOf("xdg-open", file.absolutePath))) return
            if (runCommand(arrayOf("gnome-open", file.absolutePath))) return
        }
    }
}

private fun runCommand(command: Array<String>): Boolean {
    try {
        val p = Runtime.getRuntime().exec(command) ?: return false
        try {
            val value = p.exitValue()
            return if (value == 0) {
                false
            } else {
                false
            }
        } catch (e: IllegalThreadStateException) {
            logger.error(e) { "runCommand 执行命令异常, 异常信息: ${e.message}" }
            return true
        }
    } catch (e: IOException) {
        logger.error(e) { "runCommand 执行命令异常, 异常信息: ${e.message}" }
        return false
    }
}

fun renameManifestPackage(file: File, minSdkVersion: String, targetSdkVersion: String) {
    val doc = XmlUtils.loadDocument(file)
    // 查找 uses-sdk 节点
    val sdkElems = doc.getElementsByTagName("uses-sdk")
    if (sdkElems.length > 0) {
        val sdk = sdkElems.item(0)
        sdk.attributes.getNamedItem("android:minSdkVersion")
            ?.nodeValue = minSdkVersion
        sdk.attributes.getNamedItem("android:targetSdkVersion")
            ?.nodeValue = targetSdkVersion
    } else {
        // 若无 uses‑sdk 标签就插入
        val newSdk = doc.createElement("uses-sdk")
        newSdk.setAttribute("android:minSdkVersion", minSdkVersion)
        newSdk.setAttribute("android:targetSdkVersion", targetSdkVersion)
        doc.documentElement.insertBefore(newSdk, doc.documentElement.firstChild)
    }
    XmlUtils.saveDocument(doc, file)
}

fun renameValueAppName(file: File, appName: String) {
    if (!file.isFile()) {
        return
    }
    val key = "app_name"
    val doc = XmlUtils.loadDocument(file)
    val expression = String.format("/resources/%s[@name='%s']/text()", "string", key)
    val node = XmlUtils.evaluateXPath(doc, expression, Node::class.java)
    node.nodeValue = appName
    XmlUtils.saveDocument(doc, file)
}

private const val TIME_TO_TRIGGER_PROGRESS = 50

/**
 * 下载文件
 */
@OptIn(ExperimentalTime::class)
suspend fun downloadFile(
    url: String,
    destFile: File,
    onProgress: suspend (downloaded: Long, total: Long) -> Unit
) = coroutineScope {
    logger.info { "downloadFile 开始下载, url: $url, destFile: $destFile" }
    destFile.parentFile?.let { parent ->
        if (!parent.exists()) parent.mkdirs()
    }
    if (destFile.exists()) destFile.delete()

    val client = HttpClient(Apache5) {
        install(Logging) {
            level = LogLevel.INFO
        }
        engine {
            customizeClient {
                setProxySelector(ProxySelector.getDefault())
            }
        }
    }

    return@coroutineScope withContext(Dispatchers.IO) {
        var lastProgressTime = 0L
        try {
            client.prepareGet(url) {
                onDownload { bytesSentTotal: Long, contentLength: Long? ->
                    val currentTime = Clock.System.now().toEpochMilliseconds()
                    if (currentTime - lastProgressTime >= TIME_TO_TRIGGER_PROGRESS) {
                        onProgress(minOf(bytesSentTotal, contentLength ?: 0L), contentLength ?: 0)
                        lastProgressTime = currentTime
                    }
                }
            }.execute { response ->
                response.bodyAsChannel().copyAndClose(destFile.writeChannel())
            }
            DownloadResult(true, null, null)
        } catch (e: ConnectionClosedException) {
            logger.error(e) { "downloadFile 下载异常, 异常信息: ${e.message}" }
            destFile.delete()
            DownloadResult(false, Res.string.network_connection_error, destFile)
        } catch (e: Exception) {
            logger.error(e) { "downloadFile 下载异常, 异常信息: ${e.message}" }
            destFile.delete()
            DownloadResult(false, Res.string.network_error, null)
        } finally {
            client.close()
        }
    }
}

suspend fun checkUpdate() = coroutineScope {
    logger.info { "checkUpdate 开始检查更新" }
    val client = HttpClient(Apache5) {
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 1)
            exponentialDelay()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                isLenient = true
                allowSpecialFloatingPointValues = true
                allowStructuredMapKeys = true
                prettyPrint = false
                useArrayPolymorphism = false
            })
        }
        engine {
            customizeClient {
                setProxySelector(ProxySelector.getDefault())
            }
        }
    }
    return@coroutineScope withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/LazyIonEs/AndroidToolKit/releases/latest"
            val response: HttpResponse = client.get(url) {
                contentType(ContentType.Application.Json)
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            val remaining = response.headers["x-ratelimit-remaining"]
            if (remaining == "0") {
                GithubRestResult(false, Res.string.check_update_remaining_tips, null)
            } else {
                val result: GithubRestLatestResult = response.body()
                GithubRestResult(true, null, result)
            }
        } catch (e: ConnectionClosedException) {
            logger.error(e) { "checkUpdate 检查更新异常, 异常信息: ${e.message}" }
            GithubRestResult(false, Res.string.network_connection_error, null)
        } catch (e: Exception) {
            logger.error(e) { "checkUpdate 检查更新异常, 异常信息: ${e.message}" }
            GithubRestResult(false, Res.string.network_error, null)
        } finally {
            client.close()
        }
    }
}

fun String.isNewVersion(other: String): Boolean {
    fun normalize(version: String) = version.trim().removePrefix("v").removePrefix("V")
    val parts1 = normalize(this).split(".").map { it.toIntOrNull() ?: 0 }
    val parts2 = normalize(other).split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(parts1.size, parts2.size)) {
        val diff = parts1.getOrElse(i) { 0 } - parts2.getOrElse(i) { 0 }
        if (diff != 0) return diff > 0
    }
    return false
}

fun MutableList<Asset>.filterByOS(): List<Asset>? {
    val arch = System.getProperty("os.arch")
    val isArm = arch.contains("aarch64", true) || arch.contains("arm64", true)
    val targetArchKeywords = if (isArm) {
        listOf("arm64", "aarch64")
    } else {
        listOf("x64", "x86_64", "amd64")
    }
    val targetOsKeyword = when {
        isMac -> "macos"
        isLinux -> "linux"
        isWindows -> "windows"
        else -> return null
    }
    return this.filter { asset ->
        val osMatches = asset.name.contains(targetOsKeyword, true)
        val archMatches = targetArchKeywords.any { keyword ->
            asset.name.contains(keyword)
        }
        osMatches && archMatches
    }
}

/**
 * 获取日志文件
 */
fun getLogFile(): File? {
    val context = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return null
    val logger = context.getLogger(Logger.ROOT_LOGGER_NAME)
    val appender = logger.getAppender("FILE")
    if (appender is FileAppender<*>) {
        return File(appender.file)
    }
    return null
}

private const val CHAR_POOL = "abcdefghijklmnopqrstuvwxyz"

private val secureRandom by lazy { SecureRandom() }

/**
 * 生成随机字符
 */
fun generateSecureToken(min: Int, max: Int): String {
    val length = secureRandom.nextInt(max - min + 1) + min
    return buildString {
        repeat(length) {
            val index = secureRandom.nextInt(CHAR_POOL.length)
            append(CHAR_POOL[index])
        }
    }
}