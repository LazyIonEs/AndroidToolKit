package org.tool.kit.domain.repository

import org.tool.kit.domain.signing.SigningCredentials

/** 下拉列表仅使用路径和别名，选中后再读取密码。 */
data class SavedSigningProfile(val path: String, val alias: String)

interface SigningProfileRepository {
    suspend fun list(): List<SavedSigningProfile>
    suspend fun load(profile: SavedSigningProfile): SigningCredentials?

    /** 更新同一路径和别名的记录，返回按最近使用排序的列表。 */
    suspend fun save(credentials: SigningCredentials): List<SavedSigningProfile>
}
