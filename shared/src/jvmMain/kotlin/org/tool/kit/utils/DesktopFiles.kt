package org.tool.kit.utils

import brut.xml.XmlUtils
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.FileAppender
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.downloadsDir
import io.github.vinceglb.filekit.path
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.tool.kit.model.FileSelectorType
import org.w3c.dom.Node
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.zip.ZipFile

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/1 09:02
 * @Description : 桌面资源定位、ZIP 读取、清单修改与文件管理器操作
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

/** 匹配任一允许的扩展名或可执行条件；普通扩展名匹配不等同于文件存在性检查。 */
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

/** 安装包优先使用 Compose 提供的资源根；开发运行时定位到当前系统和架构子目录。 */
val resourcesDirWithOs: String = System.getProperty("compose.application.resources.dir")
    ?: File(File(System.getProperty("user.dir"), "resources"), appInternalResourcesDir).absolutePath

/** 安装包资源已汇总到统一根目录，开发运行时则从 common 子目录读取。 */
val resourcesDirWithCommon: String = System.getProperty("compose.application.resources.dir")
    ?: File(File(System.getProperty("user.dir"), "resources"), "common").absolutePath

/** 读取指定 ZIP 条目的完整字节数组并关闭条目流；条目不存在时返回 null。 */
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

/** 打开指定条目流；调用方负责在所属 ZipFile 关闭前消费并关闭该流。 */
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

/** 目录递归累加所有条目的 length，包含目录条目自身；普通文件直接返回长度。 */
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

/** 尝试启动桌面打开命令；返回 true 表示检查时进程仍在运行，不表示命令已成功退出。 */
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

/** 修改清单已有包名及 SDK 属性；缺少 uses-sdk 节点时创建该节点后保存。 */
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

/** 替换 strings.xml 中 app_name 的文本节点，目标文件不存在时直接返回。 */
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
 * 生成长度在 min..max 闭区间内的随机小写字符串，调用方需保证范围有效
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
