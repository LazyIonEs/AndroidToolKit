package org.tool.kit.data.generator

import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.random.Random
import org.tool.kit.domain.junk.JunkAarSizeRange

private val projectIdPattern = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")

@Serializable
internal data class JunkProject(
    val projectId: String,
    val packageName: String,
    val packageCount: Int = 0,
    val activitiesPerPackage: Int = 0,
    val resourcePrefix: String = "",
    val profileId: Int? = null,
    val seed: String? = null,
    val minimumSizeMb: JsonPrimitive? = null,
    val maximumSizeMb: JsonPrimitive? = null,
    val includeDtoStringAdapters: Boolean? = null,
    val includeDtoComparison: Boolean? = null,
) {
    fun sizeRange(): JunkAarSizeRange? {
        if (minimumSizeMb == null && maximumSizeMb == null) return null
        return requireNotNull(JunkAarSizeRange.fromMegabytes(minimumSizeMb?.content.orEmpty(), maximumSizeMb?.content.orEmpty())) {
            "项目 $projectId 的 minimumSizeMb 和 maximumSizeMb 必须构成有效的 AAR 大小范围。"
        }
    }
}

@Serializable
internal data class JunkProjectManifest(
    val projects: List<JunkProject>,
    val generatorVersion: Int = JunkGenerationProfile.GENERATOR_VERSION,
) {
    fun resolve(): JunkProjectManifest {
        JunkGenerationProfile.requireSupportedVersion(generatorVersion)
        require(projects.size in 1..JunkGenerationProfile.CAPACITY) {
            "Expected 1..${JunkGenerationProfile.CAPACITY} projects, got ${projects.size}"
        }
        val projectIds = mutableSetOf<String>()
        val assignedProfiles = mutableSetOf<Int>()
        for (project in projects) {
            require(projectIdPattern.matches(project.projectId)) { "Invalid project ID: ${project.projectId}" }
            require(projectIds.add(project.projectId.lowercase())) { "Duplicate project ID: ${project.projectId}" }
            project.profileId?.let { id ->
                JunkGenerationProfile(id, generatorVersion)
                require(assignedProfiles.add(id)) { "Profile $id is assigned to more than one project" }
            }
            require(project.seed == null || project.seed.toLongOrNull() != null) {
                "Seed for ${project.projectId} must be a signed 64-bit integer string"
            }
            project.sizeRange()
        }

        // Mix the profile order so even a partial allocation uses all five structural dimensions.
        val available = (0 until JunkGenerationProfile.CAPACITY).shuffled(Random(generatorVersion))
            .filterNot(assignedProfiles::contains).iterator()
        val resolved = projects.sortedBy { it.projectId }.map { project ->
            val seed = project.seed?.toLong()
                ?: fingerprint("$generatorVersion:${project.projectId}").take(16).toULong(16).toLong()
            project.copy(profileId = project.profileId ?: available.next(), seed = seed.toString())
        }
        return copy(projects = resolved)
    }
}

internal data class JunkProjectArchive(val project: JunkProject, val file: File, val activities: Int)

internal data class JunkProjectGeneration(
    val manifest: File,
    val archives: List<JunkProjectArchive>,
    val comparison: File?,
)

internal object JunkProjectGenerator {
    private val json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    fun generate(
        manifest: File,
        output: File,
        projectId: String? = null,
        compare: Boolean = false,
        generatorVersion: Int? = null,
    ): JunkProjectGeneration {
        require(!compare || projectId == null) { "Pairwise comparison requires the full project list" }
        generatorVersion?.let { JunkGenerationProfile.requireSupportedVersion(it) }
        Files.createDirectories(output.toPath())
        val lockFile = output.resolve(".project-aars.lock").toPath()
        FileChannel.open(lockFile, CREATE, WRITE).use { channel ->
            val lock = requireNotNull(channel.tryLock()) { "Another generation is using $output" }
            lock.use {
                val original = json.decodeFromString<JunkProjectManifest>(manifest.readText()).resolve()
                val plan = if (generatorVersion == null) original else original.copy(generatorVersion = generatorVersion)
                val selected = if (projectId == null) plan.projects else listOf(
                    requireNotNull(plan.projects.find { it.projectId == projectId }) { "Unknown project ID: $projectId" }
                )
                val work = Files.createTempDirectory(output.toPath(), ".project-aar-work-").toFile()
                try {
                    val archives = selected.map { project -> generateArchive(work, project, plan.generatorVersion) }
                    val generatedProjects = archives.associate { it.project.projectId to it.project }
                    val resolved = plan.copy(projects = plan.projects.map { generatedProjects[it.projectId] ?: it })
                    val manifestFile = work.resolve("projects.resolved.json").apply { writeText(json.encodeToString(resolved) + "\n") }
                    val comparison = if (compare) work.resolve("similarity.json").apply {
                        writeText(JunkAarComparison.compare(archives, work).toJson())
                    } else null

                    fun published(file: File) = output.resolve(file.relativeTo(work))
                    val files = archives.map { it.file } + listOfNotNull(manifestFile, comparison)
                    publishJunkFiles(files.map { file -> file to published(file).also { it.parentFile.mkdirs() } })
                    return JunkProjectGeneration(published(manifestFile), archives.map { it.copy(file = published(it.file)) },
                        comparison?.let(::published))
                } finally {
                    check(work.deleteRecursively()) { "Cannot clean project generation workspace: $work" }
                }
            }
        }
    }

    private fun generateArchive(work: File, project: JunkProject, version: Int): JunkProjectArchive {
        val policy = JunkGenerationPolicy(seed = requireNotNull(project.seed).toLong(),
            profile = JunkGenerationProfile(requireNotNull(project.profileId), version),
            includeDtoStringAdapters = project.includeDtoStringAdapters == true,
            includeDtoComparison = project.includeDtoComparison == true)
        val archiveDir = work.resolve("archives/aar-${project.projectId}")
        fun generate(packageCount: Int, activityCount: Int, destination: File): JunkGeneratedArchive {
            val generator = AndroidJunkGenerator(work.path, destination.path, project.packageName, packageCount, activityCount,
                project.resourcePrefix, policy, logReport = false)
            return JunkGeneratedArchive(generator.startGenerate(), generator.lastReport)
        }
        val size = project.sizeRange()
        if (size != null) {
            val result = generateJunkAarInRange(archiveDir, size, policy) { activities, stage -> generate(activities, 1, stage) }
            val resolved = project.copy(packageCount = result.activities, activitiesPerPackage = 1, minimumSizeMb = null, maximumSizeMb = null)
            return JunkProjectArchive(resolved, result.archive.file, result.activities)
        }
        val generated = generate(project.packageCount, project.activitiesPerPackage, archiveDir)
        val activities = checkNotNull(generated.report).values.getValue("activities") as Int
        return JunkProjectArchive(project, generated.file, activities)
    }
}
