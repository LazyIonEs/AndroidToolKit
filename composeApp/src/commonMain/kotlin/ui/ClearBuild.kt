package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import model.Sequence
import utils.formatFileSize
import utils.formatFileUnit
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

@Composable
fun ClearBuild(viewModel: MainViewModel) {
    ClearMain(viewModel)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClearMain(viewModel: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClearBuildPreview(viewModel)
            ClearBuildList(viewModel)
        }
        AnimatedVisibility(
            visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = fadeIn() + expandHorizontally(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            DirectoryButton(
                value = "点击选择文件夹",
                expanded = viewModel.verifierState !is UIState.Success,
            ) { directory ->
                viewModel.scanPendingDeletionFileList(directory)
            }
        }
    }
}

@Composable
private fun ClearBuildPreview(viewModel: MainViewModel) {
    // 总空间
    var totalSpace = 0L
    // 可用空间
    var usableSpace = 0L
    File.listRoots()?.forEach { fileRoot ->
        totalSpace += fileRoot.totalSpace
        usableSpace += fileRoot.usableSpace
    }
    // 已使用空间
    var usedSpace = totalSpace - usableSpace
    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            AnimatedVisibility(
                visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text("总存储空间", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.size(3.dp))
                            Text(totalSpace.formatFileSize(scale = 0), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text("已使用空间", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.size(3.dp))
                            Text(usedSpace.formatFileSize(scale = 1), style = MaterialTheme.typography.headlineSmall)
                        }
                        Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                            Text("可用空间", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.size(3.dp))
                            Text(usableSpace.formatFileSize(scale = 1), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { usedSpace.toFloat() / totalSpace.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedVisibility(
                visible = viewModel.pendingDeletionFileList.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val checkedList = viewModel.pendingDeletionFileList.filter { it.checked }
                val checkedCount = checkedList.size
                val checkedTotalLength = checkedList.sumOf { it.fileLength }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Topic,
                            contentDescription = "已选择文件夹",
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "已选择文件夹",
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
                            Text("个文件夹", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(
                                checkedTotalLength.formatFileSize(scale = 1, withUnit = false),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(Modifier.size(3.dp))
                            Text(
                                checkedTotalLength.formatFileUnit(), style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            val percentage = checkedTotalLength.toBigDecimal().multiply(100.toBigDecimal())
                                .divide(totalSpace.toBigDecimal(), 1, RoundingMode.HALF_UP)
                            Text("${percentage.toPlainString()}%", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.size(3.dp))
                            Text("占总存储空间", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = viewModel.fileClearUIState == UIState.Loading,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ClearBuildList(viewModel: MainViewModel) {
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = viewModel.pendingDeletionFileList,
                key = { _, item -> item.file.absolutePath }) { index, pendingDeletionFile ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pendingDeletionFile.file.isDirectory) Icon(
                            Icons.Outlined.FolderOpen, "文件夹", modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        else Icon(
                            Icons.Outlined.Description, "文件", modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        val instant = Instant.fromEpochMilliseconds(pendingDeletionFile.fileLastModified)
                        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")

                        Column(modifier = Modifier.weight(1f)) {
                            val path = pendingDeletionFile.filePath.replace(
                                pendingDeletionFile.directoryPath + File.separatorChar, ""
                            )
                            Text(
                                path,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${pendingDeletionFile.fileLength.formatFileSize()}，${
                                    localDateTime.toJavaLocalDateTime().format(formatter)
                                }", style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Checkbox(
                            checked = pendingDeletionFile.checked,
                            onCheckedChange = { check ->
                                viewModel.changeFileChecked(pendingDeletionFile, check)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    // 最后一项不需要分割线
                    if (index != viewModel.pendingDeletionFileList.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}

@Composable
fun ClearBuildBottom(viewModel: MainViewModel) {
    var sequenceExpanded by remember { mutableStateOf(false) }
    var deletionAlert by remember { mutableStateOf(false) }
    val launcher = rememberDirectoryPickerLauncher { directory ->
        viewModel.scanPendingDeletionFileList(directory?.file ?: return@rememberDirectoryPickerLauncher)
    }
    BottomAppBar(actions = {
        IconButton(onClick = { viewModel.closeFileCheck() }) {
            Icon(Icons.Outlined.Close, contentDescription = "Localized description")
        }
        IconButton(onClick = { sequenceExpanded = !sequenceExpanded }) {
            Icon(
                Icons.AutoMirrored.Outlined.Sort,
                contentDescription = "Localized description",
            )
        }
        IconButton(onClick = { viewModel.changeFileAllChecked() }) {
            val isAllCheck = viewModel.pendingDeletionFileList.none { file -> !file.checked }
            Icon(
                if (isAllCheck) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                contentDescription = "Localized description",
            )
        }
        IconButton(onClick = { launcher.launch() }) {
            Icon(
                Icons.Outlined.DriveFolderUpload,
                contentDescription = "Localized description",
            )
        }
    }, floatingActionButton = {
        FloatingActionButton(
            onClick = {
                if (viewModel.isAllFileUnchecked()) {
                    viewModel.updateSnackbarVisuals("请先选择需要删除的目录")
                } else {
                    deletionAlert = !deletionAlert
                }
            },
            containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
        ) {
            Icon(Icons.Outlined.Delete, "Localized description")
        }
    })
    val onDismissRequest = { sequenceExpanded = false }
    DropdownMenu(expanded = sequenceExpanded, offset = DpOffset(64.dp, 24.dp), onDismissRequest = onDismissRequest) {
        SequenceDropdownMenu("日期（从新到旧）", Sequence.DATE_NEW_TO_OLD, onDismissRequest, viewModel)
        SequenceDropdownMenu("日期（从旧到新）", Sequence.DATE_OLD_TO_NEW, onDismissRequest, viewModel)
        SequenceDropdownMenu("大小（从大到小）", Sequence.SIZE_LARGE_TO_SMALL, onDismissRequest, viewModel)
        SequenceDropdownMenu("大小（从小到大）", Sequence.SIZE_SMALL_TO_LARGE, onDismissRequest, viewModel)
        SequenceDropdownMenu("名称（从 A 到 Z）", Sequence.NAME_A_TO_Z, onDismissRequest, viewModel)
        SequenceDropdownMenu("名称（从 Z 到 A）", Sequence.NAME_Z_TO_A, onDismissRequest, viewModel)
    }
    if (deletionAlert) {
        DeleteAlertDialog(
            onConfirm = {
                deletionAlert = false
                viewModel.removeFileChecked()
            },
            onDismiss = {
                deletionAlert = false
            }
        )
    }
}

@Composable
private fun SequenceDropdownMenu(
    text: String, sequence: Sequence, onDismissRequest: () -> Unit, viewModel: MainViewModel
) {
    DropdownMenuItem(
        text = {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }, leadingIcon = if (viewModel.currentFileSequence == sequence) {
            { Icon(Icons.Rounded.Check, "当前文件排序模式选中") }
        } else {
            null
        }, onClick = {
            viewModel.updateFileSort(sequence)
            onDismissRequest.invoke()
        })
}

@Composable
private fun DeleteAlertDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(icon = {
        Icon(Icons.Rounded.DeleteSweep, contentDescription = "DeleteSweep")
    }, title = {
        Text("确认删除缓存？")
    }, text = {
        Text("此操作将永久清除该目录下的所有文件，删除后数据将无法恢复，且可能导致下次构建时间延长。请确保您已备份所有重要数据。")
    }, onDismissRequest = {
        onDismiss.invoke()
    }, confirmButton = {
        TextButton(onClick = {
            onConfirm.invoke()
        }) {
            Text("确认删除")
        }
    }, dismissButton = {
        TextButton(onClick = {
            onDismiss.invoke()
        }) {
            Text("取消")
        }
    })
}