package org.tool.kit.platform

import java.util.Locale

internal actual fun isChineseLocale(): Boolean =
    Locale.getDefault().language.equals("zh", ignoreCase = true)
