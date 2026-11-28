package org.tool.kit.feature.apk

import androidx.compose.ui.graphics.ImageBitmap

/** Decodes a LibChecker rule icon identifier into a rasterized Compose bitmap. */
fun interface LibRuleIconDecoder {
    suspend fun decode(iconId: String): ImageBitmap?
}
