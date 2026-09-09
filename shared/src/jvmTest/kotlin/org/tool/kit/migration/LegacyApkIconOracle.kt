package org.tool.kit.migration

// Frozen Phase 4B icon implementation, retained solely as a migration oracle.
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.devrel.gmscore.tools.apk.arsc.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.tool.kit.utils.*
import java.util.zip.ZipFile
private val logger = io.github.oshai.kotlinlogging.KotlinLogging.logger("LegacyApkIconOracle")
internal suspend fun legacyExtractIcon(text: String?, apkPath: String, iconPath: String): ImageBitmap? =
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
    val binaryResourceIdentifier = ResourceIdentifier.create(resourceId)

    ZipFile(apkPath).use { zipFile ->
        val inputStream = zipFile.getZipFileInputStream("resources.arsc") ?: return null
        val resourceFile = ResourceFile.fromInputStream(inputStream)

        val resourceTable = resourceFile.chunks.firstOrNull() as? ResourceTableChunk ?: return null

        val blamer = ArscBlamer(resourceTable).apply { blame() }

        val matchingTypeChunk = blamer.getTypeChunks().lastOrNull { typeChunk ->
            typeChunk.containsResource(binaryResourceIdentifier) &&
                    typeChunk.configuration.density() in listOf(160, 240, 320, 480, 640)
        } ?: return null

        val entry = matchingTypeChunk.entries[binaryResourceIdentifier.entryId()] ?: return null
        if (entry.isComplex) return null

        val resourcePath = resourceTable.stringPool.getString(entry.value()?.data() ?: 0)
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

