package org.tool.kit.core.validation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main-thread owner. Cancellation is best effort; revision also rejects non-cooperative results. */
class LatestRequest(private val scope: CoroutineScope) {
    private var revision = 0L
    private var job: Job? = null

    fun cancel() {
        revision++
        job?.cancel()
        job = null
    }

    fun <T> launch(block: suspend () -> T, onResult: (T) -> Unit) {
        cancel()
        val requestRevision = revision
        job = scope.launch {
            val result = block()
            if (isActive && requestRevision == revision) onResult(result)
        }
    }
}
