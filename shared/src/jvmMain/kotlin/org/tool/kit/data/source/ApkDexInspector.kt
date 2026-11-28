package org.tool.kit.data.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

private const val MAX_DEX_BYTES = 64 * 1024 * 1024
private val DEX_ENTRY_PATTERN = Regex("classes\\d*\\.dex")

/**
 * Scan root `classes*.dex` entries in an APK and collect distinct class package prefixes.
 */
internal suspend fun inspectApkDexPackages(path: String): Set<String>? = try {
    withContext(Dispatchers.IO) {
        ZipFile(path).use { zip ->
            val packages = linkedSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                currentCoroutineContext().ensureActive()
                val entry = entries.nextElement()
                if (entry.isDirectory || !DEX_ENTRY_PATTERN.matches(entry.name)) continue
                if (entry.size !in 0L..MAX_DEX_BYTES.toLong()) continue
                val bytes = try {
                    zip.getInputStream(entry).use { it.readNBytes(MAX_DEX_BYTES + 1) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    continue
                }
                if (bytes.size > MAX_DEX_BYTES) continue
                try {
                    packages.addAll(readDexClassPackages(bytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Ignore malformed individual DEX entries while keeping valid ones.
                }
            }
            packages
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/**
 * Parse a DEX binary header and `class_defs` table without allocating full class models,
 * returning distinct package names (e.g. `com.google.android.material`).
 */
internal fun readDexClassPackages(bytes: ByteArray): Set<String> {
    if (bytes.size < 112) return emptySet()
    if (bytes[0] != 'd'.code.toByte() || bytes[1] != 'e'.code.toByte() ||
        bytes[2] != 'x'.code.toByte() || bytes[3] != '\n'.code.toByte() ||
        bytes[7] != 0.toByte()
    ) {
        return emptySet()
    }
    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val stringIdsSize = buf.getInt(56)
    val stringIdsOff = buf.getInt(60)
    val typeIdsSize = buf.getInt(64)
    val typeIdsOff = buf.getInt(68)
    val classDefsSize = buf.getInt(96)
    val classDefsOff = buf.getInt(100)

    if (!validRange(stringIdsOff, stringIdsSize, 4, bytes.size) ||
        !validRange(typeIdsOff, typeIdsSize, 4, bytes.size) ||
        !validRange(classDefsOff, classDefsSize, 32, bytes.size)
    ) {
        return emptySet()
    }

    val packages = linkedSetOf<String>()
    for (i in 0 until classDefsSize) {
        val classIdx = buf.getInt(classDefsOff + i * 32)
        if (classIdx !in 0 until typeIdsSize) continue
        val descriptorIdx = buf.getInt(typeIdsOff + classIdx * 4)
        if (descriptorIdx !in 0 until stringIdsSize) continue
        val strDataOff = buf.getInt(stringIdsOff + descriptorIdx * 4)
        if (strDataOff !in 0 until bytes.size) continue
        val descriptor = readDexMutf8String(bytes, strDataOff) ?: continue
        if (descriptor.length > 3 && descriptor[0] == 'L' && descriptor[descriptor.length - 1] == ';') {
            val slash = descriptor.lastIndexOf('/')
            if (slash > 1) {
                packages.add(descriptor.substring(1, slash).replace('/', '.'))
            }
        }
    }
    return packages
}

private fun validRange(offset: Int, count: Int, stride: Int, limit: Int): Boolean {
    if (offset < 0 || count < 0) return false
    if (count == 0) return true
    val end = offset.toLong() + count.toLong() * stride.toLong()
    return end in 0L..limit.toLong()
}

private fun readDexMutf8String(bytes: ByteArray, offset: Int): String? {
    var pos = offset
    // Skip ULEB128 UTF-16 length prefix (up to 5 bytes).
    for (step in 0 until 5) {
        if (pos >= bytes.size) return null
        val b = bytes[pos++].toInt() and 0xFF
        if ((b and 0x80) == 0) break
    }
    val out = ByteArrayOutputStream(64)
    while (pos < bytes.size) {
        val b = bytes[pos++].toInt() and 0xFF
        if (b == 0) return out.toString(Charsets.UTF_8)
        out.write(b)
    }
    return null
}
