package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.tool.kit.platform.DesktopFileSelection
import kotlin.io.path.pathString

/** 同步应答桌面拖放协议，再异步检查整批文件并回到页面协程发布结果。 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun dragAndDropTarget(dragging: (Boolean) -> Unit, onFinish: (Result<List<String>>) -> Unit): DragAndDropTarget {
    // 拖放对象会被 remember 保留，因此通过 UpdatedState 使用重组后的最新页面回调。
    val currentDragging by rememberUpdatedState(dragging)
    val currentOnFinish by rememberUpdatedState(onFinish)
    val files = koinInject<DesktopFileSelection>()
    val scope = rememberCoroutineScope()
    return remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { currentDragging(true) }
            override fun onExited(event: DragAndDropEvent) { currentDragging(false) }
            override fun onEnded(event: DragAndDropEvent) { currentDragging(false) }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentDragging(false)
                val data = event.dragData()
                if (data !is DragData.FilesList) {
                    currentOnFinish(Result.failure(Throwable("file list not obtained")))
                    return false
                }
                val candidates = data.readFiles().toList()
                scope.launch {
                    val result = try {
                        Result.success(files.resolveDrop(candidates).map { it.toAbsolutePath().pathString })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    currentOnFinish(result)
                }
                // Native protocol acceptance is synchronous; ordered, filtered paths arrive on Main.
                return true
            }
        }
    }
}
