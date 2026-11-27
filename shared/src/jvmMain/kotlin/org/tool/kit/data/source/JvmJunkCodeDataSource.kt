package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.data.generator.AndroidJunkGenerator
import org.tool.kit.data.generator.MultiAarGenerator
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkGenerationProfile
import org.tool.kit.data.generator.JunkGenerationProgress
import org.tool.kit.data.generator.JunkGeneratedArchive
import org.tool.kit.data.generator.generateJunkAarInRange
import org.tool.kit.data.generator.logJunkArchiveReport
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
            val strategy = request.strategy
            strategy?.let { JunkGenerationProfile.requireSupportedVersion(it.generatorVersion) }
            temporaryRoot.mkdirs()
            val context = currentCoroutineContext()
            val defaults = policy ?: JunkGenerationPolicy(seed = random.nextLong())
            val seeded = request.seed?.let { defaults.copy(seed = it) } ?: defaults
            val runPolicy = if (strategy == null) seeded else seeded.copy(includeDtoStringAdapters = false, includeDtoComparison = false)
            fun singlePolicy(): JunkGenerationPolicy = strategy?.let {
                val id = Random(runPolicy.seed).nextInt(JunkGenerationProfile.CAPACITY)
                runPolicy.copy(profile = JunkGenerationProfile(id, it.generatorVersion))
            } ?: runPolicy
            fun generate(path: String, output: String, config: JunkConfiguration.Single, selectedPolicy: JunkGenerationPolicy,
                check: () -> Unit = { context.ensureActive() }, logReport: Boolean = true): JunkGeneratedArchive {
                generateArchive?.let { return JunkGeneratedArchive(it(path, output, config)) }
                val generator = AndroidJunkGenerator(path, output, config.appPackageName,
                    config.packageCount, config.activityCount, config.resPrefix, selectedPolicy, check, onProgress, logReport)
                return JunkGeneratedArchive(generator.startGenerate(), generator.lastReport)
            }
            val workspace = Files.createTempDirectory(temporaryRoot.toPath(), "toolkit-junk-").toFile()
            try {
                suspend fun generateBatch(
                    outputDir: String, aarCount: Int, packageCounts: IntRange = 1..1,
                    activityCounts: IntRange = 1..1, size: JunkAarSizeRange? = null,
                ): Pair<File, List<File>> {
                    val archives = MultiAarGenerator.generate(workspace.path, request.outputPath, outputDir, aarCount,
                        packageCounts.first, packageCounts.last, activityCounts.first, activityCounts.last,
                        Random(runPolicy.seed), runPolicy, profileVersion = strategy?.generatorVersion) { entry, output ->
                        val workerContext = currentCoroutineContext()
                        val check = { workerContext.ensureActive() }
                        val worker = Files.createTempDirectory(workspace.toPath(), "aar-").toFile()
                        val config = JunkConfiguration.Single(entry.packageName, entry.packageCount, entry.activityCount, entry.resPrefix)
                        if (size == null) {
                            generate(worker.path, output.absolutePath, config, entry.policy, check, logReport = false)
                        } else {
                            generateJunkAarInRange(output, size, entry.policy, check) { activities, stage ->
                                generate(worker.path, stage.path, config.copy(packageCount = activities, activityCount = 1),
                                    entry.policy, check, logReport = false)
                            }.archive
                        }
                    }
                    return File(request.outputPath, outputDir) to archives
                }
                val (result, archives) = when (val config = request.configuration) {
                    is JunkConfiguration.Single -> {
                        val file = generate(workspace.path, request.outputPath, config, singlePolicy()).file
                        file to listOf(file)
                    }
                    is JunkConfiguration.SingleBySize -> {
                        val selectedPolicy = singlePolicy()
                        val generated = generateJunkAarInRange(File(request.outputPath), config.size, selectedPolicy, { context.ensureActive() }) { activities, stage ->
                            generate(workspace.path, stage.path, JunkConfiguration.Single(config.appPackageName, activities, 1, config.resPrefix),
                                selectedPolicy, logReport = false)
                        }.archive
                        generated.report?.let { logJunkArchiveReport(generated.file, it) }
                        generated.file to listOf(generated.file)
                    }
                    is JunkConfiguration.Multi -> generateBatch(config.outputDir, config.aarCount,
                        config.leastPackages..config.maximumPackages, config.leastActivities..config.maximumActivities)
                    is JunkConfiguration.MultiBySize -> generateBatch(config.outputDir, config.aarCount, size = config.size)
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
