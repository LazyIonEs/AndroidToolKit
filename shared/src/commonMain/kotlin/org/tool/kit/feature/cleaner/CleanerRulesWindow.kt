package org.tool.kit.feature.cleaner

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.cleaner_rules
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme
import java.awt.Dimension

/** A single native editor window; the main cleaner remains available while editing. */
@Composable
internal fun CleanerRulesWindow(
    state: CleanerRulesUiState,
    viewModel: CleanerRulesViewModel,
    useDarkTheme: Boolean,
    focusRequest: Int = 0,
    onDismiss: () -> Unit,
    onSaved: (tryRun: Boolean) -> Unit,
) {
    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.Center),
    )
    Window(
        onCloseRequest = { if (!state.saving) onDismiss() },
        state = windowState,
        title = stringResource(Res.string.cleaner_rules),
        icon = painterResource(Res.drawable.icon),
    ) {
        SideEffect { window.minimumSize = Dimension(720, 520) }
        LaunchedEffect(focusRequest) {
            if (focusRequest > 0) {
                windowState.isMinimized = false
                window.toFront()
                window.requestFocus()
            }
        }
        AppTheme(useDarkTheme) {
            CleanerRulesWindowContent(state, viewModel, onDismiss, onSaved)
        }
    }
}

/** Shared editor behavior for the native window and offscreen UI rendering. */
@Composable
internal fun CleanerRulesWindowContent(
    state: CleanerRulesUiState,
    viewModel: CleanerRulesViewModel,
    onDismiss: () -> Unit,
    onSaved: (tryRun: Boolean) -> Unit,
) {
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) {
        if (state.saved) currentOnSaved(state.tryRun)
    }
    Box(Modifier.fillMaxSize().testTag("cleaner-rules-window-content")) {
        CleanerRulesScreen(
            state, viewModel,
            onCancel = { if (!state.saving) onDismiss() },
            onSave = viewModel::save
        )
    }
}
