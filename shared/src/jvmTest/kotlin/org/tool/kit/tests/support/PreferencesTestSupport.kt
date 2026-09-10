package org.tool.kit.tests.support

import kotlinx.coroutines.flow.*
import org.tool.kit.domain.preferences.*

internal class ImmediatePreferencesRepository(initial: PreferencesSnapshot = PreferencesSnapshot(ready = true)) : PreferencesRepository {
    private val snapshot = MutableStateFlow(initial)
    fun completeInitialLoad(enabled: Boolean) { snapshot.value = snapshot.value.copy(ready = true, isStartCheckUpdate = enabled) }
    override val state = snapshot.asStateFlow()
    override fun change(change: PreferenceChange): PreferencesSnapshot = snapshot.value.changed(change).also { snapshot.value = it }
    override suspend fun awaitReady() = state.first { it.ready }
}
