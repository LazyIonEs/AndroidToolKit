package org.tool.kit.platform

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.feature.apk.ApkIconDecoder

class JvmApkIconDecoder(private val io: CoroutineDispatcher) : ApkIconDecoder {
    /** 在平台侧将编码图标解码为 Compose 图片，使业务模型无需依赖图形库。 */
    override suspend fun decode(source: ApkIconSource) = withContext(io) {
        val bytes = source.bytes()
        if (isSvgBytes(bytes)) {
            renderSvgToImageBitmap(bytes, 512) ?: error("Invalid SVG icon")
        } else {
            Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
        }
    }

    private fun isSvgBytes(bytes: ByteArray): Boolean {
        val prefix = bytes.copyOf(minOf(bytes.size, 2048))
        if (prefix.any { it == 0.toByte() }) return false
        val text = prefix.toString(Charsets.UTF_8).removePrefix("\uFEFF").trimStart()
        return text.startsWith('<') && text.contains("<svg", ignoreCase = true)
    }
}
