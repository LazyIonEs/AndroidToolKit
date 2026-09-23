package org.tool.kit.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.codec.binary.Hex
import org.tool.kit.domain.apk.ApkFileMetadata
import org.tool.kit.domain.repository.ApkInformationRepository
import org.tool.kit.data.source.Aapt2DataSource
import org.tool.kit.data.source.ApkIconDataSource
import org.tool.kit.data.source.inspectApkArchive
import org.tool.kit.data.source.inspectApkComponents
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class JvmApkInformationRepository(
    private val aapt: Aapt2DataSource,
    private val icons: ApkIconDataSource,
    private val io: CoroutineDispatcher,
) : ApkInformationRepository {
    override suspend fun badging(path: String) = aapt.badging(path)
    override suspend fun manifest(path: String) = aapt.manifest(path)
    override suspend fun icon(path: String, manifest: String?, iconPath: String) = icons.read(manifest, path, iconPath)
    override suspend fun archive(path: String) = withContext(io) { inspectApkArchive(path) }
    override suspend fun components(path: String) = withContext(io) { inspectApkComponents(path) }
    /** 一次流式读取同时计算两种文件摘要，不把整个 APK 载入内存；换包时可及时取消。 */
    override suspend fun metadata(path: String) = withContext(io) {
        val file = File(path)
        val size = file.length()
        val md5 = MessageDigest.getInstance("MD5")
        val sha256 = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                md5.update(buffer, 0, count)
                sha256.update(buffer, 0, count)
            }
        }
        ApkFileMetadata(size, Hex.encodeHexString(md5.digest()), Hex.encodeHexString(sha256.digest()))
    }
}
