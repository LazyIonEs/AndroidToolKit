package org.tool.kit.model

import org.jetbrains.compose.resources.StringResource

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2025/11/10 21:14
 */
data class DownloadResult<T>(
    val isSuccess: Boolean,
    val msg: StringResource?,
    val data: T,
)