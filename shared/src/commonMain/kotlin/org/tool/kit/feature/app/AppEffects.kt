package org.tool.kit.feature.app

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

sealed interface UiMessage {
    data class Text(val value: String) : UiMessage
    data class Resource(val value: StringResource) : UiMessage
}
sealed interface SnackbarAction {
    data class OpenDirectory(val path: String?) : SnackbarAction
}
data class SnackbarMessage(
    val message: UiMessage,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val action: SnackbarAction? = null,
)
data class AppEffect(val originFeature: String, val operationId: Long, val effectId: Long, val snackbar: SnackbarMessage)

/** Single window mailbox; closed only with the app session, never by a Route. */
@OptIn(ExperimentalAtomicApi::class)
class AppEffectSink {
    private val sequence = AtomicLong(0)
    private val mailbox = Channel<AppEffect>(64)
    val effects = mailbox.receiveAsFlow()
    suspend fun send(origin: String, message: SnackbarMessage, operationId: Long = 0): Boolean {
        return try {
            mailbox.send(AppEffect(origin, operationId, sequence.addAndFetch(1), message))
            true
        } catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) { false }
    }
    fun close() = mailbox.close()
}

interface DesktopActionHandler {
    suspend fun openDirectory(path: String?)
    suspend fun openInstaller(path: String): Boolean
    suspend fun logFilePath(): String?
    fun browse(url: String)
    fun exitAfterInstall()
}
