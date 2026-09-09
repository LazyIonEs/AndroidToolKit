package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTarget
import org.tool.kit.model.FileSelectorType
import java.io.File
import java.nio.file.Path

// Compatibility bridge for the existing desktop routes. Pure FileInput/FolderInput
// only accept values and callbacks; launchers and filesystem work live in jvmMain.
@Composable
expect fun rememberFilePickerRequest(vararg types: FileSelectorType, onSelected: (String) -> Unit): () -> Unit

@Composable
expect fun FileInputWithPicker(
    value: String, label: String, isError: Boolean,
    vararg fileSelectorType: FileSelectorType, onValueChange: (String) -> Unit,
)

@Composable
expect fun FileInputWithPicker(
    value: String, label: String, isError: Boolean,
    modifier: Modifier = Modifier, trailingIcon: @Composable (() -> Unit)? = null,
    vararg fileSelectorType: FileSelectorType, onValueChange: (String) -> Unit,
)

@Composable
expect fun FolderInputWithPicker(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit)

@Composable
expect fun FileButton(value: String, expanded: Boolean, vararg fileSelectorType: FileSelectorType, onFileSelector: (String) -> Unit)

@Composable
expect fun DirectoryButton(value: String, expanded: Boolean, onDirectorySelector: (File) -> Unit)

@Composable
expect fun dragAndDropTarget(dragging: (Boolean) -> Unit, onFinish: (Result<List<Path>>) -> Unit): DragAndDropTarget
