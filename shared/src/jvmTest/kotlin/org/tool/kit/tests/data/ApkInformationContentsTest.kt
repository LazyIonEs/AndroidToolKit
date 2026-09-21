package org.tool.kit.tests.data

import kotlinx.coroutines.*
import org.junit.Test
import org.tool.kit.data.source.inspectApkArchive
import org.tool.kit.data.source.inspectElfAlignment
import org.tool.kit.data.source.readApkComponents
import org.tool.kit.domain.apk.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class ApkInformationContentsTest {
    @Test fun componentsRespectTreeBoundariesAliasesAndExplicitExportDeclarations() {
        val manifest = """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example">
              <application android:process=":app">
                <activity android:name=".Main" android:exported="true" android:process=":ui">
                  <intent-filter><action android:name="NESTED_NAME" /></intent-filter>
                </activity>
                <activity-alias android:name="Shortcut" android:targetActivity=".Main" android:exported="false" />
                <service android:name="com.remote.SyncService" android:exported="@7f000001" />
                <receiver android:name=".Receiver" android:exported="true" />
                <provider android:name=".Provider" />
              </application>
              <queries><provider android:authorities="external.provider" /></queries>
            </manifest>
        """.trimIndent()
        for (separator in listOf("\n", "\r\n", "\r")) {
            val parsed = assertNotNull(readApkComponents(manifest.replace("\n", separator).toByteArray()))
            assertEquals(5, parsed.size)
            assertEquals(ApkComponent("org.example.Main", ApkComponentType.Activity, ApkExportedDeclaration.Enabled, "org.example:ui"), parsed[0])
            assertEquals(ApkComponent("org.example.Shortcut", ApkComponentType.ActivityAlias, ApkExportedDeclaration.Disabled, "org.example:ui", "org.example.Main"), parsed[1])
            assertEquals("com.remote.SyncService", parsed[2].name)
            assertEquals(ApkExportedDeclaration.Unknown, parsed[2].exported)
            assertEquals("org.example:app", parsed[2].process)
            assertEquals(ApkExportedDeclaration.Enabled, parsed[3].exported)
            assertEquals(ApkExportedDeclaration.Unspecified, parsed[4].exported)
        }
        assertFails { readApkComponents("<wrong />".toByteArray()) }
        assertEquals(emptyList(), readApkComponents("<manifest><application /></manifest>".toByteArray()))
    }

    @Test fun elfChecksLoadAlignmentRelroAndRejectsUnsupportedOrMalformedHeaders() = runBlocking {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN)) {
            assertEquals(ApkAlignment.Aligned, inspectElfAlignment(elf(order = order).inputStream()))
            assertEquals(ApkAlignment.Unaligned, inspectElfAlignment(elf(alignment = 4096, order = order).inputStream()))
            assertEquals(ApkAlignment.Unaligned, inspectElfAlignment(elf(relroEnd = 4096, order = order).inputStream()))
        }
        assertEquals(ApkAlignment.Unknown, inspectElfAlignment(elf(order = ByteOrder.BIG_ENDIAN).inputStream()))
        assertEquals(ApkAlignment.Unaligned, inspectElfAlignment(elf(alignment = 0).inputStream()))
        assertEquals(ApkAlignment.Unknown, inspectElfAlignment(byteArrayOf(1, 2).inputStream()))
        assertEquals(ApkAlignment.Unknown, inspectElfAlignment(elf().copyOf(70).inputStream()))
        val hugeOffset = elf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putLong(32, Long.MAX_VALUE) }
        assertEquals(ApkAlignment.Unknown, inspectElfAlignment(hugeOffset.inputStream()))
        val missingLoads = elf().also { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(64, 0) }
        assertEquals(ApkAlignment.Unknown, inspectElfAlignment(missingLoads.inputStream()))
        val elf32 = ByteArray(84)
        ByteBuffer.wrap(elf32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(byteArrayOf(0x7f, 69, 76, 70, 1, 1, 1))
            putInt(28, 52); putShort(42, 32); putShort(44, 1); putInt(52, 1); putInt(80, 4096)
        }
        assertEquals(ApkAlignment.NotApplicable, inspectElfAlignment(elf32.inputStream()))
    }

    @Test fun archiveAccountsForEveryByteAndDistinguishesCompressedStoredAndInvalidLibraries() = runBlocking {
        val file = Files.createTempFile("apk-contents 中文", ".apk").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                // First stored entry: padding its local header places its payload at exactly 16 KB.
                val path = "lib/arm64-v8a/libaligned.so"
                val extraSize = 16384 - 30 - path.toByteArray().size
                val extra = ByteBuffer.allocate(extraSize).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort(0xaffe.toShort()).putShort((extraSize - 4).toShort()).array()
                zip.entry(path, elf(), stored = true, extra = extra)
                zip.entry("lib/x86_64/libunaligned.so", elf(alignment = 4096), stored = true)
                zip.entry("lib/arm64-v8a/libcompressed.so", elf())
                zip.entry("lib/armeabi-v7a/libbroken.so", byteArrayOf(1, 2))
                zip.entry("classes.dex", ByteArray(4096) { 7 })
                zip.entry("resources.arsc", ByteArray(32))
                zip.entry("res/raw/a", ByteArray(17))
                zip.entry("assets/data", ByteArray(128))
                zip.entry("META-INF/CERT.RSA", ByteArray(35))
                zip.entry("AndroidManifest.xml", ByteArray(50))
                zip.entry("other.bin", ByteArray(23))
                zip.putNextEntry(ZipEntry("empty/")); zip.closeEntry()
            }
            val result = assertNotNull(inspectApkArchive(file.path))
            assertEquals(11, result.files.size)
            assertEquals(file.length(), result.files.sumOf { it.compressedSize } + result.overheadBytes)
            assertTrue(result.overheadBytes >= 16384)
            assertEquals(ApkFileCategory.entries.toSet(), result.files.map { it.category }.toSet())
            assertEquals(4, result.nativeLibraries.size)
            val byName = result.nativeLibraries.associateBy { it.path.substringAfterLast('/') }
            assertEquals(ApkAlignment.Aligned, byName.getValue("libaligned.so").elfAlignment)
            assertEquals(ApkAlignment.Aligned, byName.getValue("libaligned.so").zipAlignment)
            assertEquals(ApkAlignment.Unaligned, byName.getValue("libunaligned.so").elfAlignment)
            assertEquals(ApkAlignment.Unaligned, byName.getValue("libunaligned.so").zipAlignment)
            assertEquals(ApkAlignment.NotApplicable, byName.getValue("libcompressed.so").zipAlignment)
            assertEquals(ApkAlignment.Unknown, byName.getValue("libbroken.so").elfAlignment)
            assertTrue(result.files.first { it.path == "classes.dex" }.compressedSize < 4096)
            assertTrue(file.delete())
        } finally { file.delete() }
    }

    @Test fun corruptArchiveIsUnavailableAndCancellationIsPropagated() = runBlocking {
        val file = Files.createTempFile("apk-unavailable", ".apk").toFile()
        try {
            file.writeText("bad archive")
            assertNull(inspectApkArchive(file.path))
            var cancellationObserved = false
            val job = launch {
                val request = currentCoroutineContext().job
                val input = object : ByteArrayInputStream(elf()) {
                    override fun readNBytes(len: Int): ByteArray {
                        val bytes = super.readNBytes(len)
                        if (len == 64) request.cancel()
                        return bytes
                    }
                }
                assertFailsWith<CancellationException> { inspectElfAlignment(input) }
                cancellationObserved = true
            }
            job.join()
            assertTrue(cancellationObserved)
        } finally { file.delete() }
    }

    private fun elf(alignment: Long = 16384, relroEnd: Long = 16384, order: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray =
        ByteBuffer.allocate(240).order(order).apply {
            put(byteArrayOf(0x7f, 69, 76, 70, 2, if (order == ByteOrder.LITTLE_ENDIAN) 1 else 2, 1))
            putShort(16, 3); putShort(18, 183); putInt(20, 1)
            putLong(32, 64); putLong(40, 176); putShort(52, 64); putShort(54, 56); putShort(56, 2); putShort(58, 64); putShort(60, 1)
            putInt(64, 1); putLong(104, 32768); putLong(112, alignment)
            putInt(120, 0x6474e552); putLong(136, 0); putLong(160, relroEnd)
            putLong(192, 8192); putLong(208, 32)
        }.array()

    private fun ZipOutputStream.entry(path: String, bytes: ByteArray, stored: Boolean = false, extra: ByteArray? = null) {
        putNextEntry(ZipEntry(path).apply {
            if (stored) { method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size; crc = CRC32().apply { update(bytes) }.value }
            if (extra != null) this.extra = extra
        })
        write(bytes); closeEntry()
    }
}
