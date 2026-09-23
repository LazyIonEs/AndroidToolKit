package org.tool.kit.domain.repository

import org.tool.kit.domain.apk.*

interface ApkInformationRepository {
    /** 取得 aapt badging 原始文本；工具执行失败时抛出异常。 */
    suspend fun badging(path: String): String
    /** 读取文件大小和摘要，不依赖 APK 清单解析。 */
    suspend fun metadata(path: String): ApkFileMetadata
    /** 读取清单的文本转储；无法提取时返回 null，允许其他信息继续展示。 */
    suspend fun manifest(path: String): String?
    /** 按清单和资源路径读取编码后的图标；无可用图像时返回 null。 */
    suspend fun icon(path: String, manifest: String?, iconPath: String): ApkIconSource?
    /** Optional inspection failure must not discard basic APK information. */
    suspend fun archive(path: String): ApkArchiveInformation? = null
    suspend fun components(path: String): List<ApkComponent>? = null
}
