package org.tool.kit.model

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:44
 */
/**
 * Snackbar信息
 * @param action 需要执行的操作
 */
data class SnackbarVisualsData(
    override var message: String = "",
    override var actionLabel: String? = null,
    override var withDismissAction: Boolean = false,
    override var duration: SnackbarDuration = SnackbarDuration.Short,
    var timestamp: Long = System.currentTimeMillis(),
    var action: (() -> Unit)? = null
) : SnackbarVisuals {

    fun reset(): SnackbarVisualsData {
        actionLabel = null
        withDismissAction = false
        duration = SnackbarDuration.Short
        action = null
        timestamp = System.currentTimeMillis()
        return this
    }
}
