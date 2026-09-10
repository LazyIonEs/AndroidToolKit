package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
actual fun rememberDirectoryPickerRequest(onSelected: (String) -> Unit): () -> Unit {
    val currentOnSelected by rememberUpdatedState(onSelected)
    val launcher = rememberDirectoryPickerLauncher { directory -> directory?.path?.let { currentOnSelected(it) } }
    return { launcher.launch() }
}
