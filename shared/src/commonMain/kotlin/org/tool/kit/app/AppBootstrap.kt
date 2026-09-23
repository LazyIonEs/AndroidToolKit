package org.tool.kit.app

import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository

/** Prepared before the first visible composition. Holds only the one-time capacity seed. */
class AppBootstrap(private val preferences: PreferencesRepository, private val storage: StorageRepository) {
    var storageCapacity = StorageCapacity(0, 0)
        private set
    /** 首屏显示前等待设置加载并读取容量种子，避免界面先展示未初始化配置。 */
    suspend fun prepare() {
        preferences.awaitReady()
        storageCapacity = storage.readCapacity()
    }
}
