package org.tool.kit.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.commons.codec.digest.DigestUtils
import org.tool.kit.domain.apk.ApkFileMetadata
import org.tool.kit.domain.repository.ApkInformationRepository
import org.tool.kit.data.source.Aapt2DataSource
import org.tool.kit.data.source.ApkIconDataSource
import java.io.File
import java.io.FileInputStream

class JvmApkInformationRepository(
    private val aapt: Aapt2DataSource,
    private val icons: ApkIconDataSource,
    private val io: CoroutineDispatcher,
) : ApkInformationRepository {
    override suspend fun badging(path: String) = aapt.badging(path)
    override suspend fun manifest(path: String) = aapt.manifest(path)
    override suspend fun icon(path: String, manifest: String?, iconPath: String) = icons.read(manifest, path, iconPath)
    override suspend fun metadata(path: String) = withContext(io) {
        val file = File(path)
        val size = file.length()
        ApkFileMetadata(size, FileInputStream(file).use { DigestUtils.md5Hex(it) })
    }
}
