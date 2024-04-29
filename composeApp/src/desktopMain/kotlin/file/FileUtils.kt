package file

import androidx.compose.ui.awt.ComposeWindow
import utils.isApk
import utils.isImage
import utils.isKey
import java.awt.FileDialog
import java.io.File

/**
 * 显示文件选择器
 * @param fileSelectorType 文件选择类型
 * @param onFileSelected 选择回调
 */
fun showFileSelector(
    vararg fileSelectorType: FileSelectorType, onFileSelected: (String) -> Unit
) {
    val fileDialog = FileDialog(ComposeWindow())
    fileDialog.isMultipleMode = false
    fileDialog.setFilenameFilter { file, name ->
        val sourceFile = File(file, name)
        if (!sourceFile.isFile) {
            return@setFilenameFilter false
        }
        for (type in fileSelectorType) {
            val isConform = when(type) {
                FileSelectorType.APK -> name.isApk
                FileSelectorType.KEY -> name.isKey
                FileSelectorType.EXECUTE -> sourceFile.canExecute()
                FileSelectorType.IMAGE -> name.isImage
            }
            if (isConform) return@setFilenameFilter true
        }
        return@setFilenameFilter false
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

/**
 * 文件选择类型
 */
enum class FileSelectorType {
    APK,
    KEY,
    IMAGE,
    EXECUTE
}
