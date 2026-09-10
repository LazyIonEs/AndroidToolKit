package org.tool.kit.data.source

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.data.generator.AndroidJunkGenerator
import org.tool.kit.data.generator.MultiAarGenerator
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

class JvmJunkCodeDataSource(
    private val temporaryRoot: File,
    private val io: CoroutineDispatcher,
    private val random: Random = Random.Default,
    private val generateArchive: (String, String, JunkConfiguration.Single) -> File = { workspace, output, config ->
        AndroidJunkGenerator(workspace, output, config.appPackageName, config.packageCount, config.activityCount, config.resPrefix).startGenerate()
    },
) : JunkCodeRepository {
    override suspend fun generate(request: GenerateJunkCodeRequest): GeneratedJunkCode = withContext(io) {
        // Shared across instances, covering destructive batch preparation and completed output measurement.
        outputMutex.withLock {
            ensureActive()
            val workspace = Files.createTempDirectory(temporaryRoot.toPath(), "toolkit-junk-").toFile()
            try {
                val archives: List<File>
                val result: File
                when (val config = request.configuration) {
                    is JunkConfiguration.Single -> {
                        result = generateArchive(workspace.path, request.outputPath, config)
                        archives = listOf(result)
                    }
                    is JunkConfiguration.Multi -> {
                        archives = MultiAarGenerator.generate(workspace.path, request.outputPath, config.outputDir, config.aarCount,
                            config.leastPackages, config.maximumPackages, config.leastActivities, config.maximumActivities, random) { entry, output ->
                            // Distinct package names can still collide after removing dots. Each worker owns a private root.
                            val worker = Files.createTempDirectory(workspace.toPath(), "aar-").toFile()
                            generateArchive(worker.path, output.absolutePath,
                                JunkConfiguration.Single(entry.packageName, entry.packageCount, entry.activityCount, entry.resPrefix))
                        }
                        result = File(request.outputPath, config.outputDir)
                    }
                }
                ensureActive()
                val size = if (result.isDirectory) result.walkBottomUp().filter { it.isFile }.sumOf { it.length() } else result.length()
                GeneratedJunkCode(result.path, archives.map { it.path }, size)
            } finally {
                // Synchronous generators and their structured batch children have returned before cleanup starts.
                withContext(NonCancellable) { check(workspace.deleteRecursively()) { "Cannot clean junk workspace: ${workspace.path}" } }
            }
        }
    }

    companion object { private val outputMutex = Mutex() }
}
