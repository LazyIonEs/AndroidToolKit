package utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.devrel.gmscore.tools.apk.arsc.ArscBlamer
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.BinaryResourceIdentifier
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.downloadDir
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.FileSelectorType
import model.Verifier
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.zip.ZipFile

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/1 09:02
 * @Description : 工具类
 * @Version     : 1.0
 */

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

fun X509Certificate.getVerifier(version: Int): Verifier {
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
        version, subject, validFrom, validUntil, publicKeyType, modulus, signatureType, md5, sha1, sha256
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

suspend fun extractIcon(aapt: File, apkPath: String, iconPath: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        if (iconPath.endsWith(".xml")) {
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
            // 正则表达式匹配 "A: http://schemas.android.com/apk/res/android:icon" 后面的十六进制值
            val regex =
                """A: http://schemas.android.com/apk/res/android:icon\(0x[0-9a-fA-F]+\)=@0x([0-9a-fA-F]+)""".toRegex()
            // 查找匹配
            regex.find(result)?.let { matchResult ->
                val resourceId = matchResult.groupValues[1].toIntOrNull(16) ?: return@withContext null
                return@withContext extractBitmapFromResourceTable(apkPath, resourceId)
            }
        } else {
            return@withContext processIconFromZip(apkPath, iconPath)
        }
    } catch (e: Exception) {
        e.printStackTrace()
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
fun Long.formatFileSize(scale: Int = 2, withUnit: Boolean = true, withInterval: Boolean = false): String {
    val divisor = if (isMac) { //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
        1000L
    } else {
        1024L
    }
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(BigDecimal.valueOf(this), scale, FileSizeType.SIZE_TYPE_B, divisor)
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
fun browseFileDirectory(file: File) {
    if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
        Desktop.getDesktop().browseFileDirectory(file)
    } else {
        if (isWindows) {
            Runtime.getRuntime().exec("explorer /select, ${file.absolutePath}")
        } else if (isMac) {
            Runtime.getRuntime().exec("open -R ${file.absolutePath}")
        } else if (isLinux) {
            if (runCommand("xdg-open ${file.absolutePath}")) return
            if (runCommand("gnome-open ${file.absolutePath}")) return
            if (runCommand("xdg-open ${file.absolutePath}")) return
        }
    }
}

private fun runCommand(command: String): Boolean {
    try {
        val p = Runtime.getRuntime().exec(command) ?: return false
        try {
            val value = p.exitValue()
            return if (value == 0) {
                false
            } else {
                false
            }
        } catch (_: IllegalThreadStateException) {
            return true
        }
    } catch (_: IOException) {
        return false
    }
}