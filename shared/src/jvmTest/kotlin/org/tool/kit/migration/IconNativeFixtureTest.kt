package org.tool.kit.migration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmIconOutputs
import org.tool.kit.data.source.JvmImageProcessor
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.usecase.GenerateIconsUseCase
import org.tool.kit.platform.*
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.*

class IconNativeFixtureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    private val sizes = listOf(48, 72, 96, 144, 192)
    private val normal = IconProcessingOptions(3, 5, true, 70, 100, 1, 6, 85f)

    /** Frozen successful iconGeneration algorithm from 498ef364, before the Phase 7A extraction. */
    private fun legacy(request: GenerateIconsRequest): List<File> {
        val source = File(request.inputPath)
        val outputDirectory = File(request.outputPath, request.fileDir)
        val extension = if (request.inputPath.endsWith(".png")) ".png" else if (request.inputPath.endsWith(".jpg")) ".jpg" else ".jpeg"
        val settings = request.options
        val result = mutableListOf<File>()
        for ((index, density) in densities.withIndex()) {
            val size = sizes[index].toUInt()
            val output = File(outputDirectory, "${request.iconDir}-$density/${request.iconName}$extension")
            val resized = File(outputDirectory, "${request.iconDir}-$density/${request.iconName}_resize$extension")
            output.parentFile.mkdirs(); output.delete(); resized.delete()
            if (extension == ".png") {
                resizePng(source.absolutePath, resized.absolutePath, size, size, settings.pngAlgorithm.toUByte())
                if (settings.lossless) oxipng(resized.absolutePath, output.absolutePath, settings.preset)
                else quantize(resized.absolutePath, output.absolutePath, settings.minimum, settings.target, settings.speed, settings.preset)
            } else {
                resizeFir(source.absolutePath, resized.absolutePath, size, size, settings.jpegAlgorithm.toUByte())
                mozJpeg(resized.absolutePath, output.absolutePath, if (settings.lossless) 100f else settings.quality)
            }
            result += output
            resized.delete()
        }
        return result
    }

    private fun input(extension: String): File {
        val png = extension == "png"
        val pixels = BufferedImage(73, 57, if (png) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val alpha = if (png) 80 + (x * 3 + y * 7) % 176 else 255
            pixels.setRGB(x, y, (alpha shl 24) or ((x * 3 % 256) shl 16) or ((y * 4 % 256) shl 8) or ((x + y) * 2 % 256))
        }
        return temporary.root.resolve("中文 source.$extension").also { ImageIO.write(pixels, if (png) "png" else "jpeg", it) }
    }

    @Test fun realRustOutputsMatchFrozenPipelineForEveryAlgorithmAndCompressionMode() = runBlocking {
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
        val report = StringBuilder("case\tdensity\tdimensions\tbytes\tsha256\tencodedAndPixelEqual\n")
        val evidence = File(System.getProperty("migration.fixtureRoot"), "phase7a-native-comparison").apply { mkdirs() }
        for ((name, source, options) in cases) {
            val originalRoot = temporary.root.resolve("old/$name")
            val actualRoot = temporary.root.resolve("new/$name")
            val request = GenerateIconsRequest(source.path, originalRoot.path, " res 中文 ", "drawable", "icon fixture", options)
            val old = legacy(request)
            val result = service(request.copy(outputPath = actualRoot.path)) as GenerateIconsOutcome.Success
            assertEquals(File(actualRoot, " res 中文 ").path, result.outputDirectory)
            assertEquals(5, result.outputPaths.size)
            for ((index, path) in result.outputPaths.withIndex()) {
                val file = File(path)
                assertEquals(old[index].relativeTo(originalRoot).path, file.relativeTo(actualRoot).path)
                val bytes = file.readBytes()
                assertContentEquals(old[index].readBytes(), bytes, name + "/" + densities[index])
                val expectedImage = ImageIO.read(old[index]); val actualImage = ImageIO.read(file)
                val size = sizes[index]
                assertEquals(size, actualImage.width); assertEquals(size, actualImage.height)
                assertContentEquals(expectedImage.getRGB(0, 0, size, size, null, 0, size), actualImage.getRGB(0, 0, size, size, null, 0, size))
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                report.append("$name\t${densities[index]}\t${size}x$size\t${bytes.size}\t$sha\ttrue\n")
                if (name.startsWith("default-")) {
                    val target = evidence.resolve("$name/${file.relativeTo(actualRoot)}")
                    target.parentFile.mkdirs(); file.copyTo(target, overwrite = true)
                }
            }
            assertTrue(actualRoot.walkTopDown().none { "_resize" in it.name })
        }
        png.copyTo(evidence.resolve(png.name), overwrite = true); jpg.copyTo(evidence.resolve(jpg.name), overwrite = true)
        evidence.resolve("comparison.tsv").writeText(report.toString())
        evidence.resolve("result.txt").writeText("23 configurations, 115 density outputs: encoded bytes, decoded pixels, dimensions and naming equal to frozen 498ef364 pipeline.\nAll four PNG and six JPEG algorithms, lossless/lossy paths, .jpg/.jpeg extensions and default preferences verified.\nNo _resize files remain.\n")
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
