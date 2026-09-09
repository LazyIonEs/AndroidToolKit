package org.tool.kit.utils

import brut.xml.XmlUtils
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.FileAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.downloadsDir
import io.github.vinceglb.filekit.path
import io.ktor.client.request.get
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tool.kit.model.FileSelectorType
import org.w3c.dom.Node
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.zip.ZipFile

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
fun getDownloadDirectory() = FileKit.downloadsDir.path

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

fun renameManifestPackage(
    file: File,
    packageName: String,
    minSdkVersion: String,
    targetSdkVersion: String
) {
    val doc = XmlUtils.loadDocument(file)
    logger.info { "generateApktool ${doc.getUserData("package")}" }

    val manifest = doc.firstChild
    val attrs = manifest.attributes
    val packageAttr = attrs.getNamedItem("package")
    if (packageAttr != null) {
        packageAttr.nodeValue = packageName
    }

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
