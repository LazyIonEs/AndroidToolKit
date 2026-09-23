package org.tool.kit.utils

import org.tool.kit.model.CopyMode

/** 按选择的指纹格式转换大小写和冒号，保留其他字符。 */
fun formatClipboardValue(value: String, copyMode: CopyMode): String =
    when (copyMode) {
        CopyMode.UPPERCASE_WITH_COLON -> value.uppercase()
        CopyMode.LOWERCASE_WITH_COLON -> value.lowercase()
        CopyMode.UPPERCASE_WITHOUT_COLON -> value.uppercase().replace(":", "")
        CopyMode.LOWERCASE_WITHOUT_COLON -> value.lowercase().replace(":", "")
    }

