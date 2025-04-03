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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Divider
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
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import utils.formatFileSize
import utils.formatFileUnit
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.math.RoundingMode
import java.time.format.DateTimeFormatter

@Composable
fun ClearBuild(viewModel: MainViewModel) {
    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ClearBuildBottom(viewModel)
            }
        }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ClearMain(viewModel)
        }
    }
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
        AnimatedVisibility(viewModel.pendingDeletionFileList.isNotEmpty()) {
            val checkedList = viewModel.pendingDeletionFileList.filter { it.checked }
            val checkedCount = checkedList.size
            val checkedTotalLength = checkedList.sumOf { it.fileLength }
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
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
                                "$checkedCount",
                                style = MaterialTheme.typography.headlineSmall
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
                                checkedTotalLength.formatFileUnit(),
                                style = MaterialTheme.typography.bodySmall
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
        AnimatedVisibility(
            visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
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
            items(
                items = viewModel.pendingDeletionFileList,
                key = { file -> file.file.absolutePath }) { pendingDeletionFile ->
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
                    // 最后一项是否需要分割线 待定
//                    if (index != viewModel.scanFileList.lastIndex) {
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
//                    }
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
private fun ClearBuildBottom(viewModel: MainViewModel) {
    val launcher = rememberDirectoryPickerLauncher { directory ->
        viewModel.scanPendingDeletionFileList(directory?.file ?: return@rememberDirectoryPickerLauncher)
    }
    BottomAppBar(actions = {
        IconButton(onClick = { viewModel.closeFileCheck() }) {
            Icon(Icons.Outlined.Close, contentDescription = "Localized description")
        }
        IconButton(onClick = { /* do something */ }) {
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
            onClick = { /* do something */ },
            containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
        ) {
            Icon(Icons.Outlined.Delete, "Localized description")
        }
    })
}