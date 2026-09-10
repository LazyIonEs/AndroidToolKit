package org.tool.kit.data.source

import org.tool.kit.domain.preferences.*

interface PreferencesStorage {
    suspend fun read(): PreferencesSnapshot
    suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot)
}

