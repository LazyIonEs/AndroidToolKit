package org.tool.kit.data.generator

import java.io.File
import java.lang.management.ManagementFactory
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

/** Reproducible engineering entry point; does not add controls to the desktop form. */
object JunkValidationCli {
    private val logger = KotlinLogging.logger("JunkGeneration")
    private val reportJson = Json { prettyPrint = true }
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        require(args.size >= 4) { "Usage: outputDirectory packageCount activitiesPerPackage seed [aarCount]" }
        val output = File(args[0]).absoluteFile.apply { mkdirs() }
        val packages = args[1].toInt(); val activities = args[2].toInt(); val seed = args[3].toLong()
        val count = args.getOrNull(4)?.toInt() ?: 1
        val work = java.nio.file.Files.createTempDirectory(output.parentFile.toPath(), ".junk-validation-work-").toFile()
        val policy = JunkGenerationPolicy(seed = seed)
        val started = System.nanoTime()
        try {
            val files = if (count == 1) listOf(AndroidJunkGenerator(work.path, output.path, "org.tool.kit.sample.s${java.lang.Long.toUnsignedString(seed, 36)}",
                packages, activities, "sample_", policy).startGenerate())
            else MultiAarGenerator.generate(work.path, output.path, "batch", count, packages, packages, activities, activities, Random(seed), policy)
            val peaks = ManagementFactory.getMemoryPoolMXBeans().filter { it.type == java.lang.management.MemoryType.HEAP }.sumOf { it.peakUsage.used }
            val run = linkedMapOf("seed" to seed, "archives" to files.map { it.path }, "archiveBytes" to files.sumOf { it.length() },
                "wallMs" to (System.nanoTime() - started) / 1_000_000, "sumHeapPoolPeakBytes" to peaks,
                "heapNote" to "Sum of per-pool JVM high-water marks, not an OS RSS peak; pools may peak at different times")
            logger.info {
                "AAR 验证生成完成：${files.size} 份归档，合计 ${run["archiveBytes"]} 字节，耗时 ${run["wallMs"]} 毫秒。\n" +
                    "JVM 内存值为各堆内存池峰值之和，不等同于进程峰值 RSS；运行统计仅记录到日志。\n" +
                    reportJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), Json.parseToJsonElement(jsonValue(run)))
            }
        } finally { work.deleteRecursively() }
    }
}
