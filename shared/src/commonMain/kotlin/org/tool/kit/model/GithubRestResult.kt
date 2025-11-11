package org.tool.kit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/**
 * * author      : Eddy
 * * description : 描述
 * * createDate  : 2025/9/9 17:42
 */
data class GithubRestResult<T>(
    val isSuccess: Boolean,
    val msg: StringResource?,
    val data: T,
)

@Serializable
data class GithubRestLatestResult(
    val url: String,
    @SerialName("html_url")
    val htmlUrl: String,
    @SerialName("assets_url")
    val assetsUrl: String,
    @SerialName("upload_url")
    val uploadUrl: String,
    @SerialName("tarball_url")
    val tarballUrl: String?,
    @SerialName("zipball_url")
    val zipballUrl: String?,
    val id: Long,
    @SerialName("node_id")
    val nodeId: String,
    @SerialName("tag_name")
    val tagName: String,
    @SerialName("target_commitish")
    val targetCommitish: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val immutable: Boolean,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("published_at")
    val publishedAt: String?,
    @SerialName("updated_at")
    val updatedAt: String?,
    val assets: MutableList<Asset>
)

@Serializable
data class Asset(
    val url: String,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val id: Long,
    @SerialName("node_id")
    val nodeId: String,
    val name: String,
    val label: String?,
    val state: String,
    @SerialName("content_type")
    val contentType: String,
    val size: Long,
    val digest: String?,
    @SerialName("download_count")
    val downloadCount: Long,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String?,
)