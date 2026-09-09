package org.tool.kit.app

import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository

/** Prepared before the first visible composition. Holds only the one-time capacity seed. */
class AppBootstrap(private val preferences: PreferencesRepository, private val storage: StorageRepository) {
    var storageCapacity = StorageCapacity(0, 0)
        private set
    suspend fun prepare() {
        preferences.awaitReady()
        storageCapacity = storage.readCapacity()
    }
}
