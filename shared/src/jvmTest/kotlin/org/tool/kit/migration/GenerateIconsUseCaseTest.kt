package org.tool.kit.migration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.GenerateIconsUseCase
import kotlin.test.*

class GenerateIconsUseCaseTest {
    private val options = IconProcessingOptions(1, 4, true, 17, 83, 7, 2, 43f)
    private fun request(extension: String = "png", lossless: Boolean = true) = GenerateIconsRequest(
        "输入 icon.$extension", "output path", " res ", "drawable", "中文 icon", options.copy(lossless = lossless))
    private val densities = listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")
    private val sizes = listOf(48, 72, 96, 144, 192)
    private data class Call(val kind: String, val input: String, val output: String, val parameters: List<Number>)

    private class Pipeline(val failAt: Int? = null) : ImageProcessor, IconOutputs {
        val calls = mutableListOf<Call>()
        val cleaned = mutableListOf<String>()
        var request: GenerateIconsRequest? = null
        var hook: suspend () -> Unit = {}
        private suspend fun call(kind: String, input: String, output: String, vararg parameters: Number) {
            calls += Call(kind, input, output, parameters.toList())
            hook()
            if (calls.size == failAt) error("native failure")
        }
        override suspend fun resizePng(inputPath: String, outputPath: String, size: Int, algorithm: Int) = call("resizePng", inputPath, outputPath, size, algorithm)
        override suspend fun resizeJpeg(inputPath: String, outputPath: String, size: Int, algorithm: Int) = call("resizeFir", inputPath, outputPath, size, algorithm)
        override suspend fun optimizePng(inputPath: String, outputPath: String, preset: Int) = call("oxipng", inputPath, outputPath, preset)
        override suspend fun quantizePng(inputPath: String, outputPath: String, minimum: Int, target: Int, speed: Int, preset: Int) = call("quantize", inputPath, outputPath, minimum, target, speed, preset)
        override suspend fun compressJpeg(inputPath: String, outputPath: String, quality: Float) = call("mozJpeg", inputPath, outputPath, quality)
        override suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T {
            this.request = request
            return block(object : IconOutputSession {
                override val outputDirectory = "${request.outputPath}/${request.fileDir}"
                override suspend fun <R> density(name: String, suffix: String, block: suspend (IconOutputFiles) -> R): R {
                    val base = "$outputDirectory/${request.iconDir}-$name/${request.iconName}"
                    return try { block(IconOutputFiles("$base$suffix", "${base}_resize$suffix")) }
                    finally { cleaned += name }
                }
            })
        }
    }

    @Test fun pngPipelinePreservesEveryDensityParameterAndCompressionBranch() = runTest {
        for (lossless in listOf(true, false)) {
            val pipeline = Pipeline()
            val request = request(lossless = lossless)
            val result = GenerateIconsUseCase(pipeline, pipeline)(request) as GenerateIconsOutcome.Success
            assertEquals(request, pipeline.request)
            assertEquals("output path/ res ", result.outputDirectory)
            assertEquals(densities, pipeline.cleaned)
            val expected = densities.flatMapIndexed { index, density ->
                val base = "output path/ res /drawable-$density/中文 icon"
                listOf(Call("resizePng", "输入 icon.png", "${base}_resize.png", listOf(sizes[index], 1)),
                    if (lossless) Call("oxipng", "${base}_resize.png", "$base.png", listOf(2))
                    else Call("quantize", "${base}_resize.png", "$base.png", listOf(17, 83, 7, 2)))
            }
            assertEquals(expected, pipeline.calls)
            assertEquals(expected.filterIndexed { index, _ -> index % 2 == 1 }.map { it.output }, result.outputPaths)
        }
    }

    @Test fun jpegKeepsExtensionAndUsesQuality100OnlyForLossless() = runTest {
        for (extension in listOf("jpg", "jpeg")) for (lossless in listOf(true, false)) {
            val pipeline = Pipeline()
            val result = GenerateIconsUseCase(pipeline, pipeline)(request(extension, lossless)) as GenerateIconsOutcome.Success
            val expected = densities.flatMapIndexed { index, density ->
                val base = "output path/ res /drawable-$density/中文 icon"
                listOf(Call("resizeFir", "输入 icon.$extension", "${base}_resize.$extension", listOf(sizes[index], 4)),
                    Call("mozJpeg", "${base}_resize.$extension", "$base.$extension", listOf(if (lossless) 100f else 43f)))
            }
            assertEquals(expected, pipeline.calls)
            assertEquals(expected.filterIndexed { index, _ -> index % 2 == 1 }.map { it.output }, result.outputPaths)
        }
    }

    @Test fun everyNativeFailureStopsImmediatelyAndRetainsOnlyCompletedDensities() = runTest {
        for ((extension, lossless) in listOf("png" to true, "png" to false, "jpeg" to false)) {
            for (position in 1..10) {
                val pipeline = Pipeline(position)
                val result = GenerateIconsUseCase(pipeline, pipeline)(request(extension, lossless)) as GenerateIconsOutcome.Failure
                assertEquals("native failure", result.message)
                assertEquals(position, pipeline.calls.size)
                assertEquals((position - 1) / 2, result.outputPaths.size)
                assertEquals(densities.take((position + 1) / 2), pipeline.cleaned)
            }
        }
    }

    @Test fun unsupportedExtensionsStaySilentWithoutPreparingOutputs() = runTest {
        for (extension in listOf("PNG", "JPG", "JPEG", "webp", "png ", "")) {
            val pipeline = Pipeline()
            assertEquals(GenerateIconsOutcome.UnsupportedInput, GenerateIconsUseCase(pipeline, pipeline)(request(extension)))
            assertNull(pipeline.request); assertTrue(pipeline.calls.isEmpty())
        }
    }

    @Test fun preparationErrorsKeepNullMessagesAndCancellationDoesNotBecomeFailure() = runTest {
        val outputs = object : IconOutputs {
            override suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T = throw IllegalStateException()
        }
        assertEquals(GenerateIconsOutcome.Failure(emptyList(), null), GenerateIconsUseCase(UnusedImageProcessor, outputs)(request()))
        val pipeline = Pipeline()
        val entered = CompletableDeferred<Unit>()
        pipeline.hook = { entered.complete(Unit); awaitCancellation() }
        val job = launch {
            GenerateIconsUseCase(pipeline, pipeline)(request())
            fail("Cancellation cannot return a result")
        }
        entered.await(); job.cancelAndJoin()
        assertEquals(1, pipeline.calls.size)
        assertEquals(listOf("mdpi"), pipeline.cleaned)
    }
}
