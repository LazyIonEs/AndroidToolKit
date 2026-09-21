@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.*

/** The owner saves expansion with its list state; only an explicit open requests input focus. */
@Composable
internal fun ApkExpandableSearch(
    query: String,
    onQuery: (String) -> Unit,
    enabled: Boolean,
    label: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    tag: String = "apk-detail-search",
    header: @Composable RowScope.() -> Unit
) {
    val requester = remember { FocusRequester() }
    val focus = LocalFocusManager.current
    var focusOnOpen by remember { mutableStateOf(false) }
    val close = {
        focus.clearFocus()
        focusOnOpen = false
        onQuery("")
        onExpanded(false)
    }
    val toggleLabel =
        stringResource(if (expanded) Res.string.apk_info_hide_search else Res.string.apk_info_show_search)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                content = header
            )
            ApkInfoTooltip(toggleLabel) {
                IconButton(
                    onClick = {
                        if (expanded) close() else {
                            focusOnOpen = true; onExpanded(true)
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier.size(48.dp).testTag("$tag-toggle")
                        .semantics { stateDescription = toggleLabel }) {
                    Icon(
                        if (expanded) Icons.Outlined.Close else Icons.Outlined.Search,
                        toggleLabel,
                        Modifier.size(24.dp)
                    )
                }
            }
        }
        AnimatedVisibility(
            expanded, modifier = Modifier.testTag("$tag-reveal"),
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(
                MaterialTheme.motionScheme.fastEffectsSpec()
            ),
            exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeOut(
                MaterialTheme.motionScheme.fastEffectsSpec()
            )
        ) {
            ApkDetailSearch(
                query,
                onQuery,
                enabled,
                label,
                tag,
                Modifier.focusRequester(requester).onPreviewKeyEvent {
                    if (it.key == Key.Escape && it.type == KeyEventType.KeyDown) {
                        close(); true
                    } else false
                })
            LaunchedEffect(focusOnOpen) {
                if (focusOnOpen) {
                    requester.requestFocus(); focusOnOpen = false
                }
            }
        }
    }
}
