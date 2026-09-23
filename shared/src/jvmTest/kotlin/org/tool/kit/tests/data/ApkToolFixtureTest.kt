package org.tool.kit.tests.data

import com.android.apksig.ApkVerifier
import com.android.ide.common.signing.KeystoreHelper
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.process.JvmProcessRunner
import org.tool.kit.data.source.*
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.*

class ApkToolFixtureTest {
    @get:Rule val temporary = TemporaryFolder()
    private val template get() = File(checkNotNull(System.getProperty("test.apkTemplate")))
    private fun request(output: File, icon: File? = null) = BuildApkRequest(output.path, icon?.path.orEmpty(),
        "org.fixture.generated", "32", "23", "12", "2.3", "中文 Empty")

    @Test fun realTemplateBuildsRequestedMetadataAndUnscaledIconsWithSigningOnAndOff() = runBlocking {
        val icon = temporary.root.resolve("中文 icon.png")
        val pixels = BufferedImage(17, 13, BufferedImage.TYPE_INT_ARGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, (0xff000000L or (x.toLong() shl 16) or (y.toLong() shl 8) or 0x42).toInt())
        }
        ImageIO.write(pixels, "png", icon)
        val output = temporary.newFolder("output")
        val cache = temporary.newFolder("cache")
        val installed = temporary.newFolder("read-only install")
        val installedTemplate = template.copyTo(installed.resolve("apktool.apk"))
        val originalPermissions = Files.getPosixFilePermissions(installed.toPath())
        Files.setPosixFilePermissions(installedTemplate.toPath(), PosixFilePermissions.fromString("r--r--r--"))
        Files.setPosixFilePermissions(installed.toPath(), PosixFilePermissions.fromString("r-xr-xr-x"))
        try {
            assertFalse(installed.canWrite(), "The installation fixture really must be read-only")
            val useCase = BuildApkUseCase(JvmApkToolDataSource(installedTemplate, Dispatchers.IO),
                SignApkUseCase(JvmApkSignerDataSource(Dispatchers.IO)), JvmApkBuildWorkspaces(cache, Dispatchers.IO))
            val result = useCase(request(output, icon)) as BuildApkOutcome.Success
            val built = File(result.outputPath)
            assertEquals("中文 Empty.apk", built.name)
            assertEquals(built.length(), result.sizeBytes)
            assertNull(result.signing)
            assertFalse(ApkVerifier.Builder(built).build().verify().isVerified)
            val os = if (System.getProperty("os.name").lowercase().contains("win")) "windows" else if (System.getProperty("os.arch") == "aarch64") "macos-arm64" else "macos-x64"
            val aapt = Aapt2DataSource(Aapt2Locator({ template.parentFile.parentFile.resolve(os).path }), JvmProcessRunner(Dispatchers.IO), Dispatchers.IO)
            val badging = aapt.badging(built.path)
            assertContains(badging, "name='org.fixture.generated'")
            assertContains(badging, "versionCode='12'")
            assertContains(badging, "versionName='2.3'")
            assertContains(badging, "sdkVersion:'23'")
            assertContains(badging, "targetSdkVersion:'32'")
            assertContains(badging, "application-label:'中文 Empty'")
            ZipFile(built).use { actual ->
                listOf("AndroidManifest.xml", "resources.arsc", "classes.dex").forEach { assertNotNull(actual.getEntry(it)) }
                val icons = actual.entries().asSequence().filter { it.name.matches(Regex("res/mipmap-.*?/ic_launcher.png")) }.toList()
                assertEquals(5, icons.size)
                for (entry in icons) {
                    val bytes = actual.getInputStream(entry).use { it.readBytes() }
                    val image = ImageIO.read(bytes.inputStream())
                    assertEquals(17, image.width); assertEquals(13, image.height)
                    assertContentEquals(pixels.getRGB(0, 0, 17, 13, null, 0, 17), image.getRGB(0, 0, 17, 13, null, 0, 17))
                }
            }
            val store = temporary.root.resolve("fixture.jks")
            assertTrue(KeystoreHelper.createNewStore("JKS", store, "fixture-only", "fixture-only", "fixture",
                "CN=Fixture,OU=Test,O=AndroidToolKit,L=Test,S=Test,C=CN", 1, 2048))
            val signing = SignApkRequest("", output.path, "", "-sign", true, true, false,
                "unused", ApkSigningPolicy.V3, "apk-name.apk.idsig", SigningCredentials(store.path, "fixture-only", "fixture", "fixture-only"))
            val signed = useCase(request(output, icon).copy(signing = signing)) as BuildApkOutcome.Success
            assertEquals(built.path, signed.outputPath)
            assertEquals(built.length(), signed.sizeBytes)
            val signature = signed.signing as SignApkOutcome.Success
            assertEquals("中文 Empty-sign.apk", File(signature.outputPath).name)
            val verified = ApkVerifier.Builder(File(signature.outputPath)).setMinCheckedPlatformVersion(23).build().verify()
            assertTrue(verified.isVerified, verified.errors.toString())
            assertTrue(verified.isVerifiedUsingV1Scheme); assertTrue(verified.isVerifiedUsingV2Scheme); assertTrue(verified.isVerifiedUsingV3Scheme)
            assertFalse(verified.isVerifiedUsingV4Scheme)
            val failedSign = useCase(request(output).copy(signing = signing.copy(credentials = signing.credentials.copy(storePassword = "wrong")))) as BuildApkOutcome.Success
            assertIs<SignApkOutcome.Failure>(failedSign.signing)
            assertTrue(File(failedSign.outputPath).isFile)
            assertTrue(cache.listFiles()!!.isEmpty())
            assertEquals(listOf("apktool.apk"), installed.list()!!.toList())
            assertContentEquals(template.readBytes(), installedTemplate.readBytes())
            // The default icon path also remains valid after repeated builds into the same output.
            assertContains(aapt.badging(failedSign.outputPath), "application-icon-160:")
        } finally { Files.setPosixFilePermissions(installed.toPath(), originalPermissions) }
    }
}
