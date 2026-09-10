package org.tool.kit.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import kotlin.time.Instant

private enum class FileSizeType(val unit: String) {
    SIZE_TYPE_B("B"), SIZE_TYPE_KB("KB"), SIZE_TYPE_MB("MB"), SIZE_TYPE_GB("GB"), SIZE_TYPE_TB("TB")
}

/**
 * @param scale 精确到小数点以后几位 (Accurate to a few decimal places)
 */
actual fun Long.formatFileSize(
    scale: Int,
    withUnit: Boolean,
    withInterval: Boolean
): String {
    val divisor = if (isMac) { //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
        1000L
    } else {
        1024L
    }
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(
            BigDecimal.valueOf(this),
            scale,
            FileSizeType.SIZE_TYPE_B,
            divisor
        )
    val interval = if (withInterval) " " else ""
    if (kiloByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_B.unit else ""}"
    } //KB
    val megaByte = formatSizeByTypeWithDivisor(kiloByte, scale, FileSizeType.SIZE_TYPE_KB, divisor)
    if (megaByte.toDouble() < 1) {
        return "${kiloByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_KB.unit else ""}"
    } //M
    val gigaByte = formatSizeByTypeWithDivisor(megaByte, scale, FileSizeType.SIZE_TYPE_MB, divisor)
    if (gigaByte.toDouble() < 1) {
        return "${megaByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_MB.unit else ""}"
    } //GB
    val teraBytes = formatSizeByTypeWithDivisor(gigaByte, scale, FileSizeType.SIZE_TYPE_GB, divisor)
    if (teraBytes.toDouble() < 1) {
        return "${gigaByte.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_GB.unit else ""}"
    } //TB
    return "${teraBytes.toPlainString()}${interval}${if (withUnit) FileSizeType.SIZE_TYPE_TB.unit else ""}"
}

actual fun Long.formatFileUnit(): String {
    val divisor = if (isMac) { //ROUND_DOWN 1023 -> 1023B ; ROUND_HALF_UP  1023 -> 1KB
        1000L
    } else {
        1024L
    }
    val kiloByte: BigDecimal =
        formatSizeByTypeWithDivisor(BigDecimal.valueOf(this), 2, FileSizeType.SIZE_TYPE_B, divisor)
    if (kiloByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_B.unit
    } //KB
    val megaByte = formatSizeByTypeWithDivisor(kiloByte, 2, FileSizeType.SIZE_TYPE_KB, divisor)
    if (megaByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_KB.unit
    } //M
    val gigaByte = formatSizeByTypeWithDivisor(megaByte, 2, FileSizeType.SIZE_TYPE_MB, divisor)
    if (gigaByte.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_MB.unit
    } //GB
    val teraBytes = formatSizeByTypeWithDivisor(gigaByte, 2, FileSizeType.SIZE_TYPE_GB, divisor)
    if (teraBytes.toDouble() < 1) {
        return FileSizeType.SIZE_TYPE_GB.unit
    } //TB
    return FileSizeType.SIZE_TYPE_TB.unit
}

/** 逐级换算容量；首次换算向下截断，后续单位使用四舍五入，保持显示边界规则。 */
private fun formatSizeByTypeWithDivisor(
    size: BigDecimal, scale: Int, sizeType: FileSizeType, divisor: Long
): BigDecimal = size.divide(
    BigDecimal.valueOf(divisor),
    scale,
    if (sizeType == FileSizeType.SIZE_TYPE_B) RoundingMode.DOWN else RoundingMode.HALF_UP
)


actual fun formatStoragePercentage(bytes: Long, totalBytes: Long): String =
    (if (totalBytes == 0L) BigDecimal("0.0") else bytes.toBigDecimal().multiply(100.toBigDecimal())
        .divide(totalBytes.toBigDecimal(), 1, RoundingMode.HALF_UP)).toPlainString()

actual fun formatModifiedTime(epochMillis: Long, pattern: String): String =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        .toJavaLocalDateTime().format(DateTimeFormatter.ofPattern(pattern))
