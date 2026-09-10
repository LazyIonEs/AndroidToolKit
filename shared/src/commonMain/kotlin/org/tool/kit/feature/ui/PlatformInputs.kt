package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.draganddrop.DragAndDropTarget
import org.tool.kit.model.FileSelectorType
import java.nio.file.Path

// Platform input boundary: routes receive values; launchers and filesystem work live in jvmMain.
@Composable
expect fun rememberFilePickerRequest(vararg types: FileSelectorType, onSelected: (String) -> Unit): () -> Unit

@Composable
expect fun dragAndDropTarget(dragging: (Boolean) -> Unit, onFinish: (Result<List<Path>>) -> Unit): DragAndDropTarget

@Composable
expect fun rememberDirectoryPickerRequest(onSelected: (String) -> Unit): () -> Unit
