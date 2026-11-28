package org.tool.kit.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Data
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.jetbrains.skia.svg.SVGLength
import org.jetbrains.skia.svg.SVGLengthContext
import org.jetbrains.skia.svg.SVGLengthUnit
import org.tool.kit.domain.repository.LibRulesRepository
import org.tool.kit.feature.apk.LibRuleIconDecoder
import java.util.concurrent.ConcurrentHashMap

private const val ICON_SIZE_PX = 64

class JvmLibRuleIconDecoder(
    private val rules: LibRulesRepository,
    private val io: CoroutineDispatcher,
) : LibRuleIconDecoder {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    override suspend fun decode(iconId: String): ImageBitmap? = withContext(io) {
        if (iconId.isBlank()) return@withContext null
        val version = try {
            rules.metadata().dataVersion
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return@withContext null
        }
        val key = "$version:$iconId"
        cache[key]?.let { return@withContext it }
        val bytes = try {
            rules.iconBytes(iconId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } ?: return@withContext null
        val rendered = renderSvgToImageBitmap(bytes, ICON_SIZE_PX) ?: return@withContext null
        cache.putIfAbsent(key, rendered) ?: rendered
    }
}

internal fun renderSvgToImageBitmap(bytes: ByteArray, sizePx: Int): ImageBitmap? = try {
    Data.makeFromBytes(bytes).use { data ->
        SVGDOM(data).use { svg ->
            val root = svg.root
            if (root != null) {
                if (root.viewBox == null) {
                    val intrinsic = root.getIntrinsicSize(SVGLengthContext(24f, 24f, 96f))
                    val vw = if (intrinsic.x > 0f) intrinsic.x else 24f
                    val vh = if (intrinsic.y > 0f) intrinsic.y else 24f
                    root.viewBox = Rect.makeWH(vw, vh)
                }
                root.width = SVGLength(sizePx.toFloat(), SVGLengthUnit.PX)
                root.height = SVGLength(sizePx.toFloat(), SVGLengthUnit.PX)
            }
            svg.setContainerSize(sizePx.toFloat(), sizePx.toFloat())
            Surface.makeRasterN32Premul(sizePx, sizePx).use { surface ->
                surface.canvas.clear(0)
                svg.render(surface.canvas)
                surface.makeImageSnapshot().use { image ->
                    image.toComposeImageBitmap()
                }
            }
        }
    }
} catch (_: Throwable) {
    null
}
