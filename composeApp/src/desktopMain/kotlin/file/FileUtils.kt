package file

import androidx.compose.ui.awt.ComposeWindow
import java.awt.FileDialog
import java.io.File

/**
 * 显示文件选择器
 * @param isApk 是APK还是签名
 * @param isAll 可选APK或签名
 * @param onFileSelected 选择回调
 */
fun showFileSelector(
    isApk: Boolean = true, isAll: Boolean = false, onFileSelected: (String) -> Unit
) {
    val fileDialog = FileDialog(ComposeWindow())
    fileDialog.isMultipleMode = false
    fileDialog.setFilenameFilter { file, name ->
        val sourceFile = File(file, name)
        sourceFile.isFile && if (isAll) {
            sourceFile.name.endsWith(".apk") || sourceFile.name.endsWith(".keystore") || sourceFile.name.endsWith(".jks")
        } else {
            if (isApk) sourceFile.name.endsWith(".apk") else (sourceFile.name.endsWith(".keystore") || sourceFile.name.endsWith(
                ".jks"
            ))
        }
    }
    fileDialog.isVisible = true
    val directory = fileDialog.directory
    val file = fileDialog.file
    if (directory != null && file != null) {
        onFileSelected("$directory$file")
    }
}

/**
 * 显示可执行文件选择器
 * @param onFileSelected 选择回调
 */
fun showExecuteSelector(
    onFileSelected: (String) -> Unit
) {
    val fileDialog = FileDialog(ComposeWindow())
    fileDialog.isMultipleMode = false
    fileDialog.setFilenameFilter { file, name ->
        val sourceFile = File(file, name)
        sourceFile.canExecute()
    }
    fileDialog.isVisible = true
    val directory = fileDialog.directory
    val file = fileDialog.file
    if (directory != null && file != null) {
        onFileSelected("$directory$file")
    }
}

/**
 * 显示文件夹选择器
 * @param onFolderSelected 选择回调
 */
fun showFolderSelector(
    onFolderSelected: (String) -> Unit
) {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    val fileDialog = FileDialog(ComposeWindow())
    fileDialog.isMultipleMode = false
    fileDialog.isVisible = true
    val directory = fileDialog.directory
    val file = fileDialog.file
    if (directory != null && file != null) {
        onFolderSelected("$directory$file")
    }
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
}
