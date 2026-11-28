package org.tool.kit.data.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tool.kit.data.source.rules.CanonicalLibraryDto
import org.tool.kit.data.source.rules.KtorLibRulesTransport
import org.tool.kit.data.source.rules.LibRulesTransport
import org.tool.kit.data.source.rules.LoadedRulesBundle
import org.tool.kit.data.source.rules.SUPPORTED_RULES_SCHEMA
import org.tool.kit.data.source.rules.loadBundleFromFile
import org.tool.kit.data.source.rules.loadBundleFromZipBytes
import org.tool.kit.data.source.rules.repackagePortableZipToLightweight
import org.tool.kit.data.source.rules.resolveLocalizedDetail
import org.tool.kit.data.source.rules.rulesJson
import org.tool.kit.domain.apk.ApkLibRuleDetail
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.ApkRuleUpdateResult
import org.tool.kit.domain.apk.ApkRuleUpdateStatus
import org.tool.kit.domain.apk.LibRuleMatcher
import org.tool.kit.domain.repository.LibRulesRepository
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger("JvmLibRulesRepository")
private val UUID_PATTERN = Regex("^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")

class JvmLibRulesRepository(
    private val io: CoroutineDispatcher,
    private val transport: LibRulesTransport = KtorLibRulesTransport(io),
    private val storageRoot: File = defaultLibRulesStorageDir(),
    private val baselineStreamProvider: () -> InputStream = {
        requireNotNull(JvmLibRulesRepository::class.java.classLoader?.getResourceAsStream("lcrules/baseline-v5.zip")) {
            "Missing bundled resource lcrules/baseline-v5.zip"
        }
    },
) : LibRulesRepository {
    private val bundleMutex = Mutex()
    private val detailCache = ConcurrentHashMap<String, CanonicalLibraryDto>()

    @Volatile
    private var activeBundle: LoadedRulesBundle? = null

    override suspend fun metadata(): ApkLibRulesMetadata = ensureLoaded().metadata

    override suspend fun matcher(): LibRuleMatcher = ensureLoaded().matcher

    override suspend fun iconBytes(iconId: String): ByteArray? =
        ensureLoaded().icons[iconId]?.copyOf()

    override suspend fun detail(
        uuid: String,
        ruleId: Int,
        preferChinese: Boolean,
    ): Result<ApkLibRuleDetail?> = withContext(io) {
        try {
            require(UUID_PATTERN.matches(uuid)) { "Invalid library UUID: $uuid" }
            val cached = detailCache[uuid]
            if (cached != null) {
                return@withContext Result.success(resolveLocalizedDetail(cached, uuid, ruleId, preferChinese))
            }
            val diskFile = File(File(storageRoot, "details"), "$uuid.json")
            if (diskFile.isFile) {
                val parsed = runCatching {
                    rulesJson.decodeFromString<CanonicalLibraryDto>(diskFile.readText(Charsets.UTF_8))
                }.getOrNull()
                if (parsed != null) {
                    detailCache[uuid] = parsed
                    return@withContext Result.success(resolveLocalizedDetail(parsed, uuid, ruleId, preferChinese))
                } else {
                    diskFile.delete()
                }
            }
            val rawJson = transport.fetchLibraryDetailJson(uuid)
                ?: return@withContext Result.success(null)
            val parsed = rulesJson.decodeFromString<CanonicalLibraryDto>(rawJson)
            writeDetailAtomically(diskFile, rawJson)
            detailCache[uuid] = parsed
            Result.success(resolveLocalizedDetail(parsed, uuid, ruleId, preferChinese))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            logger.warn(error) { "Failed to load detail for $uuid ($ruleId)" }
            Result.failure(error)
        }
    }

    override suspend fun checkAndUpdate(): ApkRuleUpdateResult = withContext(io) {
        bundleMutex.withLock {
            val current = loadBundleLocked()
            val stagingDir = File(storageRoot, "staging")
            val activeDir = File(storageRoot, "active")
            val backupDir = File(storageRoot, "backup")
            try {
                val manifest = transport.fetchManifest()
                require(manifest.schemaVersion == SUPPORTED_RULES_SCHEMA) {
                    "Unsupported manifest schema: ${manifest.schemaVersion}"
                }
                val minReader = manifest.minimumReader["portable"] ?: SUPPORTED_RULES_SCHEMA
                require(minReader <= SUPPORTED_RULES_SCHEMA) {
                    "Newer app version required for rules v${manifest.dataVersion}"
                }
                val artifact = requireNotNull(manifest.artifacts["portable"]) {
                    "Missing portable artifact in manifest"
                }
                require(artifact.schemaVersion == SUPPORTED_RULES_SCHEMA &&
                    artifact.minimumReaderVersion <= SUPPORTED_RULES_SCHEMA
                ) {
                    "Incompatible portable artifact version"
                }
                if (manifest.dataVersion <= current.metadata.dataVersion) {
                    return@withLock ApkRuleUpdateResult(ApkRuleUpdateStatus.AlreadyUpToDate, current.metadata)
                }

                stagingDir.deleteRecursively()
                check(stagingDir.mkdirs()) { "Cannot create staging directory" }
                val downloadedZip = File(stagingDir, "portable-v5.zip")
                val stagedBundle = File(stagingDir, "bundle-v5.zip")
                transport.downloadArtifact(
                    relativePath = artifact.path,
                    destFile = downloadedZip,
                    expectedSize = artifact.size,
                    expectedSha256 = artifact.sha256,
                )
                val loaded = repackagePortableZipToLightweight(downloadedZip, stagedBundle)
                require(loaded.metadata.dataVersion > current.metadata.dataVersion) {
                    "Downloaded rules version ${loaded.metadata.dataVersion} is not newer than ${current.metadata.dataVersion}"
                }

                check(activeDir.mkdirs() || activeDir.isDirectory) { "Cannot create active directory" }
                val activeFile = File(activeDir, "bundle-v5.zip")
                val backupFile = File(backupDir, "bundle-v5.zip")
                backupDir.deleteRecursively()
                if (activeFile.exists()) {
                    check(backupDir.mkdirs() || backupDir.isDirectory)
                    check(activeFile.renameTo(backupFile)) { "Cannot back up active rules bundle" }
                }
                if (!stagedBundle.renameTo(activeFile)) {
                    if (backupFile.exists()) backupFile.renameTo(activeFile)
                    error("Cannot publish updated rules bundle")
                }
                backupDir.deleteRecursively()
                activeBundle = loaded
                detailCache.clear()
                ApkRuleUpdateResult(ApkRuleUpdateStatus.Updated, loaded.metadata)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                logger.warn(error) { "Rules bundle update failed" }
                ApkRuleUpdateResult(ApkRuleUpdateStatus.Failed, current.metadata, error.message)
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    private suspend fun ensureLoaded(): LoadedRulesBundle {
        activeBundle?.let { return it }
        return withContext(io) {
            bundleMutex.withLock {
                activeBundle ?: loadBundleLocked().also { activeBundle = it }
            }
        }
    }

    private fun loadBundleLocked(): LoadedRulesBundle {
        val baseline = baselineStreamProvider().use { it.readBytes() }.let {
            loadBundleFromZipBytes(it, isBundled = true)
        }
        val activeDir = File(storageRoot, "active")
        val backupDir = File(storageRoot, "backup")
        val activeFile = File(activeDir, "bundle-v5.zip")
        val backupFile = File(backupDir, "bundle-v5.zip")
        if (!activeFile.exists() && backupFile.exists()) {
            activeDir.mkdirs()
            backupFile.renameTo(activeFile)
        }
        if (activeFile.isFile) {
            val candidate = runCatching { loadBundleFromFile(activeFile, isBundled = false) }.getOrNull()
            if (candidate != null && candidate.metadata.dataVersion > baseline.metadata.dataVersion) {
                backupDir.deleteRecursively()
                return candidate
            }
            activeFile.delete()
        }
        backupDir.deleteRecursively()
        return baseline
    }

    private fun writeDetailAtomically(destFile: File, rawJson: String) {
        val parent = destFile.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, "${destFile.name}.tmp")
        temp.writeText(rawJson, Charsets.UTF_8)
        if (destFile.exists()) destFile.delete()
        if (!temp.renameTo(destFile)) {
            temp.delete()
        }
    }
}

internal fun defaultLibRulesStorageDir(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = File(System.getProperty("user.home"))
    val base = when {
        os.contains("mac") -> File(home, "Library/Application Support/AndroidToolKit")
        os.contains("win") -> {
            val localAppData = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(home, "AppData/Local")
            File(localAppData, "AndroidToolKit")
        }
        else -> {
            val xdgData = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                ?: File(home, ".local/share")
            File(xdgData, "AndroidToolKit")
        }
    }
    return File(base, "lcrules")
}
