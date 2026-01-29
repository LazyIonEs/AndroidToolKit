package org.tool.kit.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:45
 */
class PendingDeletionFile(
    val directoryPath: String,
    val file: File,
    val filePath: String,
    val fileLength: Long,
    val fileLastModified: Long,
    deleteExceptions: Boolean = false,
    initialChecked: Boolean = true,
) {
    var checked by mutableStateOf(initialChecked)
    var exception by mutableStateOf(deleteExceptions)
}