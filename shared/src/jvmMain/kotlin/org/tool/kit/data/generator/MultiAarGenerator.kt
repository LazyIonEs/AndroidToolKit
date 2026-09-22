package org.tool.kit.data.generator


import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.io.File
import kotlin.random.Random

/**
 * 多 AAR 批量生成器
 */
object MultiAarGenerator {
    private val logger = KotlinLogging.logger("JunkGeneration")

    // 预分配字符池，避免在大量生成随机字符串时重复创建集合引发内存抖动（Memory Churn）
    private val CHAR_POOL = "abcdefghijklmnopqrstuvwxyz".toCharArray()

    // 外层最多同时生成两个 AAR；每个 AAR 再分配有上限的独立工作线程，控制 IO 和内存竞争。
    private const val MAX_CONCURRENT_TASKS = 2

    /**
     * 批量生成多个 AAR 文件
     *
     * @param resourcesDir 工作空间目录
     * @param outputPath 输出基础目录
     * @param outputDir 输出文件夹名称
     * @param aarCount 需要生成的 AAR 总数
     * @param leastPackageCount 每个 AAR 内的最少包数量
     * @param maximumPackageCount 每个 AAR 内的最多包数量
     * @param leastActivityCount 每个包内的最少 Activity 数量
     * @param maximumActivityCount 每个包内的最多 Activity 数量
     * 整批成功后原子替换 outputDir；失败保留上次产物，调用方需保证目标目录由本任务独占。
     * @param random 配置随机源，测试可提供固定种子
     * @param onReport 整批成功发布后提供内存汇总；报告默认记录到日志，不另建文件。
     * 通知回调的普通异常只记录警告，不撤销已发布产物；取消异常与 JVM Error 仍向调用方传播。
     * @param generateArchive 可替换的单个归档生成入口，默认使用 AndroidJunkGenerator
     * @return 按预先生成的配置顺序排列的 AAR 文件列表
     */
    suspend fun generate(
        resourcesDir: String,
        outputPath: String,
        outputDir: String,
        aarCount: Int,
        leastPackageCount: Int,
        maximumPackageCount: Int,
        leastActivityCount: Int,
        maximumActivityCount: Int,
        random: Random? = null,
        policy: JunkGenerationPolicy = JunkGenerationPolicy(),
        onReport: (JunkGenerationReport) -> Unit = {},
        generateArchive: (suspend (AarConfig, File) -> JunkGeneratedArchive)? = null,
    ): List<File> = withContext(Dispatchers.IO) {
        
        val context = currentCoroutineContext()
        context.ensureActive()
        val started = System.nanoTime()
        require(aarCount in 1..256) { "AAR count must be in 1..256" }
        val concurrentAars = minOf(aarCount, MAX_CONCURRENT_TASKS, policy.maxParallelism)
        // Validate before touching the existing output. Staging is owned by this batch only.
        val base = File(outputPath).apply { mkdirs() }.canonicalFile
        val outputFolder = File(base, outputDir).canonicalFile
        require(outputFolder != base && outputFolder.toPath().startsWith(base.toPath())) { "Batch output must be a child of the output directory" }
        val configs = configurations(aarCount, leastPackageCount, maximumPackageCount, leastActivityCount, maximumActivityCount, random ?: Random(policy.seed)) { context.ensureActive() }
            .mapIndexed { index, config ->
                val budget = policy.batchResourceTotal / aarCount + if (index < policy.batchResourceTotal % aarCount) 1 else 0
                config.copy(policy = policy.copy(seed = policy.seedFor(index, 11),
                    resourceNamespace = policy.resourceNamespace?.let { "$it.module${index}_${java.lang.Long.toUnsignedString(policy.seedFor(index, 12), 36)}" },
                    maxParallelism = maxOf(1, policy.maxParallelism / concurrentAars),
                    resources = policy.resources.copy(total = minOf(policy.resources.total, budget))))
            }
        val staging = Files.createTempDirectory(base.toPath(), ".junk-batch-").toFile()
        try {
            val semaphore = Semaphore(concurrentAars)
            val generated = coroutineScope {
                configs.map { config -> async {
                    semaphore.withPermit {
                        val workerContext = currentCoroutineContext()
                        workerContext.ensureActive()
                        if (generateArchive != null) generateArchive(config, staging)
                        else {
                            val generator = AndroidJunkGenerator(resourcesDir, staging.path, config.packageName, config.packageCount,
                                config.activityCount, config.resPrefix, config.policy, { workerContext.ensureActive() }, logReport = false)
                            JunkGeneratedArchive(generator.startGenerate(), generator.lastReport)
                        }
                    }
                } }.awaitAll()
            }
            context.ensureActive()
            val files = generated.map { it.file }
            val report = JunkGenerationReport(linkedMapOf(
                "seed" to policy.seed, "aarCount" to files.size, "archiveBytes" to files.sumOf { it.length() },
                "optionalResourceBudget" to policy.batchResourceTotal,
                "aggregate" to aggregateReports(generated),
                "archives" to files.mapIndexed { i, file -> linkedMapOf("file" to file.name,
                    "bytes" to file.length(), "seed" to configs[i].policy.seed,
                    "optionalResourceLimit" to configs[i].policy.resources.total) }))
            // Replace the previous batch only after every worker has exited successfully.
            outputFolder.parentFile.mkdirs()
            val backup = File(base, ".junk-backup-${staging.name}")
            val hadPrevious = outputFolder.exists()
            if (hadPrevious) Files.move(outputFolder.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
            try {
                Files.move(staging.toPath(), outputFolder.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (error: Throwable) {
                if (hadPrevious) Files.move(backup.toPath(), outputFolder.toPath(), StandardCopyOption.ATOMIC_MOVE)
                throw error
            }
            if (hadPrevious) check(backup.deleteRecursively()) { "Cannot remove previous batch backup: $backup" }
            val published = generated.map { it.copy(file = File(outputFolder, it.file.name)) }
            val completedReport = report.copy(values = report.values + ("totalMs" to (System.nanoTime() - started) / 1_000_000))
            published.forEach { archive -> archive.report?.let { logJunkArchiveReport(archive.file, it) } }
            logJunkBatchReport(outputFolder, completedReport)
            try {
                onReport(completedReport)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.warn(error) { "AAR 批次已成功发布，统计通知失败；已发布产物保持有效。输出目录：${outputFolder.absolutePath}" }
            }
            published.map { it.file }
        } finally {
            // coroutineScope above joins cancelled/failed synchronous workers before cleanup.
            check(staging.deleteRecursively()) { "Cannot clean batch staging: $staging" }
        }
    }

    /** Aggregate in-memory measurements; custom callbacks can explicitly omit unavailable statistics. */
    private fun aggregateReports(archives: List<JunkGeneratedArchive>): Map<String, Any> {
        val reports = archives.mapNotNull { it.report?.let { report -> Json.parseToJsonElement(report.toJson()).jsonObject } }
        fun JsonObject.number(key: String): Long = (this[key] as? JsonPrimitive)?.longOrNull ?: 0L
        fun section(report: JsonObject, name: String): JsonObject = report[name] as? JsonObject ?: JsonObject(emptyMap())
        fun totals(name: String, fields: List<String>): Map<String, Long> = fields.associateWith { field ->
            reports.sumOf { section(it, name).number(field) }
        }
        fun nestedTotals(name: String, field: String): Map<String, Long> {
            val result = sortedMapOf<String, Long>()
            reports.forEach { report ->
                (section(report, name)[field] as? JsonObject)?.forEach { (type, value) ->
                    result[type] = (result[type] ?: 0L) + ((value as? JsonPrimitive)?.longOrNull ?: 0L)
                }
            }
            return result
        }
        val generated = nestedTotals("resources", "generated")
        val requests = nestedTotals("resources", "requests")
        val reused = nestedTotals("resources", "reused")
        fun rate(numerator: Long, denominator: Long): Double = if (denominator == 0L) 0.0 else numerator.toDouble() / denominator
        val code = totals("code", listOf("classes", "methods", "fields", "classBytes"))
        val layouts = totals("layouts", listOf("count", "bytes"))
        val resources = totals("resources", listOf("optionalFiles", "optionalBytes", "valuesEntries", "ids", "assets", "assetBytes", "metadataResourceFiles"))
        val resourceBytes = resources.getValue("optionalBytes") + resources.getValue("assetBytes") + layouts.getValue("bytes")
        return linkedMapOf(
            "measuredAars" to reports.size,
            "unmeasuredAars" to archives.size - reports.size,
            "code" to (code + ("classRoles" to nestedTotals("code", "classRoles"))),
            "layouts" to layouts,
            "resources" to (resources + linkedMapOf(
                "generated" to generated, "requests" to requests, "reused" to reused,
                "optionalGeneratedCount" to generated.values.sum(),
                // Rates use summed requests, never the unweighted average of per-AAR rates.
                "requestReuseRate" to rate(reused.values.sum(), requests.values.sum()),
                "successfulRequestReuseRate" to rate(reused.values.sum(), reused.values.sum() + generated.values.sum()),
                "requestReuseRateByType" to requests.mapValues { (type, count) -> rate(reused[type] ?: 0L, count) },
            )),
            "sizes" to (totals("sizes", listOf("aarBytes", "classesJarBytes", "classBytes", "layoutBytes")) + linkedMapOf(
                "resourceBytes" to resourceBytes,
                "compressedEntryBytes" to nestedTotals("sizes", "compressedEntryBytes"),
                "classShareOfClassAndResourceBytes" to rate(code.getValue("classBytes"), code.getValue("classBytes") + resourceBytes),
            )),
            "dexOrApkMeasured" to false,
        )
    }

    /**
     * 在单线程内生成本批次配置并去重包名、资源前缀；随机源可注入以重现结果。
     * 数量范围两端都可取到，最小值不小于最大值时使用最小值。
     */
    internal fun configurations(aarCount: Int, leastPackageCount: Int, maximumPackageCount: Int,
        leastActivityCount: Int, maximumActivityCount: Int, random: Random = Random.Default,
        checkActive: () -> Unit = {}): List<AarConfig> {
        // 使用 Set 进行查重，保证本次批量生成的包名和资源前缀绝对不重复
        val generatedPackages = mutableSetOf<String>()
        val generatedPrefixes = mutableSetOf<String>()

        // 提前生成本次任务的所有配置，确保查重逻辑在单线程内安全完成
        return (0 until aarCount).map {
            checkActive()
            // 2. 包名生成规则：com.xxx.xxx (3-8位小写字母)，并加入查重逻辑
            var packageName: String
            do {
                checkActive()
                val part1 = generateRandomLowercaseString(3, 8, random)
                val part2 = generateRandomLowercaseString(3, 8, random)
                packageName = "com.$part1.$part2"
            } while (!generatedPackages.add(packageName))

            // 3. 资源前缀规则：xxx_ (3-8位小写字母)，并加入查重逻辑
            var resPrefix: String
            do {
                checkActive()
                resPrefix = generateRandomLowercaseString(3, 8, random) + "_"
            } while (!generatedPrefixes.add(resPrefix))

            // 4. 随机包数量：在 leastPackageCount 到 maximumPackageCount 之间随机
            val packageCount = if (leastPackageCount < maximumPackageCount) {
                random.nextInt(leastPackageCount, Math.addExact(maximumPackageCount, 1))
            } else {
                leastPackageCount
            }

            // 5. 随机 Activity 数量：在 leastActivityCount 到 maximumActivityCount 之间随机
            val activityCount = if (leastActivityCount < maximumActivityCount) {
                random.nextInt(leastActivityCount, Math.addExact(maximumActivityCount, 1))
            } else {
                leastActivityCount
            }

            AarConfig(packageName, resPrefix, packageCount, activityCount)
        }

    }

    /**
     * 生成指定长度范围内的纯小写随机字符串（通过预置字符池提升性能）
     */
    private fun generateRandomLowercaseString(minLength: Int, maxLength: Int, random: Random): String {
        val length = random.nextInt(minLength, maxLength + 1)
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = CHAR_POOL[random.nextInt(CHAR_POOL.size)]
        }
        return String(chars)
    }

    /**
     * 内部配置类，用于临时存储每个 AAR 的生成参数
     */
    data class AarConfig(
        val packageName: String,
        val resPrefix: String,
        val packageCount: Int,
        val activityCount: Int,
        val policy: JunkGenerationPolicy = JunkGenerationPolicy(seed = 0)
    )
}
