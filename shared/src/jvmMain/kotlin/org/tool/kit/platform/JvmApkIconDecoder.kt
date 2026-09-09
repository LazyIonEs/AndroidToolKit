package org.tool.kit.platform

import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.feature.apk.ApkIconDecoder

class JvmApkIconDecoder(private val io: CoroutineDispatcher) : ApkIconDecoder {
    override suspend fun decode(source: ApkIconSource) = withContext(io) {
        Image.makeFromEncoded(source.bytes()).toComposeImageBitmap()
    }
}
