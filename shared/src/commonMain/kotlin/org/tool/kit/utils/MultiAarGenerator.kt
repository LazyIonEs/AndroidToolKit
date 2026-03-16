package org.tool.kit.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/**
 * 多 AAR 批量生成器
 */
object MultiAarGenerator {

    // 预分配字符池，避免在大量生成随机字符串时重复创建集合引发内存抖动（Memory Churn）
    private val CHAR_POOL = "abcdefghijklmnopqrstuvwxyz".toCharArray()

    // 限制最外层的最大并发数。
    // 因为 AndroidJunkGenerator 内部已经使用了 IntStream.parallel() 进行多线程计算和 IO，
    // 如果大批量（如 50 个） AAR 完全并发执行，会导致严重的线程争用（Thread Contention）、
    // GC 压力以及由于过高的磁盘 IO 导致的系统瓶颈。
    // 根据可用 CPU 核心数动态调整最大并发（一般 2 到 6 之间最佳）将大幅提升总吞吐量。
    private val MAX_CONCURRENT_TASKS = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)

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
     * @return 生成的 AAR 文件列表
     */
    suspend fun generate(
        resourcesDir: String,
        outputPath: String,
        outputDir: String,
        aarCount: Int,
        leastPackageCount: Int,
        maximumPackageCount: Int,
        leastActivityCount: Int,
        maximumActivityCount: Int
    ): List<File> = withContext(Dispatchers.IO) {
        
        // 1. 输出路径管理：拼接输出基础目录和文件夹，并确保其存在
        val outputFolder = File(outputPath, outputDir)
        if (!outputFolder.exists()) {
            outputFolder.mkdirs()
        }

        // 使用 Set 进行查重，保证本次批量生成的包名和资源前缀绝对不重复
        val generatedPackages = mutableSetOf<String>()
        val generatedPrefixes = mutableSetOf<String>()

        // 提前生成本次任务的所有配置，确保查重逻辑在单线程内安全完成
        val configs = (0 until aarCount).map {
            // 2. 包名生成规则：com.xxx.xxx (3-8位小写字母)，并加入查重逻辑
            var packageName: String
            do {
                val part1 = generateRandomLowercaseString(3, 8)
                val part2 = generateRandomLowercaseString(3, 8)
                packageName = "com.$part1.$part2"
            } while (!generatedPackages.add(packageName))

            // 3. 资源前缀规则：xxx_ (3-8位小写字母)，并加入查重逻辑
            var resPrefix: String
            do {
                resPrefix = generateRandomLowercaseString(3, 8) + "_"
            } while (!generatedPrefixes.add(resPrefix))

            // 4. 随机包数量：在 leastPackageCount 到 maximumPackageCount 之间随机
            val packageCount = if (leastPackageCount < maximumPackageCount) {
                Random.nextInt(leastPackageCount, maximumPackageCount + 1)
            } else {
                leastPackageCount
            }

            // 5. 随机 Activity 数量：在 leastActivityCount 到 maximumActivityCount 之间随机
            val activityCount = if (leastActivityCount < maximumActivityCount) {
                Random.nextInt(leastActivityCount, maximumActivityCount + 1)
            } else {
                leastActivityCount
            }

            AarConfig(packageName, resPrefix, packageCount, activityCount)
        }

        // 使用信号量（Semaphore）控制并发任务数，防止内存爆炸和 CPU 过载
        val semaphore = Semaphore(MAX_CONCURRENT_TASKS)

        // 6. 协程并发执行：避免阻塞 UI 线程，并在受控的并发范围内极速生成所有的 AAR 文件
        val deferredFiles = configs.map { config ->
            async {
                semaphore.withPermit {
                    val generator = AndroidJunkGenerator(
                        dir = resourcesDir,
                        output = outputFolder.absolutePath,
                        appPackageName = config.packageName,
                        packageCount = config.packageCount,
                        activityCountPerPackage = config.activityCount,
                        resPrefix = config.resPrefix
                    )
                    // 调用已有的 AndroidJunkGenerator 执行生成
                    generator.startGenerate()
                }
            }
        }

        // 等待所有 AAR 生成完毕并返回结果
        deferredFiles.awaitAll()
    }

    /**
     * 生成指定长度范围内的纯小写随机字符串（通过预置字符池提升性能）
     */
    private fun generateRandomLowercaseString(minLength: Int, maxLength: Int): String {
        val length = Random.nextInt(minLength, maxLength + 1)
        val chars = CharArray(length)
        for (i in 0 until length) {
            chars[i] = CHAR_POOL[Random.nextInt(CHAR_POOL.size)]
        }
        return String(chars)
    }

    /**
     * 内部配置类，用于临时存储每个 AAR 的生成参数
     */
    private data class AarConfig(
        val packageName: String,
        val resPrefix: String,
        val packageCount: Int,
        val activityCount: Int
    )
}
