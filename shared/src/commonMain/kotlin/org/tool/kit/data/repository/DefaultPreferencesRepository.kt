package org.tool.kit.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.source.PreferencesStorage
import org.tool.kit.domain.preferences.*

/** One application-session owner. Call change on Main; only this worker writes physical settings. */
class DefaultPreferencesRepository(private val source: PreferencesStorage, dispatchers: AppDispatchers) : PreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private data class Write(val revision: Long, val change: PreferenceChange)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()
    private val beforeReady = mutableListOf<PreferenceChange>()
    private val _state = MutableStateFlow(PreferencesSnapshot())
    override val state = _state.asStateFlow()

    init {
        scope.launch {
            var stored = try { source.read() } catch (failure: Exception) {
                ready.completeExceptionally(failure)
                return@launch
            }
            val accepted = _state.value
            var loaded = stored
            beforeReady.forEach { loaded = loaded.changed(it) }
            beforeReady.clear()
            _state.value = loaded.copy(ready = true, revision = accepted.revision,
                outputPathVersion = accepted.outputPathVersion)
            ready.complete(Unit)
            val pending = ArrayDeque<Write>()
            for (write in writes) {
                stored = stored.changed(write.change)
                pending.addLast(write)
                while (pending.isNotEmpty()) {
                    val next = pending.first()
                    try {
                        source.write(next.change, stored)
                        pending.removeFirst()
                        _state.value = _state.value.copy(persistedRevision = next.revision, writeFailure = null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        _state.value = _state.value.copy(writeFailure = failure::class.simpleName)
                        // Keep the failed field ahead of later writes; acknowledgements stay contiguous.
                        break
                    }
                }
            }
        }
    }

    override fun change(change: PreferenceChange): PreferencesSnapshot {
        check(scope.isActive) { "Preferences session closed" }
        val old = _state.value
        val next = old.changed(change).copy(revision = old.revision + 1,
            outputPathVersion = old.outputPathVersion + if (change is PreferenceChange.OutputPath && change.value != old.userData.defaultOutputPath) 1 else 0)
        // Unlimited here preserves every keystroke/save event without blocking the UI.
        if (!old.ready) beforeReady += change
        _state.value = next
        check(writes.trySend(Write(next.revision, change)).isSuccess) { "Preferences writer closed" }
        return next
    }

    override suspend fun awaitReady(): PreferencesSnapshot { ready.await(); return state.value }
    fun close() { writes.close(); ready.cancel(); scope.cancel() }
}
