package org.tool.kit.model

import org.jetbrains.compose.resources.StringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.lowercase_with_colon
import org.tool.kit.shared.generated.resources.lowercase_without_colon
import org.tool.kit.shared.generated.resources.uppercase_with_colon
import org.tool.kit.shared.generated.resources.uppercase_without_colon

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/19 14:58
 */
enum class CopyMode(val title: StringResource) {
    UPPERCASE_WITH_COLON(Res.string.uppercase_with_colon),
    LOWERCASE_WITH_COLON(Res.string.lowercase_with_colon),
    UPPERCASE_WITHOUT_COLON(Res.string.uppercase_without_colon),
    LOWERCASE_WITHOUT_COLON(Res.string.lowercase_without_colon)
}