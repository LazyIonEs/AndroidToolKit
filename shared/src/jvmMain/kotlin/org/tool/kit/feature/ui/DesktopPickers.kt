package org.tool.kit.feature.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.tool.kit.model.FileSelectorType
import org.tool.kit.platform.DesktopFileSelection
import org.tool.kit.utils.toFileExtensions
import java.io.File

/** FileKit stays on its UI thread; acceptance (including executable checks) runs on IO. */
@Composable
actual fun rememberFilePickerRequest(vararg types: FileSelectorType, onSelected: (String) -> Unit): () -> Unit {
    val files = koinInject<DesktopFileSelection>()
    val scope = rememberCoroutineScope()
    val currentOnSelected by rememberUpdatedState(onSelected)
    val currentTypes by rememberUpdatedState(types.toList())
    val launcher = rememberFilePickerLauncher(type = FileKitType.File(types.toFileExtensions()), mode = FileKitMode.Single) { file ->
        val path = file?.path
        val selectionTypes = currentTypes
        scope.launch {
            files.acceptPickerPath(path, selectionTypes)?.let { currentOnSelected(it) }
        }
    }
    return { launcher.launch() }
}

@Composable
actual fun FileInputWithPicker(
    value: String, label: String, isError: Boolean,
    vararg fileSelectorType: FileSelectorType, onValueChange: (String) -> Unit,
) {
    FileInput(value, label, isError,
        onPickerRequest = rememberFilePickerRequest(*fileSelectorType, onSelected = onValueChange),
        onValueChange = onValueChange)
}

@Composable
actual fun FileInputWithPicker(
    value: String, label: String, isError: Boolean,
    modifier: Modifier, trailingIcon: @Composable (() -> Unit)?,
    vararg fileSelectorType: FileSelectorType, onValueChange: (String) -> Unit,
) {
    FileInput(value, label, isError, modifier, trailingIcon,
        onPickerRequest = rememberFilePickerRequest(*fileSelectorType, onSelected = onValueChange),
        onValueChange = onValueChange)
}

@Composable
actual fun FolderInputWithPicker(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit) {
    val currentOnSelected by rememberUpdatedState(onValueChange)
    val launcher = rememberDirectoryPickerLauncher { directory -> directory?.path?.let { currentOnSelected(it) } }
    FolderInput(value, label, isError, onPickerRequest = { launcher.launch() }, onValueChange = onValueChange)
}

@Composable
actual fun FileButton(value: String, expanded: Boolean, vararg fileSelectorType: FileSelectorType, onFileSelector: (String) -> Unit) {
    val request = rememberFilePickerRequest(*fileSelectorType, onSelected = onFileSelector)
    ExtendedFloatingActionButton(
        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
        onClick = request,
        icon = { Icon(Icons.Rounded.DriveFolderUpload, value) },
        text = { Text(value) },
        expanded = expanded
    )
}

@Composable
actual fun DirectoryButton(value: String, expanded: Boolean, onDirectorySelector: (File) -> Unit) {
    val currentOnSelected by rememberUpdatedState(onDirectorySelector)
    val launcher = rememberDirectoryPickerLauncher { directory -> directory?.file?.let { currentOnSelected(it) } }
    ExtendedFloatingActionButton(
        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
        onClick = { launcher.launch() },
        icon = { Icon(Icons.Rounded.DriveFolderUpload, value) },
        text = { Text(value) },
        expanded = expanded
    )
}

@Composable
actual fun rememberDirectoryPickerRequest(onSelected: (String) -> Unit): () -> Unit {
    val currentOnSelected by rememberUpdatedState(onSelected)
    val launcher = rememberDirectoryPickerLauncher { directory -> directory?.path?.let { currentOnSelected(it) } }
    return { launcher.launch() }
}
