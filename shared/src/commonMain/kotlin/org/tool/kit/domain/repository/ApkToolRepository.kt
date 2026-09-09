package org.tool.kit.domain.repository

import org.tool.kit.domain.apk.ApkBuildWorkspace
import org.tool.kit.domain.apk.BuildApkRequest

interface ApkToolRepository {
    suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession
}

/** A session owns only one decoded template; no UI state or preferences enter this boundary. */
interface ApkBuildSession {
    suspend fun updateManifest()
    suspend fun updateAppName()
    suspend fun copyIcon()
    suspend fun saveMetadata(versionCode: Int)
    suspend fun build()
    suspend fun outputSize(): Long
}

interface ApkBuildWorkspaces {
    suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T
}
