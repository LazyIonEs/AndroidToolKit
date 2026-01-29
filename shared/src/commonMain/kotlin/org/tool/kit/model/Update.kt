package org.tool.kit.model

/**
 * * author      : LazyIonEs
 * * description : 描述
 * * createDate  : 2025/9/17 19:26
 */
data class Update(
    val version: String,
    val htmlUrl: String,
    val createdAt: String,
    val body: String?,
    val assets: List<Asset>
)