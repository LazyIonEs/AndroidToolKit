package org.tool.kit.tests.data

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.tool.kit.data.source.inspectApkArchiveEntryPreview
import org.tool.kit.data.source.inspectApkDexPackages
import org.tool.kit.data.source.inspectDecodedManifestXml
import org.tool.kit.data.source.readApkManifestExtras
import org.tool.kit.data.source.readDexClassPackages
import org.tool.kit.domain.apk.ApkArchiveEntryContent
import org.tool.kit.domain.apk.ApkArchiveFile
import org.tool.kit.domain.apk.ApkArchivePreviewKind
import org.tool.kit.domain.apk.ApkFileCategory
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.domain.apk.previewKind
import org.tool.kit.platform.JvmApkIconDecoder
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApkDexAndExtrasInspectorTest {
    @Test
    fun readsStaticLibrariesAndIntentActionsWhileIgnoringResourceRefsAndQueries() {
        val manifest = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example">
              <application>
                <uses-library android:name="org.apache.http.legacy" android:required="false" />
                <uses-static-library android:name="com.google.android.trichromelibrary" />
                <uses-library android:name="@7f010001" />
                <activity android:name=".MainActivity">
                  <intent-filter>
                    <action android:name="android.intent.action.MAIN" />
                    <action android:name="com.example.ACTION_VIEW" />
                  </intent-filter>
                </activity>
                <service android:name=".PushService">
                  <intent-filter>
                    <action android:name="com.huawei.push.action.MESSAGING_EVENT" />
                    <action android:name="?attr/ignored" />
                  </intent-filter>
                </service>
              </application>
              <queries>
                <intent><action android:name="android.intent.action.SEND" /></intent>
              </queries>
            </manifest>
        """.trimIndent()
        val extras = readApkManifestExtras(manifest.toByteArray())
        assertEquals(
            listOf("com.google.android.trichromelibrary", "org.apache.http.legacy"),
            extras.staticLibraries,
        )
        assertEquals(
            listOf(
                "android.intent.action.MAIN",
                "com.example.ACTION_VIEW",
                "com.huawei.push.action.MESSAGING_EVENT",
            ),
            extras.intentActions,
        )
    }

    @Test
    fun parsesSyntheticDexClassPackagesAndRealBundledApkDex() = runBlocking {
        val dexBytes = buildMinimalDex(
            listOf(
                "Lcom/example/app/MainActivity;",
                "Landroidx/compose/runtime/Composer;",
                "LRootClass;",
            )
        )
        val packages = readDexClassPackages(dexBytes)
        assertEquals(setOf("com.example.app", "androidx.compose.runtime"), packages)
        assertEquals(emptySet(), readDexClassPackages(byteArrayOf(1, 2, 3)))

        val template = File(checkNotNull(System.getProperty("test.apkTemplate")))
        val realPackages = assertNotNull(inspectApkDexPackages(template.path))
        assertTrue(realPackages.isNotEmpty(), "Expected packaged DEX classes in ${template.path}")
    }

    @Test
    fun decodesManifestXmlWithResolvedArscReferences() = runBlocking {
        val template = File(checkNotNull(System.getProperty("test.apkTemplate")))
        val xml = assertNotNull(inspectDecodedManifestXml(template.path))
        assertTrue(xml.contains("<manifest"), "Decoded XML should start with manifest tag")
        assertTrue(xml.contains("com.lazyiones.helloandroid"), "Decoded XML should contain package name")
        assertTrue(xml.contains("@string/") || xml.contains("@mipmap/") || xml.contains("@drawable/"),
            "Decoded XML should resolve resource references from resources.arsc")
    }

    @Test
    fun previewsSupportedImagesAndXmlWhileRejectingNinePatchAndBinaryGarbage() = runBlocking {
        assertEquals(ApkArchivePreviewKind.Image, ApkArchiveFile("res/icon.png", 1, 1, ApkFileCategory.Resources).previewKind())
        assertEquals(ApkArchivePreviewKind.Image, ApkArchiveFile("assets/logo.SVG", 1, 1, ApkFileCategory.Assets).previewKind())
        assertEquals(ApkArchivePreviewKind.Xml, ApkArchiveFile("res/layout/main.xml", 1, 1, ApkFileCategory.Resources).previewKind())
        assertNull(ApkArchiveFile("res/drawable/bg.9.png", 1, 1, ApkFileCategory.Resources).previewKind())
        assertNull(ApkArchiveFile("classes.dex", 1, 1, ApkFileCategory.Dex).previewKind())

        val template = File(checkNotNull(System.getProperty("test.apkTemplate")))
        val decoder = JvmApkIconDecoder(kotlinx.coroutines.Dispatchers.IO)
        val templateImage = assertIs<ApkArchiveEntryContent.Image>(
            inspectApkArchiveEntryPreview(template.path, "res/o-.png")
        )
        assertTrue(decoder.decode(templateImage.source).width > 0)
        val templateManifest = assertIs<ApkArchiveEntryContent.Xml>(
            inspectApkArchiveEntryPreview(template.path, "AndroidManifest.xml")
        )
        assertTrue(templateManifest.content.contains("com.lazyiones.helloandroid"))
        assertNull(inspectApkArchiveEntryPreview(template.path, "classes.dex"))

        val zipFile = Files.createTempFile("preview-fixture-", ".apk").toFile()
        try {
            val pngBytes = ByteArrayOutputStream().also {
                val img = BufferedImage(12, 8, BufferedImage.TYPE_INT_ARGB)
                ImageIO.write(img, "png", it)
            }.toByteArray()
            val svgBytes = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" fill="#123456"/></svg>""".toByteArray()
            ZipOutputStream(zipFile.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("assets/config.xml")); zip.write("<config enabled=\"true\"/>".toByteArray()); zip.closeEntry()
                zip.putNextEntry(ZipEntry("res/raw/bad.xml")); zip.write(byteArrayOf(0x01, 0x02, 0x00, 0x03)); zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/icon.svg")); zip.write(svgBytes); zip.closeEntry()
                zip.putNextEntry(ZipEntry("res/drawable/bg.9.png")); zip.write(pngBytes); zip.closeEntry()
            }
            val plainXml = assertIs<ApkArchiveEntryContent.Xml>(
                inspectApkArchiveEntryPreview(zipFile.path, "assets/config.xml")
            )
            assertTrue(plainXml.content.contains("<config"))
            assertNull(inspectApkArchiveEntryPreview(zipFile.path, "res/raw/bad.xml"))
            val svgContent = assertIs<ApkArchiveEntryContent.Image>(
                inspectApkArchiveEntryPreview(zipFile.path, "assets/icon.svg")
            )
            assertTrue(svgContent.isSvg)
            assertEquals(512, decoder.decode(ApkIconSource(svgContent.source.bytes())).width)
            assertNull(inspectApkArchiveEntryPreview(zipFile.path, "res/drawable/bg.9.png"))
        } finally {
            zipFile.delete()
        }
        Unit
    }

    private fun buildMinimalDex(descriptors: List<String>): ByteArray {
        val headerSize = 112
        val stringIdsOff = headerSize
        val stringIdsSize = descriptors.size
        val typeIdsOff = stringIdsOff + stringIdsSize * 4
        val typeIdsSize = descriptors.size
        val classDefsOff = typeIdsOff + typeIdsSize * 4
        val classDefsSize = descriptors.size
        val dataOff = classDefsOff + classDefsSize * 32

        val stringData = ByteArrayOutputStream()
        val stringOffsets = IntArray(descriptors.size)
        descriptors.forEachIndexed { idx, s ->
            stringOffsets[idx] = dataOff + stringData.size()
            stringData.write(s.length)
            stringData.write(s.toByteArray(Charsets.UTF_8))
            stringData.write(0)
        }

        val totalSize = dataOff + stringData.size()
        val out = ByteArray(totalSize)
        val buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), '\n'.code.toByte(), '0'.code.toByte(), '3'.code.toByte(), '5'.code.toByte(), 0))
        buf.putInt(32, totalSize)
        buf.putInt(36, headerSize)
        buf.putInt(56, stringIdsSize)
        buf.putInt(60, stringIdsOff)
        buf.putInt(64, typeIdsSize)
        buf.putInt(68, typeIdsOff)
        buf.putInt(96, classDefsSize)
        buf.putInt(100, classDefsOff)
        for (i in descriptors.indices) {
            buf.putInt(stringIdsOff + i * 4, stringOffsets[i])
            buf.putInt(typeIdsOff + i * 4, i)
            buf.putInt(classDefsOff + i * 32, i)
        }
        System.arraycopy(stringData.toByteArray(), 0, out, dataOff, stringData.size())
        return out
    }
}
