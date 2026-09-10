package org.tool.kit.utils

/** 按平台容量单位习惯格式化字节数，可控制小数位、单位和单位前空格。 */
expect fun Long.formatFileSize(scale: Int = 2, withUnit: Boolean = true, withInterval: Boolean = false): String
/** 返回该容量显示时使用的单位名称。 */
expect fun Long.formatFileUnit(): String

/** 格式化容量占比，不附加百分号；总容量为 0 时返回零比例。 */
expect fun formatStoragePercentage(bytes: Long, totalBytes: Long): String
/** 按系统当前时区和指定格式展示毫秒时间戳。 */
expect fun formatModifiedTime(epochMillis: Long, pattern: String): String
