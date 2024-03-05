package utils

import toast.ToastModel
import toast.ToastUIState
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard

import java.awt.datatransfer.StringSelection

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/3 15:56
 * @Description : 描述
 * @Version     : 1.0
 */

suspend fun copy(value: String, toastUIState: ToastUIState) {
    copy(value)
    toastUIState.show(ToastModel("已复制到剪切板", ToastModel.Type.Success))
}

/**
 * 复制到剪切板
 */
fun copy(value: String) {
    val stringSelection = StringSelection(value)
    val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(stringSelection, null)
}