@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.ApkLibrarySource
import org.tool.kit.domain.apk.ApkRuleAnalysis
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_all
import org.tool.kit.shared.generated.resources.apk_info_copy
import org.tool.kit.shared.generated.resources.apk_info_no_sdks
import org.tool.kit.shared.generated.resources.apk_info_sdk_matched_entries
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_action
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_components
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_dex
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_native
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_static
import org.tool.kit.shared.generated.resources.apk_info_sdk_sources_entries
import org.tool.kit.shared.generated.resources.apk_info_search_sdks
import org.tool.kit.shared.generated.resources.apk_info_tab_sdks
import org.tool.kit.shared.generated.resources.lib_rules_bundled
import org.tool.kit.shared.generated.resources.lib_rules_check_update
import org.tool.kit.shared.generated.resources.lib_rules_summary
import org.tool.kit.shared.generated.resources.lib_rules_updated
import org.tool.kit.shared.generated.resources.lib_rules_updating

private enum class SdkSourceFilter(val sources: Set<ApkLibrarySource>) {
    Native(setOf(ApkLibrarySource.Native)),
    Components(
        setOf(
            ApkLibrarySource.Activity,
            ApkLibrarySource.Service,
            ApkLibrarySource.Receiver,
            ApkLibrarySource.Provider,
        )
    ),
    Dex(setOf(ApkLibrarySource.Dex)),
    Static(setOf(ApkLibrarySource.Static)),
    Action(setOf(ApkLibrarySource.Action)),
}

@Composable
internal fun ApkSdkPage(
    analysis: ApkRuleAnalysis?,
    ruleIcons: Map<String, ImageBitmap>,
    ruleDetails: Map<String, RuleDetailState>,
    rulesMetadata: ApkLibRulesMetadata?,
    updatingRules: Boolean,
    inset: Dp,
    onLoadDetail: (String, Int, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onUpdateRules: () -> Unit,
    onCopy: (String) -> Unit,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sourceFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val sdks = analysis?.sdks
    val filterCounts = remember(sdks) {
        SdkSourceFilter.entries.associateWith { filter ->
            sdks.orEmpty().count { sdk -> sdk.sources.any { it in filter.sources } }
        }
    }
    val filtered = remember(sdks, query, sourceFilter) {
        val activeFilter = sourceFilter?.let { name -> SdkSourceFilter.entries.firstOrNull { it.name == name } }
        val trimmed = query.trim()
        sdks.orEmpty().filter { sdk ->
            val matchesSource = activeFilter == null || sdk.sources.any { it in activeFilter.sources }
            val matchesQuery = trimmed.isEmpty() ||
                sdk.label.contains(trimmed, true) ||
                sdk.entries.any { it.name.contains(trimmed, true) || it.rule.ruleName.contains(trimmed, true) }
            matchesSource && matchesQuery
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()

    ApkPageMotion(selectedUuid, forward = { _, next -> next != null }) { uuid ->
        val sdk = sdks?.firstOrNull { it.uuid == uuid }
        if (sdk != null) {
            Column(
                Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApkBackHeading(
                    sdk.label,
                    stringResource(Res.string.apk_info_tab_sdks),
                    { selectedUuid = null },
                )
                Row(
                    Modifier.weight(1f).fillMaxWidth().testTag("apk-sdk-detail"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ApkPanel(
                        Modifier.weight(1.8f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    ) {
                        val primaryEntry = sdk.entries.firstOrNull { it.rule.ruleId == sdk.primaryRuleId }
                            ?: sdk.entries.firstOrNull()
                        ApkRuleDetailSection(
                            uuid = sdk.uuid,
                            ruleId = sdk.primaryRuleId,
                            label = sdk.label,
                            iconId = sdk.iconId,
                            isSimpleColorIcon = sdk.isSimpleColorIcon,
                            hasDetail = sdk.hasDetail,
                            matchedByRegex = primaryEntry?.rule?.matchedByRegex,
                            ruleName = primaryEntry?.rule?.ruleName,
                            icons = ruleIcons,
                            detailState = ruleDetails[ruleDetailKey(sdk.uuid, sdk.primaryRuleId)],
                            onLoadDetail = onLoadDetail,
                            onOpenUrl = onOpenUrl,
                            onCopy = onCopy,
                            fallbackIcon = Icons.Outlined.Extension,
                        )
                    }
                    ApkPanel(Modifier.weight(0.85f).fillMaxHeight()) {
                        Text(
                            stringResource(Res.string.apk_info_sdk_matched_entries) + " (${sdk.entries.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                            items(
                                sdk.entries,
                                key = { "${it.source}:${it.rule.ruleId}:${it.name}" },
                            ) { entry ->
                                val sourceText = apkLibrarySourceLabel(entry.source)
                                ListItem(
                                    onClick = { onCopy(entry.name) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shapes = apkInfoRowShapes(),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    supportingContent = {
                                        Text(
                                            "$sourceText · ${entry.rule.ruleName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    trailingContent = {
                                        ApkCopyButton(
                                            stringResource(Res.string.apk_info_copy, entry.name),
                                            { onCopy(entry.name) },
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                ) {
                                    Text(
                                        entry.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApkExpandableSearch(
                    query,
                    { query = it; scope.launch { listState.scrollToItem(0) } },
                    !sdks.isNullOrEmpty(),
                    stringResource(Res.string.apk_info_search_sdks),
                    searchExpanded,
                    { searchExpanded = it },
                ) {
                    val filterLabels = mapOf(
                        SdkSourceFilter.Native to stringResource(Res.string.apk_info_sdk_source_native),
                        SdkSourceFilter.Components to stringResource(Res.string.apk_info_sdk_source_components),
                        SdkSourceFilter.Dex to stringResource(Res.string.apk_info_sdk_source_dex),
                        SdkSourceFilter.Static to stringResource(Res.string.apk_info_sdk_source_static),
                        SdkSourceFilter.Action to stringResource(Res.string.apk_info_sdk_source_action),
                    )
                    ApkFilterButtons(
                        sourceFilter,
                        listOf(ApkFilterOption(null, stringResource(Res.string.apk_info_all))) +
                            SdkSourceFilter.entries.map { filter ->
                                ApkFilterOption(filter.name, "${filterLabels.getValue(filter)} ${filterCounts[filter] ?: 0}")
                            },
                        "apk-sdk-filter",
                    ) {
                        sourceFilter = it
                        scope.launch { listState.scrollToItem(0) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ApkDetailCount(filtered.size, sdks?.size)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (rulesMetadata != null) {
                            Text(
                                "${
                                    stringResource(
                                        Res.string.lib_rules_summary,
                                        rulesMetadata.dataVersion.toInt(),
                                        rulesMetadata.ruleCount,
                                    )
                                } · ${
                                    stringResource(
                                        if (rulesMetadata.isBundled) Res.string.lib_rules_bundled
                                        else Res.string.lib_rules_updated
                                    )
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (rulesMetadata != null || updatingRules) {
                            TextButton(
                                onClick = onUpdateRules,
                                enabled = !updatingRules,
                                modifier = Modifier.testTag("apk-update-rules"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                if (updatingRules) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(Res.string.lib_rules_updating))
                                } else {
                                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(Res.string.lib_rules_check_update))
                                }
                            }
                        }
                    }
                }
                ApkListContainer(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                    LazyColumn(
                        Modifier.fillMaxWidth().testTag("apk-sdks-list"),
                        state = listState,
                    ) {
                        if (filtered.isEmpty()) item("empty") {
                            if (sdks != null && sdks.isEmpty()) {
                                Surface(
                                    Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Text(
                                        stringResource(Res.string.apk_info_no_sdks),
                                        Modifier.padding(24.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            } else {
                                ApkDetailEmpty(unavailable = sdks == null, empty = false)
                            }
                        }
                        items(filtered, key = { it.uuid }) { sdk ->
                            val sourceLabels = sdk.sources.map { apkLibrarySourceLabel(it) }.distinct().joinToString(" / ")
                            ApkRuleNavigationRow(
                                title = sdk.label,
                                subtitle = stringResource(
                                    Res.string.apk_info_sdk_sources_entries,
                                    sourceLabels,
                                    sdk.entries.size,
                                ),
                                fullValue = sdk.label,
                                icon = {
                                    ApkRuleIcon(
                                        iconId = sdk.iconId,
                                        isSimpleColorIcon = sdk.isSimpleColorIcon,
                                        icons = ruleIcons,
                                        fallback = Icons.Outlined.Extension,
                                    )
                                },
                                modifier = Modifier.animateItem(effects, placement, effects)
                                    .testTag("apk-sdk-${sdk.uuid}"),
                            ) {
                                focus.clearFocus()
                                selectedUuid = sdk.uuid
                            }
                        }
                    }
                }
            }
        }
    }
}
