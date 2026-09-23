package org.tool.kit.domain.repository

import org.tool.kit.domain.apk.ApkBuildWorkspace
import org.tool.kit.domain.apk.BuildApkRequest

interface ApkToolRepository {
    /** 在调用方提供的隔离工作区解码模板，返回绑定本次请求的构建会话。 */
    suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession
}

/** A session owns only one decoded template; no UI state or preferences enter this boundary. */
interface ApkBuildSession {
    /** 按提交参数更新清单中的包名和 SDK 版本。 */
    suspend fun updateManifest()
    /** 更新应用名称资源。 */
    suspend fun updateAppName()
    /** 把提交时指定的图标复制到各密度资源目录。 */
    suspend fun copyIcon()
    /** 将版本和 SDK 信息写入构建元数据，供后续重新打包使用。 */
    suspend fun saveMetadata(versionCode: Int)
    /** 把本会话的解码目录重新构建为请求的 APK 输出。 */
    suspend fun build()
    /** 读取构建输出的字节大小。 */
    suspend fun outputSize(): Long
}

interface ApkBuildWorkspaces {
    /** 分配本次构建的临时目录，在 block 结束、失败或取消后回收该目录。 */
    suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T
}
