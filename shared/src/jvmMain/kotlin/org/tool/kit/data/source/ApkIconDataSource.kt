package org.tool.kit.data.source

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import com.google.devrel.gmscore.tools.apk.arsc.*
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.utils.getZipFileData
import org.tool.kit.utils.getZipFileInputStream
import java.util.zip.ZipFile

class ApkIconDataSource(private val io: CoroutineDispatcher) {
    private val logger = KotlinLogging.logger("ApkIconDataSource")
/** 普通图标直接从 ZIP 读取；XML 图标先解析清单中的资源 ID，再查找位图候选。 */
suspend fun read(text: String?, apkPath: String, iconPath: String): ApkIconSource? =
    withContext(io) {
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
        } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
            logger.error(e) { "extractIcon 提取图标异常, 异常信息: ${e.message}" }
        }
        return@withContext null
    }

/** 按资源 ID 查找常用密度的最后一个匹配配置，复杂资源条目不作为位图解析。 */
private fun extractBitmapFromResourceTable(apkPath: String, resourceId: Int): ApkIconSource? {
    val binaryResourceIdentifier = ResourceIdentifier.create(resourceId)

    ZipFile(apkPath).use { zipFile ->
        val inputStream = zipFile.getZipFileInputStream("resources.arsc") ?: return null
        val resourceFile = inputStream.use { ResourceFile.fromInputStream(it) }

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

        return validatedSource(resourceBytes)
    }
}

/** 读取图标条目并验证图片编码，ZIP 与条目流在返回前关闭。 */
private fun processIconFromZip(apkPath: String, iconPath: String): ApkIconSource? {
    ZipFile(apkPath).use { zipFile ->
        return zipFile.getZipFileData(iconPath)?.let { bytes ->
            validatedSource(bytes)
        }
    }
}


    /** 先用 Skia 试解码再发布字节模型，阻止不支持的图像进入页面状态。 */
    private fun validatedSource(bytes: ByteArray): ApkIconSource {
        Image.makeFromEncoded(bytes).use { /* Reject unsupported image data before publishing it. */ }
        return ApkIconSource(bytes)
    }
}
