package org.tool.kit.tests.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.apache.commons.codec.binary.Hex
import org.junit.Test
import org.tool.kit.data.repository.JvmLibRulesRepository
import org.tool.kit.data.source.rules.CoreRuleDto
import org.tool.kit.data.source.rules.CoreRulesFileDto
import org.tool.kit.data.source.rules.LibRulesTransport
import org.tool.kit.data.source.rules.RulesArtifactDto
import org.tool.kit.data.source.rules.RulesManifestDto
import org.tool.kit.data.source.rules.RulesMetadataDto
import org.tool.kit.data.source.rules.rulesJson
import org.tool.kit.domain.apk.ApkLibRuleType
import org.tool.kit.domain.apk.ApkRuleUpdateStatus
import org.tool.kit.platform.JvmLibRuleIconDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmLibRulesRepositoryTest {
    @Test
    fun loadsBundledBaselineAndDecodesSvgIcons() = runBlocking {
        val dir = Files.createTempDirectory("lcrules-baseline-").toFile()
        try {
            val repo = JvmLibRulesRepository(Dispatchers.IO, FakeTransport(), dir)
            val meta = repo.metadata()
            assertEquals(5, meta.schemaVersion)
            assertTrue(meta.ruleCount >= 3000)
            assertTrue(meta.isBundled)

            val matcher = repo.matcher()
            assertEquals("Flutter", matcher.match("libflutter.so", ApkLibRuleType.Native, useRegex = false)?.label)
            assertNotNull(matcher.match("libAMapSDK_MAP_v7_9_1.so", ApkLibRuleType.Native, useRegex = true))
            assertNotNull(matcher.matchDexPackage("androidx.compose.runtime"))

            val decoder = JvmLibRuleIconDecoder(repo, Dispatchers.IO)
            val flutterIcon = assertNotNull(decoder.decode("ic_lib_flutter"))
            assertEquals(64, flutterIcon.width)
            assertEquals(64, flutterIcon.height)
            assertNull(decoder.decode("ic_non_existent_icon"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun updatesAtomicallyAndPreservesBaselineOnCorruptOrOutdatedArtifacts() = runBlocking {
        val dir = Files.createTempDirectory("lcrules-update-").toFile()
        try {
            val transport = FakeTransport()
            val repo = JvmLibRulesRepository(Dispatchers.IO, transport, dir)
            val baseline = repo.metadata()

            // Already up to date when manifest version <= baseline.
            transport.manifest = manifestFor(baseline.dataVersion, byteArrayOf(1))
            val upToDate = repo.checkAndUpdate()
            assertEquals(ApkRuleUpdateStatus.AlreadyUpToDate, upToDate.status)
            assertEquals(baseline.dataVersion, repo.metadata().dataVersion)

            // Failed update on corrupt artifact leaves baseline untouched.
            val badBytes = byteArrayOf(1, 2, 3, 4)
            transport.manifest = manifestFor(baseline.dataVersion + 10, badBytes)
            transport.artifactBytes = badBytes
            val failed = repo.checkAndUpdate()
            assertEquals(ApkRuleUpdateStatus.Failed, failed.status)
            assertEquals(baseline.dataVersion, repo.metadata().dataVersion)
            assertTrue(repo.metadata().isBundled)

            // Valid newer portable zip repackages, persists, and survives repo restart.
            val newerZip = buildPortableZip(baseline.dataVersion + 5)
            transport.manifest = manifestFor(baseline.dataVersion + 5, newerZip)
            transport.artifactBytes = newerZip
            val updated = repo.checkAndUpdate()
            assertEquals(ApkRuleUpdateStatus.Updated, updated.status)
            assertEquals(baseline.dataVersion + 5, updated.metadata?.dataVersion)
            assertFalse(updated.metadata!!.isBundled)
            assertEquals("Updated SDK", repo.matcher().match("libupdated.so", ApkLibRuleType.Native)?.label)

            val restarted = JvmLibRulesRepository(Dispatchers.IO, transport, dir)
            assertEquals(baseline.dataVersion + 5, restarted.metadata().dataVersion)
            assertFalse(restarted.metadata().isBundled)
            assertEquals("Updated SDK", restarted.matcher().match("libupdated.so", ApkLibRuleType.Native)?.label)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun fetchesLocalesAndPerMatcherOverridesAndReusesDiskCache() = runBlocking {
        val dir = Files.createTempDirectory("lcrules-detail-").toFile()
        try {
            val uuid = "DAA626BA-CBC2-4265-B2C7-145D8527EBAA"
            val transport = FakeTransport()
            transport.details[uuid] = """
                {
                  "schemaVersion": 5,
                  "uuid": "$uuid",
                  "data": [
                    {"locale": "zh-Hans", "data": {"label": "默认中文", "dev_team": "TeamA", "description": "中文简介", "source_link": "https://example.com", "rule_contributors": ["Alice"]}},
                    {"locale": "en", "data": {"label": "Default EN", "dev_team": "TeamA", "description": "English desc", "source_link": "https://example.com", "rule_contributors": ["Alice"]}}
                  ],
                  "matchers": [
                    {"id": 1},
                    {"id": 2, "detailData": [
                      {"locale": "en", "data": {"label": "Override EN", "dev_team": "TeamB", "description": "Override desc", "source_link": "https://override.example.com", "rule_contributors": ["Bob"]}}
                    ]}
                  ]
                }
            """.trimIndent()
            val repo = JvmLibRulesRepository(Dispatchers.IO, transport, dir)

            val zhDetail = assertNotNull(repo.detail(uuid, 1, preferChinese = true).getOrThrow())
            assertEquals("中文简介", zhDetail.description)
            assertEquals("TeamA", zhDetail.devTeam)
            assertEquals(1, transport.detailFetchCount)

            // Re-instance reads from disk cache with 0 network calls.
            val repo2 = JvmLibRulesRepository(Dispatchers.IO, transport, dir)
            val enDetail = assertNotNull(repo2.detail(uuid, 1, preferChinese = false).getOrThrow())
            assertEquals("English desc", enDetail.description)
            val overrideDetail = assertNotNull(repo2.detail(uuid, 2, preferChinese = true).getOrThrow())
            assertEquals("Override desc", overrideDetail.description)
            assertEquals(1, transport.detailFetchCount)

            // Invalid UUID is rejected before touching disk or network.
            assertTrue(repo2.detail("../evil", 1, preferChinese = true).isFailure)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun manifestFor(version: Long, artifactBytes: ByteArray): RulesManifestDto {
        val sha = Hex.encodeHexString(MessageDigest.getInstance("SHA-256").digest(artifactBytes))
        return RulesManifestDto(
            schemaVersion = 5,
            dataVersion = version,
            ruleCount = 1,
            contentSha256 = sha,
            sourceRevision = "deadbeef",
            minimumReader = mapOf("portable" to 5),
            artifacts = mapOf(
                "portable" to RulesArtifactDto(
                    path = "releases/$version/portable-v5.zip",
                    sha256 = sha,
                    size = artifactBytes.size.toLong(),
                    schemaVersion = 5,
                    minimumReaderVersion = 5,
                )
            ),
        )
    }

    private fun buildPortableZip(version: Long): ByteArray {
        val metadata = rulesJson.encodeToString(
            RulesMetadataDto(
                schemaVersion = 5,
                dataVersion = version,
                ruleCount = 1,
                contentSha256 = "abc",
                sourceRevision = "rev$version",
                minimumReader = mapOf("portable" to 5),
            )
        )
        val core = rulesJson.encodeToString(
            CoreRulesFileDto(
                schemaVersion = 5,
                rules = listOf(
                    CoreRuleDto(
                        id = 1,
                        uuid = "11111111-1111-1111-1111-111111111111",
                        name = "libupdated.so",
                        label = "Updated SDK",
                        type = 0,
                        iconIndex = 0,
                        iconId = "ic_updated",
                        isSimpleColorIcon = true,
                        isRegexRule = false,
                        priority = 1,
                        detailPath = "details/11111111-1111-1111-1111-111111111111/1.json",
                    )
                ),
            )
        )
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" fill="#000"/></svg>"""
        return ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zip ->
                zip.putNextEntry(ZipEntry("metadata.json")); zip.write(metadata.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("core.json")); zip.write(core.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("icons/index.json")); zip.write("{}".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("icons/ic_updated.svg")); zip.write(svg.toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("details/11111111-1111-1111-1111-111111111111/1.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            }
        }.toByteArray()
    }

    private class FakeTransport : LibRulesTransport {
        var manifest: RulesManifestDto? = null
        var artifactBytes: ByteArray = ByteArray(0)
        val details = mutableMapOf<String, String>()
        var detailFetchCount = 0

        override suspend fun fetchManifest(): RulesManifestDto =
            manifest ?: error("No manifest configured")

        override suspend fun downloadArtifact(
            relativePath: String,
            destFile: File,
            expectedSize: Long,
            expectedSha256: String,
        ) {
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(artifactBytes)
        }

        override suspend fun fetchLibraryDetailJson(uuid: String): String? {
            detailFetchCount++
            return details[uuid]
        }
    }
}
