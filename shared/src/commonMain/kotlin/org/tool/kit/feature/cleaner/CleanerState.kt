package org.tool.kit.feature.cleaner

import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.model.Sequence

enum class CleanerPhase { Idle, Scanning, Deleting }

data class CleanerItemUi(
    val id: String,
    val path: String,
    val displayPath: String,
    val bytes: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean,
    val exists: Boolean,
    val checked: Boolean = true,
    val deleteFailed: Boolean = false,
)

data class CleanerUiState(
    val scanRoot: String? = null,
    val phase: CleanerPhase = CleanerPhase.Idle,
    val items: List<CleanerItemUi> = emptyList(),
    val sort: Sequence = Sequence.SIZE_LARGE_TO_SMALL,
    val capacity: StorageCapacity = StorageCapacity(0, 0),
    val deleteConfirmVisible: Boolean = false,
) {
    val checkedCount = items.count { it.checked }
    val checkedBytes = items.filter { it.checked }.sumOf { it.bytes }
    val allSelected = items.all { it.checked }
}

sealed interface CleanerIntent {
    data class Rescan(val root: String) : CleanerIntent
    data class ItemCheckedChanged(val id: String, val checked: Boolean) : CleanerIntent
    data class SortChanged(val sort: Sequence) : CleanerIntent
    data object ToggleAll : CleanerIntent
    data object CloseSelection : CleanerIntent
    data object RequestDelete : CleanerIntent
    data object ConfirmDelete : CleanerIntent
    data object DismissDelete : CleanerIntent
    data object RefreshCapacity : CleanerIntent
}

internal fun BuildDirectory.toUi() = CleanerItemUi(path, path, displayPath, bytes, modifiedAt, isDirectory, exists)
internal fun CleanerItemUi.toDirectory(root: String) = BuildDirectory(root, path, displayPath, bytes, modifiedAt, isDirectory, exists)
/** 按用户选择返回排序副本；名称排序使用完整路径，避免同名 build 目录混淆。 */
internal fun List<CleanerItemUi>.sortedBy(sequence: Sequence) = when (sequence) {
    Sequence.DATE_NEW_TO_OLD -> sortedByDescending { it.modifiedAt }
    Sequence.DATE_OLD_TO_NEW -> sortedBy { it.modifiedAt }
    Sequence.SIZE_LARGE_TO_SMALL -> sortedByDescending { it.bytes }
    Sequence.SIZE_SMALL_TO_LARGE -> sortedBy { it.bytes }
    Sequence.NAME_A_TO_Z -> sortedBy { it.path }
    Sequence.NAME_Z_TO_A -> sortedByDescending { it.path }
}
