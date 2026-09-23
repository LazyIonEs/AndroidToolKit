package org.tool.kit.tests.data

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.platform.mozJpeg
import org.tool.kit.platform.oxipng
import org.tool.kit.platform.quantize
import org.tool.kit.platform.resizeFir
import org.tool.kit.platform.resizePng

/** Real UniFFI calls: also proves that the native resource is reachable from the test classpath. */
class NativeFixtureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun allFiveNativeFunctionsLoadAndProduceReadableImages() {
        val png = temporary.newFile("透明 input.png")
        val input = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 16) for (x in 0 until 16) {
            input.setRGB(x, y, ((x * 17) shl 24) or (x * 17 shl 16) or (y * 17 shl 8) or 127)
        }
        assertTrue(ImageIO.write(input, "png", png))
        val resized = temporary.root.resolve("resize.png")
        resizePng(png.path, resized.path, 48u, 48u, 3u)
        assertDimensions(resized, 48)
        val lossless = temporary.root.resolve("lossless.png")
        oxipng(resized.path, lossless.path, 1)
        assertDimensions(lossless, 48)
        val before = ImageIO.read(resized)
        val after = ImageIO.read(lossless)
        for (y in 0 until 48) for (x in 0 until 48) {
            val original = before.getRGB(x, y)
            val optimized = after.getRGB(x, y)
            assertEquals(original ushr 24, optimized ushr 24)
            // oxipng may discard invisible RGB under fully transparent pixels.
            if (original ushr 24 != 0) assertEquals(original, optimized)
        }
        val quantized = temporary.root.resolve("quantized.png")
        quantize(resized.path, quantized.path, 0, 70, 10, 1)
        assertDimensions(quantized, 48)

        val jpg = temporary.newFile("input.jpg")
        val rgb = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 16) rgb.setRGB(x, y, input.getRGB(x, y))
        assertTrue(ImageIO.write(rgb, "jpg", jpg))
        val jpegResize = temporary.root.resolve("resize.jpg")
        resizeFir(jpg.path, jpegResize.path, 72u, 72u, 5u)
        assertDimensions(jpegResize, 72)
        val jpeg = temporary.root.resolve("compressed.jpeg")
        mozJpeg(jpegResize.path, jpeg.path, 85f)
        assertDimensions(jpeg, 72)
    }

    private fun assertDimensions(file: File, size: Int) {
        assertTrue(file.length() > 0)
        val decoded = checkNotNull(ImageIO.read(file))
        assertEquals(size, decoded.width)
        assertEquals(size, decoded.height)
    }
}
