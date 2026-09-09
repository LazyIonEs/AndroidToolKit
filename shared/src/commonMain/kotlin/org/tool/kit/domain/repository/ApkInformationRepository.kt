package org.tool.kit.domain.repository

import org.tool.kit.domain.apk.*

interface ApkInformationRepository {
    suspend fun badging(path: String): String
    suspend fun metadata(path: String): ApkFileMetadata
    suspend fun manifest(path: String): String?
    suspend fun icon(path: String, manifest: String?, iconPath: String): ApkIconSource?
}
