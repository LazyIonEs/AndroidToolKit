package ui

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import utils.formatFileSize
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.time.format.DateTimeFormatter
import kotlin.io.path.pathString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClearBuild(viewModel: MainViewModel) {
    var dragging by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ClearBuildPreview(viewModel)
            ClearBuildList(viewModel)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
                    dragging = it
                }, onFinish = { result ->
                    result.onSuccess { fileList ->
                        fileList.firstOrNull()?.let {
                            val path = it.toAbsolutePath().pathString
                        }
                    }
                })
            ), contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.align(Alignment.BottomEnd)) {
            DirectoryButton(
                value = if (dragging) {
                    "愣着干嘛，还不松手"
                } else {
                    "点击或拖拽选择文件夹"
                },
                expanded = viewModel.verifierState !is UIState.Success,
            ) { directory ->
                viewModel.scanPendingDeletionFileList(directory)
            }
        }
    }
}

@Composable
private fun ClearBuildPreview(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        AnimatedVisibility(
            visible = viewModel.fileClearUIState == UIState.WAIT && viewModel.pendingDeletionFileList.isEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
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
//    AnimatedVisibility(viewModel.pendingDeletionSummary != null) {
//        Row(modifier = Modifier.fillMaxWidth()) {
//            Text(
//                text = "文件数量：${viewModel.pendingDeletionSummary?.totalCount}",
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
//            )
//            Text(
//                text = "文件总大小：${viewModel.pendingDeletionSummary?.totalSize?.formatFileSize()}",
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
//            )
//        }
//    }
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
                key = { file -> file.file.absolutePath }
            ) { pendingDeletionFile ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pendingDeletionFile.file.isDirectory)
                            Icon(
                                Icons.Rounded.Folder,
                                "文件夹",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        else
                            Icon(
                                Icons.Rounded.Description,
                                "文件",
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                        val instant = Instant.fromEpochMilliseconds(pendingDeletionFile.fileLastModified)
                        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                        val formatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")

                        Column(modifier = Modifier.weight(1f)) {
                            val path = pendingDeletionFile.filePath.replace(
                                pendingDeletionFile.directoryPath + File.separatorChar, ""
                            )
                            Text(path, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${pendingDeletionFile.fileLength.formatFileSize()}，${
                                    localDateTime.toJavaLocalDateTime().format(formatter)
                                }",
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

}