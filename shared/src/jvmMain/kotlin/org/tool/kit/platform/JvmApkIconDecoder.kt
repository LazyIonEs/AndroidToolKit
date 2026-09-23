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
        Image.makeFromEncoded(source.bytes()).toComposeImageBitmap()
    }
}
