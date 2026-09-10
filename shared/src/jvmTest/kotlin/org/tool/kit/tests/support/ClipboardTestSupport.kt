package org.tool.kit.tests.support

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.tool.kit.feature.app.ClipboardWriter

internal class ControlledClipboard : ClipboardWriter {
    val values = mutableListOf<String>()
    val done = mutableListOf<CompletableDeferred<Unit>>()
    override suspend fun write(value: String) { withContext(NonCancellable) {
        values += value
        CompletableDeferred<Unit>().also { done += it }.await()
    } }
}
