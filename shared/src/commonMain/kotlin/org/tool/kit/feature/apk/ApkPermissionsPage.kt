@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_copy_permission
import org.tool.kit.shared.generated.resources.apk_info_no_matches
import org.tool.kit.shared.generated.resources.apk_info_no_permissions
import org.tool.kit.shared.generated.resources.apk_info_permission_count
import org.tool.kit.shared.generated.resources.apk_info_permission_declaration
import org.tool.kit.shared.generated.resources.apk_info_permission_matches
import org.tool.kit.shared.generated.resources.apk_info_search
import org.tool.kit.shared.generated.resources.apk_info_unknown_permissions

@Composable
internal fun ApkPermissionsPage(permissions: List<String>?, onCopy: (String) -> Unit) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(permissions, query) {
        permissions.orEmpty().distinct().filter { it.contains(query.trim(), true) }
    }
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement =
        MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    Column(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ApkExpandableSearch(
            query,
            { query = it; scope.launch { state.scrollToItem(0) } },
            !permissions.isNullOrEmpty(),
            stringResource(Res.string.apk_info_search),
            searchExpanded,
            { searchExpanded = it },
            tag = "apk-permission-search"
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (permissions == null) "—" else if (query.isBlank()) stringResource(
                        Res.string.apk_info_permission_count,
                        permissions.size
                    )
                    else stringResource(
                        Res.string.apk_info_permission_matches,
                        filtered.size,
                        permissions.size
                    ), style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(Res.string.apk_info_permission_declaration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ApkListContainer(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxWidth().testTag("apk-results-list"), state = state) {
                if (filtered.isEmpty()) item("empty") {
                    Text(
                        stringResource(
                            when {
                                permissions == null -> Res.string.apk_info_unknown_permissions
                                permissions.isEmpty() -> Res.string.apk_info_no_permissions
                                else -> Res.string.apk_info_no_matches
                            }
                        ), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium
                    )
                }
                items(filtered.chunked(2), key = { it.first() }) { pair ->
                    Row(Modifier.fillMaxWidth().animateItem(effects, placement, effects)) {
                        pair.forEach { permission ->
                            ListItem(
                                onClick = { onCopy(permission) },
                                modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                    .testTag("apk-permission-$permission"),
                                shapes = apkInfoRowShapes(),
                                contentPadding = PaddingValues(
                                    horizontal = 16.dp,
                                    vertical = 12.dp
                                ),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = {
                                    Icon(
                                        Icons.Outlined.Key,
                                        null,
                                        Modifier.size(18.dp)
                                    )
                                },
                                trailingContent = {
                                    ApkCopyButton(
                                        stringResource(Res.string.apk_info_copy_permission),
                                        { onCopy(permission) })
                                }) {
                                ApkInfoTooltip(permission) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (permission.contains('.')) Text(
                                            permission.substringBeforeLast('.') + ".",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            permission.substringAfterLast('.'),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
