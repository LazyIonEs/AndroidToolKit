package org.tool.kit.feature.cleaner

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.cleaner.CleanerRuleIssue
import org.tool.kit.domain.cleaner.ruleWarnings
import org.tool.kit.model.Sequence
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.available_space
import org.tool.kit.shared.generated.resources.cancel
import org.tool.kit.shared.generated.resources.cleaner_cancel_scan
import org.tool.kit.shared.generated.resources.cleaner_change_folder
import org.tool.kit.shared.generated.resources.cleaner_close_results
import org.tool.kit.shared.generated.resources.cleaner_delete_failed
import org.tool.kit.shared.generated.resources.cleaner_delete_selected
import org.tool.kit.shared.generated.resources.cleaner_delete_summary
import org.tool.kit.shared.generated.resources.cleaner_deleting_progress
import org.tool.kit.shared.generated.resources.cleaner_deselect_all
import org.tool.kit.shared.generated.resources.cleaner_directory
import org.tool.kit.shared.generated.resources.cleaner_enabled_rule_count
import org.tool.kit.shared.generated.resources.cleaner_file
import org.tool.kit.shared.generated.resources.cleaner_hide_details
import org.tool.kit.shared.generated.resources.cleaner_item_counts
import org.tool.kit.shared.generated.resources.cleaner_manage_rules
import org.tool.kit.shared.generated.resources.cleaner_matched
import org.tool.kit.shared.generated.resources.cleaner_modified_at
import org.tool.kit.shared.generated.resources.cleaner_recovered
import org.tool.kit.shared.generated.resources.cleaner_results_count
import org.tool.kit.shared.generated.resources.cleaner_risk_warning
import org.tool.kit.shared.generated.resources.cleaner_rule_separator
import org.tool.kit.shared.generated.resources.cleaner_rules
import org.tool.kit.shared.generated.resources.cleaner_safety_failure
import org.tool.kit.shared.generated.resources.cleaner_scan_issues
import org.tool.kit.shared.generated.resources.cleaner_scanning_progress
import org.tool.kit.shared.generated.resources.cleaner_select_all
import org.tool.kit.shared.generated.resources.cleaner_selected_size
import org.tool.kit.shared.generated.resources.cleaner_show_details
import org.tool.kit.shared.generated.resources.cleaner_sort
import org.tool.kit.shared.generated.resources.cleaner_total_capacity
import org.tool.kit.shared.generated.resources.cleaner_unsupported
import org.tool.kit.shared.generated.resources.confirm_deletion
import org.tool.kit.shared.generated.resources.delete_cache_dialog_title
import org.tool.kit.shared.generated.resources.largest_first
import org.tool.kit.shared.generated.resources.name_a_z
import org.tool.kit.shared.generated.resources.name_z_a
import org.tool.kit.shared.generated.resources.newest_date_first
import org.tool.kit.shared.generated.resources.oldest_date_first
import org.tool.kit.shared.generated.resources.select_folder
import org.tool.kit.shared.generated.resources.smallest_first
import org.tool.kit.shared.generated.resources.time_format
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.formatModifiedTime
import kotlin.time.ExperimentalTime

/** Each phase exposes only its main action and the information needed to use it. */
@Composable
fun CleanerScreen(
    state: CleanerUiState,
    onIntent: (CleanerIntent) -> Unit,
    onSelectDirectory: () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onManageRules: () -> Unit = {}
) {
    val showStart = state.phase == CleanerPhase.Idle && state.items.isEmpty()
    val showToolbar = state.phase == CleanerPhase.Idle && state.items.isNotEmpty()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    val listBottomPadding =
        if (showToolbar) with(LocalDensity.current) { toolbarHeight.toDp() } else 0.dp
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                // Keep the top edge fixed; animate this module's height independently of the page.
                AnimatedVisibility(
                    showStart, modifier = Modifier.testTag("cleaner-overview-reveal"),
                    enter = expandVertically(tween(280), expandFrom = Alignment.Top),
                    exit = shrinkVertically(tween(240), shrinkTowards = Alignment.Top)
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = 960.dp).fillMaxWidth().padding(24.dp)) {
                            CleanerOverviewCard(state, onManageRules)
                        }
                    }
                }
                AnimatedContent(
                    showStart, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "cleaner-phase"
                ) { start ->
                    if (start) CleanerStart(state)
                    else Column(Modifier.fillMaxSize()) {
                        CleanerResultsHeader(state, onIntent)
                        CleanerNotices(state, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        ClearBuildList(state, onIntent, onOpenDirectory, listBottomPadding)
                    }
                }
            }
            AnimatedVisibility(
                showStart, modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
            ) {
                val label = stringResource(Res.string.select_folder)
                ExtendedFloatingActionButton(
                    onClick = { if (state.rulesReady) onSelectDirectory() },
                    modifier = Modifier.padding(end = 16.dp, bottom = 16.dp)
                        .testTag("cleaner-start-scan")
                        .alpha(if (state.rulesReady) 1f else 0.38f)
                        .semantics { if (!state.rulesReady) disabled() },
                    icon = { Icon(Icons.Rounded.DriveFolderUpload, null) },
                    text = { Text(label) },
                )
            }
            AnimatedVisibility(
                showToolbar, modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + expandVertically(), exit = shrinkVertically() + fadeOut()
            ) {
                ClearBuildBottom(
                    state, onIntent, onSelectDirectory, onManageRules = onManageRules,
                    modifier = Modifier.onSizeChanged { toolbarHeight = it.height })
            }
        }
    }
}

@Composable
private fun CleanerStart(state: CleanerUiState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LazyColumn(
            Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("cleaner-start"),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            item {
                CleanerWelcome()
            }
            if (state.unsupportedRules || state.rulesRecovered || state.scanIssueCount > 0) {
                item { CleanerNotices(state) }
            }
        }
    }
}

@Composable
private fun CleanerOverviewCard(state: CleanerUiState, onManageRules: () -> Unit) {
    val capacity = state.capacity
    ElevatedCard(Modifier.fillMaxWidth().testTag("cleaner-overview")) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.available_space),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        capacity.usableBytes.formatFileSize(withInterval = true),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Text(
                    stringResource(
                        Res.string.cleaner_total_capacity,
                        capacity.totalBytes.formatFileSize(withInterval = true)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = {
                if (capacity.totalBytes <= 0L) 0f else (capacity.usedBytes.toFloat() / capacity.totalBytes).coerceIn(
                    0f,
                    1f
                )
            }, modifier = Modifier.fillMaxWidth().height(6.dp))
            HorizontalDivider(
                Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Row(
                Modifier.fillMaxWidth().testTag("cleaner-rules-overview"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.cleaner_rules),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        stringResource(
                            Res.string.cleaner_enabled_rule_count,
                            state.config.rules.count { it.enabled }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onManageRules, enabled = state.rulesReady) {
                    Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.cleaner_manage_rules))
                }
            }
        }
    }
}

@Composable
private fun CleanerResultsHeader(state: CleanerUiState, onIntent: (CleanerIntent) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(
                        when (state.phase) {
                            CleanerPhase.Scanning -> Res.string.cleaner_scanning_progress
                            CleanerPhase.Deleting -> Res.string.cleaner_deleting_progress
                            CleanerPhase.Idle -> Res.string.cleaner_results_count
                        }, state.items.size
                    ), style = MaterialTheme.typography.titleLarge
                )
                state.scanRoot?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(
                        Res.string.cleaner_selected_size,
                        state.checkedBytes.formatFileSize(withInterval = true)
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        Res.string.cleaner_item_counts,
                        state.checkedFiles,
                        state.checkedDirectories
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (state.phase != CleanerPhase.Idle) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LinearProgressIndicator(Modifier.weight(1f).testTag("cleaner-progress"))
                if (state.phase == CleanerPhase.Scanning) TextButton(onClick = {
                    onIntent(
                        CleanerIntent.CloseSelection
                    )
                }) {
                    Text(stringResource(Res.string.cleaner_cancel_scan))
                }
            }
        }
    }
}

@Composable
private fun CleanerNotices(state: CleanerUiState, modifier: Modifier = Modifier) {
    if (state.unsupportedRules || state.rulesRecovered || state.scanIssueCount > 0) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.unsupportedRules || state.rulesRecovered) Text(
                stringResource(if (state.unsupportedRules) Res.string.cleaner_unsupported else Res.string.cleaner_recovered),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
            if (state.scanIssueCount > 0) Text(
                stringResource(Res.string.cleaner_scan_issues, state.scanIssueCount),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ClearBuildList(
    state: CleanerUiState, onIntent: (CleanerIntent) -> Unit,
    onOpenDirectory: (String) -> Unit, bottomContentPadding: Dp
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().testTag("cleaner-results-list"), state = listState,
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = bottomContentPadding + 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                CleanerResultRow(
                    item,
                    state.phase != CleanerPhase.Deleting,
                    onIntent,
                    onOpenDirectory
                )
            }
        }
        VerticalScrollbar(
            rememberScrollbarAdapter(listState),
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = defaultScrollbarStyle().copy(
                unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun CleanerResultRow(
    item: CleanerItemUi,
    editable: Boolean,
    onIntent: (CleanerIntent) -> Unit,
    onOpenDirectory: (String) -> Unit
) {
    var details by rememberSaveable(item.id) { mutableStateOf(false) }
    val names = item.snapshot?.request?.rules?.filter { it.id in item.snapshot.matchedRuleIds }
        ?.map { ruleName(it) }
        ?: item.matchedRuleNames
    val matched = stringResource(
        Res.string.cleaner_matched,
        names.joinToString(stringResource(Res.string.cleaner_rule_separator))
    )
    val detailLabel =
        stringResource(if (details) Res.string.cleaner_hide_details else Res.string.cleaner_show_details)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (item.checked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().testTag("cleaner-result-${item.id}"),
        onClick = { details = !details }) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    onOpenDirectory(
                        if (item.isDirectory) item.path else item.path.substringBeforeLast(
                            '/',
                            item.path.substringBeforeLast('\\')
                        )
                    )
                }, enabled = editable) {
                    Icon(
                        if (item.isDirectory) Icons.Outlined.FolderOpen else Icons.Outlined.Description,
                        stringResource(if (item.isDirectory) Res.string.cleaner_directory else Res.string.cleaner_file)
                    )
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        item.displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (item.deleteFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (names.isNotEmpty()) Text(
                        matched,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.deleteFailed) Text(
                        stringResource(if (item.safetyFailure) Res.string.cleaner_safety_failure else Res.string.cleaner_delete_failed),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                CleanerExpandIcon(
                    details,
                    detailLabel,
                    Modifier.size(16.dp).padding(end = 2.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.bytes.formatFileSize(withInterval = true),
                    Modifier.padding(start = 12.dp, end = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Checkbox(
                    item.checked,
                    { onIntent(CleanerIntent.ItemCheckedChanged(item.id, it)) },
                    enabled = editable
                )
            }
            // The extra spacing exits with the details, avoiding a final-frame jump.
            AnimatedVisibility(
                details,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SelectionContainer {
                    Column(
                        Modifier.fillMaxWidth()
                            .padding(start = 56.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(item.path, style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(
                                Res.string.cleaner_modified_at,
                                formatModifiedTime(
                                    item.modifiedAt,
                                    stringResource(Res.string.time_format)
                                )
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (names.isNotEmpty()) Text(
                            matched,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 展示当前选择数量和体积，提供全选、排序、清理及重新选择目录操作。 */
@Composable
fun ClearBuildBottom(
    state: CleanerUiState,
    onIntent: (CleanerIntent) -> Unit,
    onSelectDirectory: () -> Unit,
    modifier: Modifier = Modifier,
    onManageRules: () -> Unit = {},
) {
    var sequenceExpanded by remember { mutableStateOf(false) }
    val onDismissRequest = { sequenceExpanded = false }
    Box(
        modifier = modifier.fillMaxWidth().padding(FloatingToolbarDefaults.ScreenOffset),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier.testTag("cleaner-toolbar"),
            floatingActionButton = {
                CleanerDeleteFab(state.checkedCount > 0) { onIntent(CleanerIntent.RequestDelete) }
            },
        ) {
            CleanerToolbarAction(
                Res.string.cleaner_close_results,
                Icons.Outlined.Close,
                "cleaner-close-selection"
            ) {
                onIntent(CleanerIntent.CloseSelection)
            }
            Box {
                CleanerToolbarAction(
                    Res.string.cleaner_sort,
                    Icons.AutoMirrored.Outlined.Sort,
                    "cleaner-sort"
                ) {
                    sequenceExpanded = !sequenceExpanded
                }
                DropdownMenu(
                    expanded = sequenceExpanded,
                    onDismissRequest = onDismissRequest
                ) {
                    SequenceDropdownMenu(
                        Res.string.newest_date_first,
                        Sequence.DATE_NEW_TO_OLD,
                        onDismissRequest,
                        state.sort, onIntent
                    )
                    SequenceDropdownMenu(
                        Res.string.oldest_date_first,
                        Sequence.DATE_OLD_TO_NEW,
                        onDismissRequest,
                        state.sort, onIntent
                    )
                    SequenceDropdownMenu(
                        Res.string.largest_first,
                        Sequence.SIZE_LARGE_TO_SMALL,
                        onDismissRequest,
                        state.sort, onIntent
                    )
                    SequenceDropdownMenu(
                        Res.string.smallest_first,
                        Sequence.SIZE_SMALL_TO_LARGE,
                        onDismissRequest,
                        state.sort, onIntent
                    )
                    SequenceDropdownMenu(
                        Res.string.name_a_z,
                        Sequence.NAME_A_TO_Z,
                        onDismissRequest,
                        state.sort,
                        onIntent
                    )
                    SequenceDropdownMenu(
                        Res.string.name_z_a,
                        Sequence.NAME_Z_TO_A,
                        onDismissRequest,
                        state.sort,
                        onIntent
                    )
                }
            }
            CleanerToolbarAction(
                if (state.allSelected) Res.string.cleaner_deselect_all else Res.string.cleaner_select_all,
                if (state.allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                "cleaner-toggle-all"
            ) {
                onIntent(CleanerIntent.ToggleAll)
            }
            CleanerToolbarAction(
                Res.string.cleaner_manage_rules,
                Icons.Outlined.Tune,
                "cleaner-toolbar-rules",
                enabled = state.rulesReady && state.phase == CleanerPhase.Idle,
                onClick = onManageRules
            )
            CleanerToolbarAction(
                Res.string.cleaner_change_folder,
                Icons.Outlined.DriveFolderUpload,
                "cleaner-select-folder",
                onClick = onSelectDirectory
            )
        }
    }
    if (state.deleteConfirmVisible) {
        DeleteAlertDialog(state, onConfirm = {
            onIntent(CleanerIntent.ConfirmDelete)
        }, onDismiss = {
            onIntent(CleanerIntent.DismissDelete)
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanerDeleteFab(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(Res.string.cleaner_delete_selected)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()
    ) {
        FloatingToolbarDefaults.VibrantFloatingActionButton(
            onClick = { if (enabled) onClick() },
            modifier = Modifier.testTag("cleaner-delete-selected")
                .alpha(if (enabled) 1f else 0.38f)
                .semantics { if (!enabled) disabled() },
        ) {
            Icon(Icons.Rounded.DeleteSweep, label)
        }
    }
}

@Composable
private fun SequenceDropdownMenu(
    resource: StringResource,
    sequence: Sequence,
    onDismissRequest: () -> Unit,
    currentSort: Sequence,
    onIntent: (CleanerIntent) -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(text = stringResource(resource), style = MaterialTheme.typography.labelLarge)
        }, leadingIcon = if (currentSort == sequence) {
            { Icon(Icons.Rounded.Check, "Check") }
        } else {
            null
        }, onClick = {
            onIntent(CleanerIntent.SortChanged(sequence))
            onDismissRequest.invoke()
        })
}

@Composable
private fun DeleteAlertDialog(state: CleanerUiState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = "DeleteSweep") },
        title = { Text(stringResource(Res.string.delete_cache_dialog_title)) },
        text = {
            val selected = state.confirmationItems.ifEmpty { state.items.filter { it.checked } }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        Res.string.cleaner_delete_summary,
                        selected.count { !it.isDirectory },
                        selected.count { it.isDirectory },
                        selected.sumOf { it.bytes }.formatFileSize(),
                        state.scanRoot.orEmpty()
                    )
                )
                if (selected.any { item ->
                        item.snapshot?.request?.rules?.any {
                            it.id in item.snapshot.matchedRuleIds && CleanerRuleIssue.BROAD_WARNING in ruleWarnings(
                                it
                            )
                        } == true
                    })
                    Text(
                        stringResource(Res.string.cleaner_risk_warning),
                        color = MaterialTheme.colorScheme.error
                    )
            }
        },
        onDismissRequest = { onDismiss.invoke() },
        confirmButton = {
            TextButton(onClick = {
                onConfirm.invoke()
            }) {
                Text(stringResource(Res.string.confirm_deletion))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss.invoke()
            }) {
                Text(stringResource(Res.string.cancel))
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CleanerToolbarAction(
    labelResource: StringResource, image: ImageVector, tag: String,
    enabled: Boolean = true, onClick: () -> Unit
) {
    val label = stringResource(labelResource)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.testTag(tag)) {
            Icon(
                image,
                label
            )
        }
    }
}
