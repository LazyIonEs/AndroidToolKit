package org.tool.kit.model

import org.jetbrains.compose.resources.StringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.dark_mode
import org.tool.kit.shared.generated.resources.follow_the_system
import org.tool.kit.shared.generated.resources.light_mode

enum class DarkThemeConfig(val resource: StringResource) {
    FOLLOW_SYSTEM(Res.string.follow_the_system),
    LIGHT(Res.string.light_mode),
    DARK(Res.string.dark_mode)
}

