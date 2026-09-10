package org.tool.kit.data.source

import org.tool.kit.domain.preferences.*

/** 设置持久化边界；读取与写入由仓库串行协调，存储实现不直接发布 UI 状态。 */
interface PreferencesStorage {
    suspend fun read(): PreferencesSnapshot
    suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot)
}

