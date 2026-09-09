package org.tool.kit.utils

import org.tool.kit.model.CopyMode
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.copied_to_clipboard
import org.tool.kit.vm.MainViewModel
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/3 15:56
 * @Description : 描述
 * @Version     : 1.0
 */

fun copy(value: String, viewModel: MainViewModel) {
    copy(value) { viewModel.updateSnackbarVisuals(Res.string.copied_to_clipboard) }
}

fun copy(value: String, copyMode: CopyMode, viewModel: MainViewModel) {
    copy(value, copyMode) { viewModel.updateSnackbarVisuals(Res.string.copied_to_clipboard) }
}

fun copy(value: String, onCopied: () -> Unit) {
    writeClipboard(value)
    onCopied()
}

fun copy(value: String, copyMode: CopyMode, onCopied: () -> Unit) {
    copy(formatClipboardValue(value, copyMode), onCopied)
}

fun formatClipboardValue(value: String, copyMode: CopyMode): String =
    when (copyMode) {
        CopyMode.UPPERCASE_WITH_COLON -> value.uppercase()
        CopyMode.LOWERCASE_WITH_COLON -> value.lowercase()
        CopyMode.UPPERCASE_WITHOUT_COLON -> value.uppercase().replace(":", "")
        CopyMode.LOWERCASE_WITHOUT_COLON -> value.lowercase().replace(":", "")
    }

/**
 * 复制到剪切板
 */
private fun writeClipboard(value: String) {
    val stringSelection = StringSelection(value)
    val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(stringSelection, null)
}
