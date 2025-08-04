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
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import model.DarkThemeConfig
import model.Sequence
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.ZCOOLKuaiLe_Regular
import org.tool.kit.composeapp.generated.resources.available_space
import org.tool.kit.composeapp.generated.resources.cache_describe
import org.tool.kit.composeapp.generated.resources.cache_title
import org.tool.kit.composeapp.generated.resources.cancel
import org.tool.kit.composeapp.generated.resources.confirm_deletion
import org.tool.kit.composeapp.generated.resources.delete_cache_dialog_describe
import org.tool.kit.composeapp.generated.resources.delete_cache_dialog_title
import org.tool.kit.composeapp.generated.resources.folders
import org.tool.kit.composeapp.generated.resources.largest_first
import org.tool.kit.composeapp.generated.resources.name_a_z
import org.tool.kit.composeapp.generated.resources.name_z_a
import org.tool.kit.composeapp.generated.resources.newest_date_first
import org.tool.kit.composeapp.generated.resources.oldest_date_first
import org.tool.kit.composeapp.generated.resources.percentage_of_total_storage_space
import org.tool.kit.composeapp.generated.resources.scanned_folders
import org.tool.kit.composeapp.generated.resources.select_delete_director
import org.tool.kit.composeapp.generated.resources.select_folder
import org.tool.kit.composeapp.generated.resources.selected_folders
import org.tool.kit.composeapp.generated.resources.size_and_time
import org.tool.kit.composeapp.generated.resources.smallest_first
import org.tool.kit.composeapp.generated.resources.time_format
import org.tool.kit.composeapp.generated.resources.total_storage_space
import org.tool.kit.composeapp.generated.resources.used_space
import utils.LottieAnimation
import utils.browseFileDirectory
import utils.formatFileSize
import utils.formatFileUnit
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
                value = stringResource(Res.string.select_folder),
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
    val usedSpace = totalSpace - usableSpace
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            AnimatedVisibility(
                visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
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
                                text = stringResource(Res.string.used_space), style = MaterialTheme.typography.bodySmall
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
                        progress = { usedSpace.toFloat() / totalSpace.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedVisibility(
                visible = viewModel.fileClearUIState == UIState.Loading || viewModel.pendingDeletionFileList.isNotEmpty(),
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
                            contentDescription = "Topic",
                            modifier = Modifier.size(18.dp),
                        )
                        val text =
                            if (viewModel.fileClearUIState == UIState.Loading) stringResource(Res.string.scanned_folders)
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
                            Text(text = stringResource(Res.string.folders), style = MaterialTheme.typography.bodySmall)
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
            visible = (viewModel.fileClearUIState == UIState.Loading && !viewModel.isClearing),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        AnimatedVisibility(
            visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Column(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    val themeConfig by viewModel.themeConfig.collectAsState()
                    val useDarkTheme = when (themeConfig) {
                        DarkThemeConfig.LIGHT -> false
                        DarkThemeConfig.DARK -> true
                        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    }
                    val modifier = Modifier.weight(2f).graphicsLayer { // 将动画放大1.5倍
                        scaleX = 1.7f
                        scaleY = 1.7f
                    }
                    if (useDarkTheme) {
                        LottieAnimation(scope, "files/lottie_main_4_dark.json", modifier)
                    } else {
                        LottieAnimation(scope, "files/lottie_main_4_light.json", modifier)
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
                        text = stringResource(Res.string.cache_describe), style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun ClearBuildList(viewModel: MainViewModel) {
    val state = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = viewModel.pendingDeletionFileList
            ) { index, pendingDeletionFile ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { browseFileDirectory(pendingDeletionFile.file) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            if (pendingDeletionFile.file.isDirectory) Icon(Icons.Outlined.FolderOpen, "FolderOpen")
                            else Icon(Icons.Outlined.Description, "Description")
                        }
                        val instant = Instant.fromEpochMilliseconds(pendingDeletionFile.fileLastModified)
                        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        val pattern = stringResource(Res.string.time_format)
                        val formatter = DateTimeFormatter.ofPattern(pattern)

                        Column(modifier = Modifier.weight(1f)) {
                            val path = pendingDeletionFile.filePath.replace(
                                pendingDeletionFile.directoryPath + File.separatorChar, ""
                            )
                            Text(
                                text = path,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (pendingDeletionFile.exception) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    Color.Unspecified
                                },
                            )
                            val size = pendingDeletionFile.fileLength.formatFileSize(withInterval = true)
                            val time = localDateTime.toJavaLocalDateTime().format(formatter)
                            Text(
                                text = stringResource(Res.string.size_and_time, size, time),
                                style = MaterialTheme.typography.bodyMedium
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
        val customLocalScrollbarStyle = defaultScrollbarStyle().copy(
            unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
        )
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = customLocalScrollbarStyle
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
                    viewModel.updateSnackbarVisuals(Res.string.select_delete_director)
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
        SequenceDropdownMenu(Res.string.newest_date_first, Sequence.DATE_NEW_TO_OLD, onDismissRequest, viewModel)
        SequenceDropdownMenu(Res.string.oldest_date_first, Sequence.DATE_OLD_TO_NEW, onDismissRequest, viewModel)
        SequenceDropdownMenu(Res.string.largest_first, Sequence.SIZE_LARGE_TO_SMALL, onDismissRequest, viewModel)
        SequenceDropdownMenu(Res.string.smallest_first, Sequence.SIZE_SMALL_TO_LARGE, onDismissRequest, viewModel)
        SequenceDropdownMenu(Res.string.name_a_z, Sequence.NAME_A_TO_Z, onDismissRequest, viewModel)
        SequenceDropdownMenu(Res.string.name_z_a, Sequence.NAME_Z_TO_A, onDismissRequest, viewModel)
    }
    if (deletionAlert) {
        DeleteAlertDialog(onConfirm = {
            deletionAlert = false
            viewModel.removeFileChecked()
        }, onDismiss = {
            deletionAlert = false
        })
    }
}

@Composable
private fun SequenceDropdownMenu(
    resource: StringResource, sequence: Sequence, onDismissRequest: () -> Unit, viewModel: MainViewModel
) {
    DropdownMenuItem(
        text = {
            Text(text = stringResource(resource), style = MaterialTheme.typography.labelLarge)
        }, leadingIcon = if (viewModel.currentFileSequence == sequence) {
            { Icon(Icons.Rounded.Check, "Check") }
        } else {
            null
        }, onClick = {
            viewModel.updateFileSort(sequence)
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