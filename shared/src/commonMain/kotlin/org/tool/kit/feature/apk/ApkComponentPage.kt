@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.*
import org.tool.kit.shared.generated.resources.*

@Composable
internal fun ApkComponentPage(
    components: List<ApkComponent>?,
    inset: Dp,
    onCopy: (String) -> Unit
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = remember(components, query, type) {
        components.orEmpty().filter {
            (type == null || it.type.name == type) && (it.name.contains(
                query.trim(),
                true
            ) || it.process.contains(query.trim(), true))
        }.sortedWith(compareBy({ it.type.ordinal }, { it.name }))
    }
    val counts = remember(components) { components.orEmpty().groupingBy { it.type }.eachCount() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement =
        MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    ApkPageMotion(selected, forward = { _, next -> next != null }) { id ->
        val component = components?.firstOrNull { "${it.type}:${it.name}" == id }
        if (component != null) Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApkBackHeading(
                stringResource(Res.string.apk_info_component_detail),
                stringResource(Res.string.apk_info_tab_components),
                { selected = null })
            Surface(Modifier.weight(1f).fillMaxWidth().testTag("apk-component-detail"),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ApkFillScrollColumn(Modifier.fillMaxSize(), padding = 16) {
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ApkCopyField(stringResource(Res.string.apk_info_component_name), component.name, onCopy, technical = true)
                        HorizontalDivider()
                    }
                    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            ApkCopyField(stringResource(Res.string.apk_info_component_type), component.type.name, onCopy, Modifier.weight(1f))
                            ApkCopyField(stringResource(Res.string.apk_info_declared_export), apkExportedLabel(component.exported), onCopy, Modifier.weight(1f))
                        }
                        ApkCopyField(stringResource(Res.string.apk_info_process), component.process, onCopy, technical = true)
                        component.targetActivity?.let {
                            ApkCopyField(stringResource(Res.string.apk_info_target_activity), it, onCopy, technical = true)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        ApkDetailNote(stringResource(Res.string.apk_info_exported_help))
                    }
                }
            }
        } else Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApkExpandableSearch(
                query,
                { query = it; scope.launch { listState.scrollToItem(0) } },
                !components.isNullOrEmpty(),
                stringResource(Res.string.apk_info_search_components),
                searchExpanded,
                { searchExpanded = it }) {
                ApkFilterButtons(
                    type,
                    listOf(
                        ApkFilterOption(
                            null,
                            stringResource(Res.string.apk_info_all)
                        )
                    ) + ApkComponentType.entries.map {
                        ApkFilterOption(
                            it.name,
                            "${if (it == ApkComponentType.ActivityAlias) "Alias" else it.name} ${counts[it] ?: 0}"
                        )
                    },
                    "apk-component-filter"
                ) { type = it; scope.launch { listState.scrollToItem(0) } }
            }
            ApkDetailCount(filtered.size, components?.size)
            ApkListContainer(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxWidth().testTag("apk-components-list"),
                    state = listState
                ) {
                    if (filtered.isEmpty()) item("empty") {
                        ApkDetailEmpty(
                            components == null,
                            components?.isEmpty() == true
                        )
                    }
                    items(filtered, key = { "${it.type}:${it.name}" }) { item ->
                        ApkNavigationRow(
                            item.name.substringAfterLast('.'),
                            "${item.type.name} · ${
                                stringResource(
                                    Res.string.apk_info_exported_value,
                                    apkExportedLabel(item.exported)
                                )
                            }",
                            item.name,
                            Icons.Outlined.Widgets,
                            Modifier.animateItem(effects, placement, effects)
                                .testTag("apk-component-${item.type}:${item.name}")
                        ) {
                            focus.clearFocus(); selected = "${item.type}:${item.name}"
                        }
                    }
                }
            }
        }
    }
}
