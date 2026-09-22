package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.data.generator.AndroidJunkGenerator
import org.tool.kit.data.generator.MultiAarGenerator
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkGenerationProgress
import org.tool.kit.data.generator.JunkGeneratedArchive
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class JvmJunkCodeDataSource(
    private val temporaryRoot: File,
    private val io: CoroutineDispatcher,
    private val random: Random = Random.Default,
    private val policy: JunkGenerationPolicy? = null,
    private val onProgress: (JunkGenerationProgress) -> Unit = {},
    private val generateArchive: ((String, String, JunkConfiguration.Single) -> File)? = null,
) : JunkCodeRepository {
    /** 在独立临时根目录中生成单个或批量 AAR，完成后统计实际输出并回收临时文件。 */
    override suspend fun generate(request: GenerateJunkCodeRequest): GeneratedJunkCode = withContext(io) {
        // Shared across instances, covering destructive batch preparation and completed output measurement.
        outputMutex.withLock {
            ensureActive()
            temporaryRoot.mkdirs()
            val context = currentCoroutineContext()
            val defaults = policy ?: JunkGenerationPolicy(seed = random.nextLong())
            val runPolicy = request.seed?.let { defaults.copy(seed = it) } ?: defaults
            fun generate(path: String, output: String, config: JunkConfiguration.Single, selectedPolicy: JunkGenerationPolicy,
                check: () -> Unit = { context.ensureActive() }, logReport: Boolean = true): JunkGeneratedArchive {
                generateArchive?.let { return JunkGeneratedArchive(it(path, output, config)) }
                val generator = AndroidJunkGenerator(path, output, config.appPackageName,
                    config.packageCount, config.activityCount, config.resPrefix, selectedPolicy, check, onProgress, logReport)
                return JunkGeneratedArchive(generator.startGenerate(), generator.lastReport)
            }
            val workspace = Files.createTempDirectory(temporaryRoot.toPath(), "toolkit-junk-").toFile()
            try {
                val archives: List<File>
                val result: File
                when (val config = request.configuration) {
                    is JunkConfiguration.Single -> {
                        result = generate(workspace.path, request.outputPath, config, runPolicy).file
                        archives = listOf(result)
                    }
                    is JunkConfiguration.Multi -> {
                        archives = MultiAarGenerator.generate(workspace.path, request.outputPath, config.outputDir, config.aarCount,
                            config.leastPackages, config.maximumPackages, config.leastActivities, config.maximumActivities, Random(runPolicy.seed), runPolicy) { entry, output ->
                            val workerContext = currentCoroutineContext()
                            // Distinct package names can still collide after removing dots. Each worker owns a private root.
                            val worker = Files.createTempDirectory(workspace.toPath(), "aar-").toFile()
                            generate(worker.path, output.absolutePath,
                                JunkConfiguration.Single(entry.packageName, entry.packageCount, entry.activityCount, entry.resPrefix), entry.policy,
                                { workerContext.ensureActive() }, logReport = false)
                        }
                        result = File(request.outputPath, config.outputDir)
                    }
                }
                ensureActive()
                val size = archives.sumOf { it.length() }
                GeneratedJunkCode(result.path, archives.map { it.path }, size)
            } finally {
                // Synchronous generators and their structured batch children have returned before cleanup starts.
                withContext(NonCancellable) { check(workspace.deleteRecursively()) { "Cannot clean junk workspace: ${workspace.path}" } }
            }
        }
    }

    companion object { private val outputMutex = Mutex() }
}
