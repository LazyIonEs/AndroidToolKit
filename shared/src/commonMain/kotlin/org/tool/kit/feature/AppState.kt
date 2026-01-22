package org.tool.kit.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import org.tool.kit.feature.signature.SignatureInformationNavKey
import org.tool.kit.navigation.NavigationState
import org.tool.kit.navigation.TOP_LEVEL_NAV_ITEMS
import org.tool.kit.navigation.rememberNavigationState

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 16:58
 */
@Composable
fun rememberAppState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): AppState {
    val navigationState =
        rememberNavigationState(SignatureInformationNavKey, TOP_LEVEL_NAV_ITEMS.keys)

    return remember(
        navigationState,
        coroutineScope,
    ) {
        AppState(
            navigationState = navigationState,
            coroutineScope = coroutineScope,
        )
    }
}

@Stable
class AppState(
    val navigationState: NavigationState,
    coroutineScope: CoroutineScope,
) {

}