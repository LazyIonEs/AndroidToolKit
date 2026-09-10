package org.tool.kit.feature.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Topic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.model.Sequence
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.ZCOOLKuaiLe_Regular
import org.tool.kit.shared.generated.resources.available_space
import org.tool.kit.shared.generated.resources.cache_describe
import org.tool.kit.shared.generated.resources.cache_title
import org.tool.kit.shared.generated.resources.cancel
import org.tool.kit.shared.generated.resources.confirm_deletion
import org.tool.kit.shared.generated.resources.delete_cache_dialog_describe
import org.tool.kit.shared.generated.resources.delete_cache_dialog_title
import org.tool.kit.shared.generated.resources.folders
import org.tool.kit.shared.generated.resources.largest_first
import org.tool.kit.shared.generated.resources.name_a_z
import org.tool.kit.shared.generated.resources.name_z_a
import org.tool.kit.shared.generated.resources.newest_date_first
import org.tool.kit.shared.generated.resources.oldest_date_first
import org.tool.kit.shared.generated.resources.percentage_of_total_storage_space
import org.tool.kit.shared.generated.resources.scanned_folders
import org.tool.kit.shared.generated.resources.select_delete_director
import org.tool.kit.shared.generated.resources.select_folder
import org.tool.kit.shared.generated.resources.selected_folders
import org.tool.kit.shared.generated.resources.size_and_time
import org.tool.kit.shared.generated.resources.smallest_first
import org.tool.kit.shared.generated.resources.time_format
import org.tool.kit.shared.generated.resources.total_storage_space
import org.tool.kit.shared.generated.resources.used_space
import org.tool.kit.utils.LottieAnimation
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.formatFileUnit
import org.tool.kit.utils.formatStoragePercentage
import org.tool.kit.utils.formatModifiedTime
import kotlin.time.ExperimentalTime

/** 渲染容量、扫描列表和选择工具栏；删除与路径操作通过事件交由外部执行。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CleanerScreen(state: CleanerUiState, useDarkTheme: Boolean,
    onIntent: (CleanerIntent) -> Unit, onSelectDirectory: () -> Unit, onOpenDirectory: (String) -> Unit) {
    val showToolbar = state.phase == CleanerPhase.Idle && state.items.isNotEmpty()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    val listBottomPadding = if (showToolbar) with(LocalDensity.current) { toolbarHeight.toDp() } else 0.dp
    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClearBuildPreview(state, useDarkTheme)
            ClearBuildList(state, onIntent, onOpenDirectory, listBottomPadding)
        }
        AnimatedVisibility(
            visible = showToolbar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            ClearBuildBottom(state, onIntent, onSelectDirectory,
                modifier = Modifier.onSizeChanged { toolbarHeight = it.height })
        }
        AnimatedVisibility(
            visible = state.phase == CleanerPhase.Idle && state.items.isEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn() + expandHorizontally(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            val label = stringResource(Res.string.select_folder)
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                onClick = onSelectDirectory,
                icon = { Icon(Icons.Rounded.DriveFolderUpload, label) },
                text = { Text(label) },
            )
        }
    }
}

@Composable
private fun ClearBuildPreview(state: CleanerUiState, useDarkTheme: Boolean) {
    val capacity = state.capacity
    val totalSpace = capacity.totalBytes
    val usableSpace = capacity.usableBytes
    val usedSpace = capacity.usedBytes
    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)
        ) {
            AnimatedVisibility(
                visible = state.phase == CleanerPhase.Idle && state.items.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text(
                                text = stringResource(Res.string.total_storage_space),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                totalSpace.formatFileSize(scale = 0, withInterval = true),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text(
                                text = stringResource(Res.string.used_space),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                usedSpace.formatFileSize(scale = 1, withInterval = true),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text(
                                text = stringResource(Res.string.available_space),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                usableSpace.formatFileSize(scale = 1, withInterval = true),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { if (totalSpace == 0L) 0f else usedSpace.toFloat() / totalSpace.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedVisibility(
                visible = state.phase != CleanerPhase.Idle || state.items.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val checkedCount = state.checkedCount
                val checkedTotalLength = state.checkedBytes
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Topic,
                            contentDescription = "Topic",
                            modifier = Modifier.size(18.dp),
                        )
                        val text =
                            if (state.phase != CleanerPhase.Idle) stringResource(Res.string.scanned_folders)
                            else stringResource(Res.string.selected_folders)
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(
                                "$checkedCount", style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                text = stringResource(Res.string.folders),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(
                                checkedTotalLength.formatFileSize(scale = 1, withUnit = false),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                checkedTotalLength.formatFileUnit(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            val percentage = formatStoragePercentage(checkedTotalLength, totalSpace)
                            Text(
                                "${percentage}%",
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                text = stringResource(Res.string.percentage_of_total_storage_space),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = (state.phase != CleanerPhase.Idle && state.phase != CleanerPhase.Deleting),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(
            visible = state.phase == CleanerPhase.Idle && state.items.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    val modifier = Modifier.weight(2f).graphicsLayer { // 将动画放大1.5倍
                        scaleX = 1.7f
                        scaleY = 1.7f
                    }
                    if (useDarkTheme) {
                        LottieAnimation("files/lottie_main_4_dark.json", modifier)
                    } else {
                        LottieAnimation("files/lottie_main_4_light.json", modifier)
                    }
                    Box(modifier = Modifier.weight(1f))
                }
                Column(modifier = Modifier.weight(1.5f).align(Alignment.CenterVertically)) {
                    val fontRegular = FontFamily(Font(Res.font.ZCOOLKuaiLe_Regular))
                    Text(
                        text = stringResource(Res.string.cache_title),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = fontRegular
                    )
                    Spacer(Modifier.size(24.dp))
                    Text(
                        text = stringResource(Res.string.cache_describe),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ClearBuildList(
    state: CleanerUiState,
    onIntent: (CleanerIntent) -> Unit,
    onOpenDirectory: (String) -> Unit,
    bottomContentPadding: Dp,
) {
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // Scrollable space lets the final row move entirely above the overlaid toolbar.
            contentPadding = PaddingValues(bottom = bottomContentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = state.items, key = { _, item -> item.id }
            ) { index, pendingDeletionFile ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onOpenDirectory(pendingDeletionFile.path) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            if (pendingDeletionFile.isDirectory) Icon(
                                Icons.Outlined.FolderOpen,
                                "FolderOpen"
                            )
                            else Icon(Icons.Outlined.Description, "Description")
                        }
                        val pattern = stringResource(Res.string.time_format)

                        Column(modifier = Modifier.weight(1f)) {
                            val path = pendingDeletionFile.displayPath
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (pendingDeletionFile.deleteFailed) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Unspecified
                                },
                            )
                            val size =
                                pendingDeletionFile.bytes.formatFileSize(withInterval = true)
                            val time = formatModifiedTime(pendingDeletionFile.modifiedAt, pattern)
                            Text(
                                text = stringResource(Res.string.size_and_time, size, time),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Checkbox(
                            checked = pendingDeletionFile.checked,
                            onCheckedChange = { check ->
                                onIntent(CleanerIntent.ItemCheckedChanged(pendingDeletionFile.id, check))
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    // 最后一项不需要分割线
                    if (index != state.items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
        val customLocalScrollbarStyle = defaultScrollbarStyle().copy(
            unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = customLocalScrollbarStyle
        )
    }
}

/** 展示当前选择数量和体积，提供全选、排序、清理及重新选择目录操作。 */
@Composable
fun ClearBuildBottom(
    state: CleanerUiState,
    onIntent: (CleanerIntent) -> Unit,
    onSelectDirectory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sequenceExpanded by remember { mutableStateOf(false) }
    val onDismissRequest = { sequenceExpanded = false }
    Box(
        modifier = modifier.fillMaxWidth().padding(FloatingToolbarDefaults.ScreenOffset),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.StandardFloatingActionButton(
                    onClick = { onIntent(CleanerIntent.RequestDelete) },
                ) {
                    Icon(Icons.Outlined.Delete, "Localized description")
                }
            },
        ) {
            IconButton(onClick = { onIntent(CleanerIntent.CloseSelection) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Localized description")
            }
            Box {
                IconButton(onClick = { sequenceExpanded = !sequenceExpanded }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = "Localized description",
                    )
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
                    SequenceDropdownMenu(Res.string.name_a_z, Sequence.NAME_A_TO_Z, onDismissRequest, state.sort, onIntent)
                    SequenceDropdownMenu(Res.string.name_z_a, Sequence.NAME_Z_TO_A, onDismissRequest, state.sort, onIntent)
                }
            }
            IconButton(onClick = { onIntent(CleanerIntent.ToggleAll) }) {
                Icon(
                    if (state.allSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                    contentDescription = "Localized description",
                )
            }
            IconButton(onClick = onSelectDirectory) {
                Icon(
                    Icons.Outlined.DriveFolderUpload,
                    contentDescription = "Localized description",
                )
            }
        }
    }
    if (state.deleteConfirmVisible) {
        DeleteAlertDialog(onConfirm = {
            onIntent(CleanerIntent.ConfirmDelete)
        }, onDismiss = {
            onIntent(CleanerIntent.DismissDelete)
        })
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
private fun DeleteAlertDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = "DeleteSweep") },
        title = { Text(stringResource(Res.string.delete_cache_dialog_title)) },
        text = { Text(stringResource(Res.string.delete_cache_dialog_describe)) },
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
