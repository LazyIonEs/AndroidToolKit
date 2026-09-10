package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.icon.*
import org.tool.kit.domain.repository.IconOutputs
import org.tool.kit.domain.repository.ImageProcessor

class GenerateIconsUseCase(private val processor: ImageProcessor, private val outputs: IconOutputs) {
    /**
     * 依次生成五种 Android 密度的图标，先缩放到临时文件，再按格式压缩。
     * 失败时返回此前已完成的路径；取消直接传播，临时文件由输出会话清理。
     */
    suspend operator fun invoke(request: GenerateIconsRequest): GenerateIconsOutcome {
        currentCoroutineContext().ensureActive()
        val suffix = when {
            request.inputPath.endsWith(".png") -> ".png"
            request.inputPath.endsWith(".jpg") -> ".jpg"
            request.inputPath.endsWith(".jpeg") -> ".jpeg"
            else -> return GenerateIconsOutcome.UnsupportedInput
        }
        val completed = mutableListOf<String>()
        val options = request.options
        return try {
            outputs.use(request) { session ->
                for ((density, size) in densities) {
                    currentCoroutineContext().ensureActive()
                    session.density(density, suffix) { files ->
                        if (suffix == ".png") {
                            processor.resizePng(request.inputPath, files.temporaryPath, size, options.pngAlgorithm)
                            currentCoroutineContext().ensureActive()
                            if (options.lossless) {
                                processor.optimizePng(files.temporaryPath, files.outputPath, options.preset)
                            } else {
                                processor.quantizePng(files.temporaryPath, files.outputPath,
                                    options.minimum, options.target, options.speed, options.preset)
                            }
                        } else {
                            processor.resizeJpeg(request.inputPath, files.temporaryPath, size, options.jpegAlgorithm)
                            currentCoroutineContext().ensureActive()
                            processor.compressJpeg(files.temporaryPath, files.outputPath,
                                if (options.lossless) 100f else options.quality)
                        }
                        currentCoroutineContext().ensureActive()
                        // 仅在本密度处理完成且任务仍有效时记入结果，便于失败时报告已完成的文件。
                        completed += files.outputPath
                    }
                }
                GenerateIconsOutcome.Success(session.outputDirectory, completed.toList())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            GenerateIconsOutcome.Failure(completed.toList(), error.message)
        }
    }

    companion object {
        private val densities = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
    }
}
