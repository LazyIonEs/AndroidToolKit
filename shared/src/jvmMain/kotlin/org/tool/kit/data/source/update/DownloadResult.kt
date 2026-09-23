package org.tool.kit.data.source.update

import org.jetbrains.compose.resources.StringResource

/**
 * @author      : LazyIonEs
 * @description : 更新文件传输结果，包含成功标记、错误资源和附加数据
 * @createDate  : 2025/11/10 21:14
 */
data class DownloadResult<T>(
    val isSuccess: Boolean,
    val msg: StringResource?,
    val data: T,
)