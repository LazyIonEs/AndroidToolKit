package org.tool.kit.feature.apk

import androidx.compose.ui.graphics.ImageBitmap
import org.tool.kit.domain.apk.ApkIconSource

fun interface ApkIconDecoder { suspend fun decode(source: ApkIconSource): ImageBitmap }
