package org.tool.kit.feature.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.tool.kit.LocalIsAppDarkTheme

/** Keeps feature loading inside the navigation content, leaving the app rail available. */
@Composable
fun FeaturePage(
    busy: Boolean,
    useDarkTheme: Boolean = LocalIsAppDarkTheme.current,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        LoadingAnimate(busy, useDarkTheme)
    }
}
