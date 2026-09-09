package org.tool.kit.feature.app

import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

@Composable
fun AppEffectHost(sink: AppEffectSink, host: SnackbarHostState, actions: DesktopActionHandler) {
    LaunchedEffect(sink, host, actions) {
        var snackbarJob: Job? = null
        sink.effects.collect { effect ->
            snackbarJob?.cancel()
            snackbarJob = launch {
                val value = effect.snackbar
                val message = when (val text = value.message) {
                    is UiMessage.Text -> text.value
                    is UiMessage.Resource -> getString(text.value)
                }
                if (message.isBlank()) return@launch
                val result = host.showSnackbar(message, value.actionLabel, value.withDismissAction, value.duration)
                if (result == SnackbarResult.ActionPerformed) {
                    // This action is independent of subsequent snackbar replacement.
                    launch(this@LaunchedEffect.coroutineContext) {
                        when (val action = value.action) {
                            is SnackbarAction.OpenDirectory -> actions.openDirectory(action.path)
                            null -> Unit
                        }
                    }
                }
            }
        }
    }
}
