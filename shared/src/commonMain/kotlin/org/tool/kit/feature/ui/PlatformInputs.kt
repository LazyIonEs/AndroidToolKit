package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropTarget
import org.tool.kit.model.FileSelectorType

// Platform input boundary: routes receive values; launchers and filesystem work live in jvmMain.
/** 返回打开文件选择器的回调；用户取消或选择未通过平台检查时不调用 onSelected。 */
@Composable
expect fun rememberFilePickerRequest(vararg types: FileSelectorType, onSelected: (String) -> Unit): () -> Unit

/** 创建拖放目标，报告拖动状态并异步返回保持原顺序的有效路径列表。 */
@Composable
expect fun dragAndDropTarget(dragging: (Boolean) -> Unit, onFinish: (Result<List<String>>) -> Unit): DragAndDropTarget

/** 返回打开目录选择器的回调，只有实际选择目录后才通知页面。 */
@Composable
expect fun rememberDirectoryPickerRequest(onSelected: (String) -> Unit): () -> Unit
