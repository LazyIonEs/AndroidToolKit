package org.tool.kit.data.source

import com.android.ide.common.pagealign.readElfAlignmentProblems
import com.android.zipflinger.ZipRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.apk.*
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Uses the same ZIP reader and ELF checks as APK Analyzer, without extracting files to disk. */
internal suspend fun inspectApkArchive(path: String): ApkArchiveInformation? = try {
    val file = File(path)
    ZipRepo(file.toPath()).use { zip ->
        val files = mutableListOf<ApkArchiveFile>()
        val libraries = mutableListOf<ApkNativeLibrary>()
        val nativePath = Regex("lib/([^/]+)/[^/]+\\.so")
        zip.entries.values.filterNot { it.isDirectory }.forEach { entry ->
            currentCoroutineContext().ensureActive()
            val abi = nativePath.matchEntire(entry.name)?.groupValues?.get(1)
            val category = when {
                abi != null -> ApkFileCategory.Native
                entry.name.endsWith(".dex") -> ApkFileCategory.Dex
                entry.name.startsWith("res/") || entry.name == "resources.arsc" -> ApkFileCategory.Resources
                entry.name.startsWith("assets/") -> ApkFileCategory.Assets
                entry.name.startsWith("META-INF/") || entry.name == "AndroidManifest.xml" -> ApkFileCategory.Metadata
                else -> ApkFileCategory.Other
            }
            files += ApkArchiveFile(entry.name, entry.uncompressedSize, entry.compressedSize, category)
            if (abi != null) {
                val elf = try { zip.getInputStream(entry.name).use { inspectElfAlignment(it, entry.uncompressedSize) } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ApkAlignment.Unknown }
                val zipAlignment = when {
                    entry.isCompressed -> ApkAlignment.NotApplicable
                    entry.payloadLocation.first % 16384 == 0L -> ApkAlignment.Aligned
                    else -> ApkAlignment.Unaligned
                }
                libraries += ApkNativeLibrary(entry.name, abi, entry.uncompressedSize, entry.compressedSize,
                    entry.isCompressed, elf, zipAlignment)
            }
        }
        ApkArchiveInformation(files, libraries.sortedWith(compareBy({ it.abi }, { it.path })),
            (file.length() - files.sumOf { it.compressedSize }).coerceAtLeast(0))
    }
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

/** Validate boundaries before calling the official verifier, which otherwise accepts partial headers. */
internal suspend fun inspectElfAlignment(input: InputStream, size: Long = Long.MAX_VALUE): ApkAlignment {
    val header = input.readNBytes(64)
    if (header.size != 64 || !header.copyOf(4).contentEquals(byteArrayOf(0x7f, 69, 76, 70)) || header[6].toInt() != 1) return ApkAlignment.Unknown
    if (header[4].toInt() == 1) return ApkAlignment.NotApplicable
    // Android's verifier supports little-endian ELF64; never label unsupported formats as aligned.
    if (header[4].toInt() != 2 || header[5].toInt() != 1) return ApkAlignment.Unknown
    val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    val offset = data.getLong(32)
    val stride = data.getShort(54).toInt() and 0xffff
    val count = data.getShort(56).toInt() and 0xffff
    val end = offset + stride.toLong() * count
    val sectionOffset = data.getLong(40)
    val sectionStride = data.getShort(58).toInt() and 0xffff
    val sectionCount = data.getShort(60).toInt() and 0xffff
    val sectionEnd = sectionOffset + sectionStride.toLong() * sectionCount
    if (offset < 64 || count == 0 || count >= 0x8000 || stride < 56 || stride >= 0x8000 || end > 1024 * 1024 || end > size || end < offset) return ApkAlignment.Unknown
    if (sectionCount > 0 && (sectionCount >= 0x8000 || sectionStride < 64 || sectionStride >= 0x8000 || sectionOffset < 64 || sectionEnd < sectionOffset || sectionEnd > size)) return ApkAlignment.Unknown
    val context = currentCoroutineContext()
    context.ensureActive()
    val remaining = input.readNBytes((end - 64).toInt())
    if (remaining.size != end.toInt() - 64) return ApkAlignment.Unknown
    val prefix = header + remaining
    val programs = ByteBuffer.wrap(prefix).order(ByteOrder.LITTLE_ENDIAN)
    val loads = (0 until count).map { (offset + it.toLong() * stride).toInt() }.filter { programs.getInt(it) == 1 }
    if (loads.isEmpty()) return ApkAlignment.Unknown
    // ELF allows p_align 0/1, but neither guarantees a 16 KB LOAD alignment.
    if (loads.any { programs.getLong(it + 48) < 16384 }) return ApkAlignment.Unaligned
    val replay = SequenceInputStream(ByteArrayInputStream(prefix, 4, prefix.size - 4), input)
    // Make truncated reads fail instead of becoming a false positive; check cancellation during skips.
    val checked = object : FilterInputStream(replay) {
        override fun read(): Int { context.ensureActive(); return super.read().also { if (it < 0) throw EOFException() } }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            context.ensureActive(); return super.read(b, off, len).also { if (it < 0) throw EOFException() }
        }
        override fun skip(n: Long): Long { context.ensureActive(); return super.skip(minOf(n, 65536)) }
    }
    return try {
        val problems = readElfAlignmentProblems(checked) ?: return ApkAlignment.Unknown
        if (problems.isEmpty()) ApkAlignment.Aligned else ApkAlignment.Unaligned
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { ApkAlignment.Unknown }
}
