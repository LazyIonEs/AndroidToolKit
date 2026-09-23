package org.tool.kit.tests.data

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmIconOutputs
import org.tool.kit.data.source.JvmImageProcessor
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.usecase.GenerateIconsUseCase

class IconNativeFixtureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    private val sizes = listOf(48, 72, 96, 144, 192)
    private val normal = IconProcessingOptions(3, 5, true, 70, 100, 1, 6, 85f)

    private fun input(extension: String): File {
        val png = extension == "png"
        val pixels = BufferedImage(73, 57, if (png) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val alpha = if (png) 80 + (x * 3 + y * 7) % 176 else 255
            pixels.setRGB(x, y, (alpha shl 24) or ((x * 3 % 256) shl 16) or ((y * 4 % 256) shl 8) or ((x + y) * 2 % 256))
        }
        return temporary.root.resolve("中文 source.$extension").also { ImageIO.write(pixels, if (png) "png" else "jpeg", it) }
    }

    @Test fun realRustOutputsHaveExpectedNamesAndDimensionsForEveryAlgorithmAndCompressionMode() = runBlocking {
        val png = input("png"); val jpg = input("jpg"); val jpeg = input("jpeg")
        val cases = buildList {
            for (algorithm in 0..3) for (lossless in listOf(true, false)) {
                add(Triple("png-$algorithm-$lossless", png, normal.copy(pngAlgorithm = algorithm, lossless = lossless, minimum = 0, target = 83, speed = 5, preset = 2)))
            }
            for (algorithm in 0..5) for (lossless in listOf(true, false)) {
                add(Triple("jpeg-$algorithm-$lossless", jpeg, normal.copy(jpegAlgorithm = algorithm, lossless = lossless, quality = 43f)))
            }
            add(Triple("default-png", png, normal))
            add(Triple("default-jpg", jpg, normal))
            add(Triple("lossy-jpg", jpg, normal.copy(lossless = false)))
        }
        val service = GenerateIconsUseCase(JvmImageProcessor(Dispatchers.IO), JvmIconOutputs(Dispatchers.IO))
        for ((name, source, options) in cases) {
            val output = temporary.root.resolve(name)
            val request = GenerateIconsRequest(source.path, output.path, " res 中文 ", "drawable", "icon fixture", options)
            val result = service(request) as GenerateIconsOutcome.Success
            assertEquals(File(output, " res 中文 ").path, result.outputDirectory)
            val expectedPaths = densities.map { density ->
                File(output, " res 中文 /drawable-$density/icon fixture.${source.extension}").path
            }
            assertEquals(expectedPaths, result.outputPaths)
            for ((index, path) in result.outputPaths.withIndex()) {
                val file = File(path)
                assertTrue(file.length() > 0, "$name/${densities[index]}")
                val image = assertNotNull(ImageIO.read(file))
                assertEquals(sizes[index], image.width)
                assertEquals(sizes[index], image.height)
            }
            assertEquals(expectedPaths.toSet(), output.walkTopDown().filter { it.isFile }.map { it.path }.toSet())
        }
    }

    @Test fun malformedInputAndUnwritableOutputFailWithoutLeavingIntermediateFiles() = runBlocking {
        val processor = JvmImageProcessor(Dispatchers.IO)
        val service = GenerateIconsUseCase(processor, JvmIconOutputs(Dispatchers.IO))
        val bad = temporary.root.resolve("bad.png").apply { writeText("not an image") }
        val request = GenerateIconsRequest(bad.path, temporary.root.resolve("out").path, "res", "mipmap", "icon", normal)
        val malformed = service(request) as GenerateIconsOutcome.Failure
        assertTrue(malformed.outputPaths.isEmpty()); assertFalse(malformed.message.isNullOrEmpty())
        assertTrue(temporary.root.walkTopDown().none { "_resize" in it.name })
        val source = input("png")
        val blocker = temporary.root.resolve("not-a-directory").apply { writeText("keep") }
        val failure = service(request.copy(inputPath = source.path, outputPath = blocker.path)) as GenerateIconsOutcome.Failure
        assertTrue(failure.outputPaths.isEmpty()); assertFalse(failure.message.isNullOrEmpty())
        assertEquals("keep", blocker.readText())
        assertTrue(temporary.root.walkTopDown().none { "_resize" in it.name })
    }
}
