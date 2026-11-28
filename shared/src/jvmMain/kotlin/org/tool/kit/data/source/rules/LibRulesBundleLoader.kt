package org.tool.kit.data.source.rules

import kotlinx.serialization.json.Json
import org.tool.kit.domain.apk.ApkLibRule
import org.tool.kit.domain.apk.ApkLibRuleType
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.LibRuleMatcher
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val SUPPORTED_RULES_SCHEMA = 5
private const val MAX_BUNDLE_ENTRY_BYTES = 16 * 1024 * 1024
private const val MAX_BUNDLE_TOTAL_BYTES = 64 * 1024 * 1024L
private const val MAX_PORTABLE_ENTRIES = 10_000

internal class LoadedRulesBundle(
    val metadata: ApkLibRulesMetadata,
    val matcher: LibRuleMatcher,
    val icons: Map<String, ByteArray>,
)

internal val rulesJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun loadBundleFromZipBytes(bytes: ByteArray, isBundled: Boolean): LoadedRulesBundle =
    ByteArrayInputStream(bytes).use { loadBundleFromStream(it, isBundled) }

internal fun loadBundleFromFile(file: File, isBundled: Boolean): LoadedRulesBundle {
    require(file.isFile && file.length() > 0L) { "Missing rules bundle: ${file.path}" }
    return FileInputStream(file).use { loadBundleFromStream(it, isBundled) }
}

private fun loadBundleFromStream(input: InputStream, isBundled: Boolean): LoadedRulesBundle {
    var metadataBytes: ByteArray? = null
    var coreBytes: ByteArray? = null
    val icons = linkedMapOf<String, ByteArray>()
    var totalRead = 0L

    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            try {
                if (entry.isDirectory) continue
                val name = entry.name
                if (name == "metadata.json" || name == "core.json" ||
                    (name.startsWith("icons/") && name.endsWith(".svg"))
                ) {
                    val data = readBoundedEntry(zip, MAX_BUNDLE_ENTRY_BYTES)
                    totalRead += data.size
                    require(totalRead <= MAX_BUNDLE_TOTAL_BYTES) { "Rules bundle exceeds size limit" }
                    when (name) {
                        "metadata.json" -> metadataBytes = data
                        "core.json" -> coreBytes = data
                        else -> {
                            val iconId = name.removePrefix("icons/").removeSuffix(".svg")
                            if (iconId.isNotBlank() && '/' !in iconId) {
                                icons[iconId] = data
                            }
                        }
                    }
                }
            } finally {
                zip.closeEntry()
            }
        }
    }

    val rawMetadata = requireNotNull(metadataBytes) { "Missing metadata.json in rules bundle" }
    val rawCore = requireNotNull(coreBytes) { "Missing core.json in rules bundle" }

    val metadataDto = rulesJson.decodeFromString<RulesMetadataDto>(rawMetadata.decodeToString())
    require(metadataDto.schemaVersion == SUPPORTED_RULES_SCHEMA) {
        "Unsupported rules schemaVersion: ${metadataDto.schemaVersion}"
    }
    val minPortableReader = metadataDto.minimumReader["portable"] ?: SUPPORTED_RULES_SCHEMA
    require(minPortableReader <= SUPPORTED_RULES_SCHEMA) {
        "Bundle requires newer portable reader: $minPortableReader"
    }

    val coreDto = rulesJson.decodeFromString<CoreRulesFileDto>(rawCore.decodeToString())
    require(coreDto.schemaVersion == SUPPORTED_RULES_SCHEMA) {
        "Unsupported core.json schemaVersion: ${coreDto.schemaVersion}"
    }
    require(metadataDto.ruleCount == coreDto.rules.size) {
        "Rules metadata count mismatch: expected ${metadataDto.ruleCount}, got ${coreDto.rules.size}"
    }

    val rules = coreDto.rules.map { dto ->
        val type = requireNotNull(ApkLibRuleType.fromCode(dto.type)) {
            "Unsupported rule type code: ${dto.type}"
        }
        ApkLibRule(
            id = dto.id,
            uuid = dto.uuid,
            name = dto.name,
            label = dto.label,
            type = type,
            iconIndex = dto.iconIndex,
            iconId = dto.iconId?.takeIf { it.isNotBlank() },
            isSimpleColorIcon = dto.isSimpleColorIcon,
            isRegexRule = dto.isRegexRule,
            regexName = dto.regexName?.takeIf { it.isNotBlank() },
            priority = dto.priority,
            hasDetail = !dto.detailPath.isNullOrBlank(),
        )
    }
    val matcher = LibRuleMatcher(rules)
    val metadata = ApkLibRulesMetadata(
        schemaVersion = metadataDto.schemaVersion,
        dataVersion = metadataDto.dataVersion,
        ruleCount = metadataDto.ruleCount,
        contentSha256 = metadataDto.contentSha256,
        sourceRevision = metadataDto.sourceRevision,
        isBundled = isBundled,
    )
    return LoadedRulesBundle(metadata = metadata, matcher = matcher, icons = icons)
}

/**
 * Extract only lightweight entries (`metadata.json`, `core.json`, `icons/index.json`, and icon SVGs)
 * from an upstream `portable-v5.zip` into [targetZipFile] and validate the result before returning.
 */
internal fun repackagePortableZipToLightweight(
    portableZipFile: File,
    targetZipFile: File,
): LoadedRulesBundle {
    targetZipFile.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
    ZipFile(portableZipFile).use { src ->
        val entries = src.entries()
        var entryCount = 0
        var totalWritten = 0L
        ZipOutputStream(FileOutputStream(targetZipFile)).use { dst ->
            dst.setLevel(Deflater.BEST_COMPRESSION)
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                require(entryCount <= MAX_PORTABLE_ENTRIES) { "Too many entries in portable zip" }
                if (entry.isDirectory) continue
                val name = entry.name
                require(!name.startsWith("/") && ".." !in name.split('/')) {
                    "Unsafe zip entry path: $name"
                }
                val keep = name == "metadata.json" ||
                    name == "core.json" ||
                    name == "icons/index.json" ||
                    (name.startsWith("icons/") && name.endsWith(".svg") && name.count { it == '/' } == 1)
                if (!keep) continue
                require(entry.size in 0L..MAX_BUNDLE_ENTRY_BYTES.toLong()) {
                    "Portable entry exceeds size limit: $name"
                }
                val bytes = src.getInputStream(entry).use { readBoundedEntry(it, MAX_BUNDLE_ENTRY_BYTES) }
                totalWritten += bytes.size
                require(totalWritten <= MAX_BUNDLE_TOTAL_BYTES) { "Lightweight bundle exceeds size limit" }
                dst.putNextEntry(ZipEntry(name))
                dst.write(bytes)
                dst.closeEntry()
            }
        }
    }
    return loadBundleFromFile(targetZipFile, isBundled = false)
}

private fun readBoundedEntry(input: InputStream, maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val out = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Entry exceeds $maxBytes bytes" }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}
