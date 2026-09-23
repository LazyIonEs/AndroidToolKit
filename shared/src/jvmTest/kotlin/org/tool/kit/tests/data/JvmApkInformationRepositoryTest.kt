package org.tool.kit.tests.data

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.junit.Test
import org.tool.kit.data.process.JvmProcessRunner
import org.tool.kit.data.repository.JvmApkInformationRepository
import org.tool.kit.data.source.Aapt2DataSource
import org.tool.kit.data.source.Aapt2Locator
import org.tool.kit.data.source.ApkIconDataSource
import org.tool.kit.domain.apk.ApkCommandFailed
import org.tool.kit.domain.apk.ApkExportedDeclaration
import org.tool.kit.domain.apk.ApkFileCategory
import org.tool.kit.domain.apk.ApkFileMetadata
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.platform.JvmApkIconDecoder
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmApkInformationRepositoryTest {
    private val template = File(checkNotNull(System.getProperty("test.apkTemplate")))
    private val resources = template.parentFile.parentFile
    private val os = if (System.getProperty("os.name").startsWith("Win")) "windows"
    else if (System.getProperty("os.arch") in listOf(
            "aarch64",
            "arm64"
        )
    ) "macos-arm64" else "macos-x64"
    private val aapt = Aapt2DataSource(
        Aapt2Locator({ File(resources, os).path }),
        JvmProcessRunner(Dispatchers.IO),
        Dispatchers.IO
    )
    private val icons = ApkIconDataSource(Dispatchers.IO)
    private val decoder = JvmApkIconDecoder(Dispatchers.IO)
    private val repository = JvmApkInformationRepository(aapt, icons, Dispatchers.IO)

    @Test
    fun realAaptParsesChineseSpaceQuotePathAndMatchesFixedTemplateFields() = runBlocking {
        val dir = Files.createTempDirectory("APK 中文 空格 ").toFile()
        try {
            val apk = template.copyTo(File(dir, "中文 ' 引号.apk"))
            val result = ReadApkInformationUseCase(repository)(apk.path).getOrThrow()
            assertEquals("HelloAndroid", result.label)
            assertEquals("com.lazyiones.helloandroid", result.packageName)
            assertEquals("1", result.versionCode); assertEquals("1.0", result.versionName)
            assertEquals("30", result.compileSdkVersion); assertEquals(
                "21",
                result.minSdkVersion
            ); assertEquals("30", result.targetSdkVersion)
            assertNull(result.usesPermissionList); assertNull(result.channel)
            assertNotNull(result.icon)
            assertEquals(3569137L, result.size)
            assertEquals("e4211c3ac04c1e42b8fc3447951b1bc5", result.md5)
            assertEquals(
                "c4c1b8699b997c57e3afc18cb9796123d805c7d2bfe533a8eaca7f82500085a3",
                result.sha256
            )
            assertEquals("com.lazyiones.helloandroid.MainActivity", result.launchableActivity)
            assertEquals(
                "com.lazyiones.helloandroid.MainActivity",
                assertNotNull(result.components).single().name
            )
            assertEquals(ApkExportedDeclaration.Enabled, result.components.single().exported)
            assertEquals("com.lazyiones.helloandroid", result.components.single().process)
            val archive = assertNotNull(result.archive)
            assertTrue(archive.files.any { it.path == "classes.dex" && it.category == ApkFileCategory.Dex })
            assertEquals(
                result.size,
                archive.files.sumOf { it.compressedSize } + archive.overheadBytes)
            assertTrue(archive.nativeLibraries.isEmpty())
            assertIconEntry(apk, "res/o-.png", result.icon)
            // Windows also proves metadata/ZIP handles are closed when this file is removed.
            assertTrue(apk.delete())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun metadataStreamsBothDigestsForEmptyAndSmallFiles() = runBlocking {
        val dir = Files.createTempDirectory("apk-digests-").toFile()
        try {
            val file = File(dir, "fixture.apk")
            file.writeText("")
            assertEquals(
                ApkFileMetadata(
                    0,
                    "d41d8cd98f00b204e9800998ecf8427e",
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                ), repository.metadata(file.path)
            )
            file.writeText("abc")
            assertEquals(
                ApkFileMetadata(
                    3,
                    "900150983cd24fb0d6963f7d28e17f72",
                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                ), repository.metadata(file.path)
            )
            assertTrue(file.delete())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resourceTableSelectsTheHighestDensityIconAndDirectPathReadsTheRequestedEntry() =
        runBlocking {
            val xml = aapt.manifest(template.path)
            val resource = icons.read(xml, template.path, "resource.xml")
            assertNotNull(resource)
            assertIconEntry(template, "res/o-.png", resource)
            val direct = icons.read(xml, template.path, "res/o-.png")
            assertNotNull(direct)
            assertIconEntry(template, "res/o-.png", direct)
            assertNull(icons.read(null, template.path, "resource.xml"))
            assertNull(icons.read("missing icon id", template.path, "resource.xml"))
            assertNull(icons.read(xml, template.path, "missing.png"))
        }

    @Test
    fun missingAndCorruptIconsRemainValidApkInformationAndBrokenApkUsesCommandFailure() =
        runBlocking {
            val dir = Files.createTempDirectory("apk-icons-").toFile()
            try {
                for (corrupt in listOf(false, true)) {
                    val apk = File(dir, "$corrupt.apk")
                    ZipFile(template).use { zip ->
                        ZipOutputStream(apk.outputStream()).use { output ->
                            zip.entries().asSequence().forEach { entry ->
                                if (entry.name.endsWith(".png")) {
                                    if (corrupt) {
                                        output.putNextEntry(ZipEntry(entry.name)); output.write(
                                            byteArrayOf(1, 2, 3)
                                        ); output.closeEntry()
                                    }
                                } else {
                                    output.putNextEntry(ZipEntry(entry.name)); zip.getInputStream(
                                        entry
                                    ).use { it.copyTo(output) }; output.closeEntry()
                                }
                            }
                        }
                    }
                    val result = ReadApkInformationUseCase(repository)(apk.path).getOrThrow()
                    assertEquals("HelloAndroid", result.label); assertNull(result.icon)
                }
                val broken = File(dir, "broken.apk").apply { writeText("not an APK") }
                assertIs<ApkCommandFailed>(ReadApkInformationUseCase(repository)(broken.path).exceptionOrNull())
            } finally {
                dir.deleteRecursively()
            }
            Unit
        }

    @Test
    fun bundledUnsignedApkPreservesPermissionOrderAndApplicationIconFallback() = runBlocking {
        val apk = File(template.parentFile, "qq.apk")
        val result = ReadApkInformationUseCase(repository)(apk.path).getOrThrow()
        assertEquals("Tap_unsign", result.label); assertEquals(
        "tap.claim.nosig",
        result.packageName
    )
        assertEquals(
            listOf(
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_PHONE_STATE",
                "android.permission.READ_EXTERNAL_STORAGE"
            ), result.usesPermissionList
        )
        assertEquals("3", result.minSdkVersion); assertEquals("", result.targetSdkVersion)
        assertNull(result.channel); assertNotNull(result.icon)
        assertIconEntry(apk, "res/drawable/ic_launcher.png", result.icon)
    }

    private suspend fun assertIconEntry(apk: File, entry: String, source: ApkIconSource) {
        val bytes = withContext(Dispatchers.IO) {
            ZipFile(apk).use { zip ->
                zip.getInputStream(zip.getEntry(entry)).use { it.readBytes() }
            }
        }
        assertContentEquals(bytes, source.bytes(), "Selected icon entry: $entry")
        val actual = decoder.decode(source)
        Image.makeFromEncoded(bytes)
            .use { expected -> assertPixelsEqual(expected.toComposeImageBitmap(), actual) }
    }

    private fun assertPixelsEqual(expected: ImageBitmap?, actual: ImageBitmap) {
        assertNotNull(expected); assertEquals(
            expected.width,
            actual.width
        ); assertEquals(expected.height, actual.height)
        val a = expected.toPixelMap()
        val b = actual.toPixelMap()
        for (y in 0 until expected.height) for (x in 0 until expected.width) assertEquals(
            a[x, y].toArgb(),
            b[x, y].toArgb(),
            "pixel $x,$y"
        )
    }
}
