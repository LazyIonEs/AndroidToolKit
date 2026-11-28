package org.tool.kit.data.source

import com.android.tools.apk.analyzer.BinaryXmlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.tool.kit.domain.apk.ApkArchiveEntryContent
import org.tool.kit.domain.apk.ApkArchiveFile
import org.tool.kit.domain.apk.ApkArchivePreviewKind
import org.tool.kit.domain.apk.ApkFileCategory
import org.tool.kit.domain.apk.ApkIconSource
import org.tool.kit.domain.apk.previewKind
import java.util.zip.ZipFile

private const val MAX_PREVIEW_XML_BYTES = 16 * 1024 * 1024
private const val MAX_PREVIEW_IMAGE_BYTES = 32 * 1024 * 1024

/**
 * Decode an APK archive image (excluding `.9.png`) or XML entry on demand without extracting to disk.
 */
internal suspend fun inspectApkArchiveEntryPreview(
    apkPath: String,
    entryPath: String,
): ApkArchiveEntryContent? {
    val kind = ApkArchiveFile(entryPath, 0L, 0L, ApkFileCategory.Other).previewKind() ?: return null
    val maxBytes = when (kind) {
        ApkArchivePreviewKind.Xml -> MAX_PREVIEW_XML_BYTES
        ApkArchivePreviewKind.Image -> MAX_PREVIEW_IMAGE_BYTES
    }
    return try {
        withContext(Dispatchers.IO) {
            ZipFile(apkPath).use { zip ->
                currentCoroutineContext().ensureActive()
                val entry = zip.getEntry(entryPath) ?: return@use null
                if (entry.isDirectory || entry.size !in 0L..maxBytes.toLong()) return@use null
                val bytes = zip.getInputStream(entry).use { it.readNBytes(maxBytes + 1) }
                if (bytes.size > maxBytes) return@use null
                currentCoroutineContext().ensureActive()
                when (kind) {
                    ApkArchivePreviewKind.Xml -> decodeXmlEntry(zip, bytes)
                    ApkArchivePreviewKind.Image -> decodeImageEntry(entryPath, bytes)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

private fun decodeXmlEntry(zip: ZipFile, bytes: ByteArray): ApkArchiveEntryContent.Xml? {
    if (isBinaryAxml(bytes)) {
        val xml = BinaryXmlParser.decodeXml(bytes, buildResourceIdResolver(zip)).toString(Charsets.UTF_8)
        return if (xml.isNotBlank()) ApkArchiveEntryContent.Xml(xml) else null
    }
    if (bytes.any { it == 0.toByte() }) return null
    val text = stripBomAndLeadingWhitespace(bytes.toString(Charsets.UTF_8))
    return if (text.startsWith('<')) ApkArchiveEntryContent.Xml(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")) else null
}

private fun decodeImageEntry(entryPath: String, bytes: ByteArray): ApkArchiveEntryContent.Image? {
    if (entryPath.lowercase().endsWith(".svg")) {
        if (bytes.any { it == 0.toByte() }) return null
        val prefix = bytes.copyOf(minOf(bytes.size, 2048)).toString(Charsets.UTF_8)
        if (!prefix.contains("<svg", ignoreCase = true)) return null
        return ApkArchiveEntryContent.Image(ApkIconSource(bytes), isSvg = true)
    }
    Image.makeFromEncoded(bytes).use { /* Validate image bytes before publishing. */ }
    return ApkArchiveEntryContent.Image(ApkIconSource(bytes), isSvg = false)
}

private fun isBinaryAxml(bytes: ByteArray): Boolean =
    bytes.size >= 8 &&
        bytes[0] == 0x03.toByte() &&
        bytes[1] == 0x00.toByte() &&
        bytes[2] == 0x08.toByte() &&
        bytes[3] == 0x00.toByte()

private fun stripBomAndLeadingWhitespace(text: String): String =
    text.removePrefix("\uFEFF").trimStart()
