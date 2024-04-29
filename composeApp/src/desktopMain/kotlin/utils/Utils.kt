package utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import file.FileSelectorType
import model.Verifier
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skiko.toBufferedImage
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
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

val isWindows = System.getProperty("os.name").startsWith("Win")

val isMac = System.getProperty("os.name").startsWith("Mac")

val String.isApk: Boolean
    get() = this.endsWith(".apk")

val String.isKey: Boolean
    get() = this.endsWith(".jks") || this.endsWith(".keystore")

val String.isImage: Boolean
    get() = this.endsWith(".png") || this.endsWith(".jpg") || this.endsWith(".jpeg")

private val simplingMode = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/**
 * 图片缩放
 * @param scale 缩放倍数
 */
fun Image.scale(scale: Double): BufferedImage? {
    val scaledWidth = (this.width * scale).toInt()
    val scaledHeight = (this.height * scale).toInt()
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(scaledWidth, scaledHeight)
    return if (this.scalePixels(bitmap.peekPixels() ?: return null, simplingMode, false)) {
        bitmap.asComposeImageBitmap().asSkiaBitmap().toBufferedImage()
    } else {
        null
    }
}

fun <T> Array<out T>.toFileExtensions(): List<String> {
    val list = mutableListOf<String>()
    for (type in this) {
        when (type) {
            FileSelectorType.APK -> list.add("apk")
            FileSelectorType.KEY -> {
                list.add("key")
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

val resourcesDir: String = System.getProperty("compose.application.resources.dir") ?: File(
    File(System.getProperty("user.dir"), "resources"), appInternalResourcesDir
).absolutePath

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

fun extractIcon(apkPath: String, iconPath: String): ImageBitmap? {
    try {
        if (iconPath.endsWith(".xml")) {
            // adaptive icon ?
            return null
        } else {
            ZipFile(apkPath).use { zipFile ->
                val bytes = zipFile.getZipFileData(iconPath) ?: return null
                return Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
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

private enum class FileSizeType(val id: Int, val unit: String) {
    SIZE_TYPE_B(1, "B"), SIZE_TYPE_KB(2, "KB"), SIZE_TYPE_MB(3, "M"), SIZE_TYPE_GB(4, "GB"), SIZE_TYPE_TB(5, "TB")
}

/**
 * @param scale 精确到小数点以后几位 (Accurate to a few decimal places)
 */
fun formatFileSize(size: Long, scale: Int, withUnit: Boolean = false): String {
    val divisor = 1024L //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(BigDecimal.valueOf(size), scale, FileSizeType.SIZE_TYPE_B, divisor)
    if (kiloByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${if (withUnit) FileSizeType.SIZE_TYPE_B.unit else ""}"
    } //KB
    val megaByte = formatSizeByTypeWithDivisor(kiloByte, scale, FileSizeType.SIZE_TYPE_KB, divisor)
    if (megaByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${if (withUnit) FileSizeType.SIZE_TYPE_KB.unit else ""}"
    } //M
    val gigaByte = formatSizeByTypeWithDivisor(megaByte, scale, FileSizeType.SIZE_TYPE_MB, divisor)
    if (gigaByte.toDouble() < 1) {
        return "${megaByte.toPlainString()}${if (withUnit) FileSizeType.SIZE_TYPE_MB.unit else ""}"
    } //GB
    val teraBytes = formatSizeByTypeWithDivisor(gigaByte, scale, FileSizeType.SIZE_TYPE_GB, divisor)
    if (teraBytes.toDouble() < 1) {
        return "${gigaByte.toPlainString()}${if (withUnit) FileSizeType.SIZE_TYPE_GB.unit else ""}"
    } //TB
    return "${teraBytes.toPlainString()}${if (withUnit) FileSizeType.SIZE_TYPE_TB.unit else ""}"
}

private fun formatSizeByTypeWithDivisor(
    size: BigDecimal, scale: Int, sizeType: FileSizeType, divisor: Long
): BigDecimal = size.divide(
    BigDecimal.valueOf(divisor),
    scale,
    if (sizeType == FileSizeType.SIZE_TYPE_B) RoundingMode.DOWN else RoundingMode.HALF_UP
)