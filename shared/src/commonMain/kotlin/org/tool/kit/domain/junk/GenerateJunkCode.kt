package org.tool.kit.domain.junk

/** 支持按数量重建 AAR，或按每份 AAR 的文件大小生成。 */
sealed interface JunkConfiguration {
    data class Single(val appPackageName: String, val packageCount: Int, val activityCount: Int, val resPrefix: String) : JunkConfiguration
    data class Multi(val outputDir: String, val aarCount: Int, val leastPackages: Int, val maximumPackages: Int,
        val leastActivities: Int, val maximumActivities: Int) : JunkConfiguration
    data class SingleBySize(val appPackageName: String, val size: JunkAarSizeRange, val resPrefix: String) : JunkConfiguration
    data class MultiBySize(val outputDir: String, val aarCount: Int, val size: JunkAarSizeRange) : JunkConfiguration
}

data class JunkAarSizeRange(val minimumBytes: Long, val maximumBytes: Long) {
    init {
        require(minimumBytes > 0 && maximumBytes >= minimumBytes) { "Invalid AAR size range" }
    }

    operator fun contains(bytes: Long): Boolean = bytes in minimumBytes..maximumBytes

    companion object {
        const val BYTES_PER_MB = 1_000_000L
        private val decimal = Regex("""(?:[0-9]+(?:[.,][0-9]{0,6})?|[.,][0-9]{1,6})""")

        fun fromMegabytes(minimum: String, maximum: String): JunkAarSizeRange? {
            val lower = megabytesToBytes(minimum) ?: return null
            val upper = megabytesToBytes(maximum) ?: return null
            return if (lower > 0 && lower <= upper) JunkAarSizeRange(lower, upper) else null
        }

        internal fun megabytesToBytes(input: String): Long? {
            val value = input.trim()
            if (!decimal.matches(value)) return null
            val parts = value.split('.', ',')
            val whole = parts.first().ifEmpty { "0" }.toLongOrNull() ?: return null
            val remainder = parts.getOrNull(1).orEmpty().padEnd(6, '0').toLong()
            if (whole > (Long.MAX_VALUE - remainder) / BYTES_PER_MB) return null
            return whole * BYTES_PER_MB + remainder
        }
    }
}

internal const val BYTES_PER_TENTH_MB = JunkAarSizeRange.BYTES_PER_MB / 10

internal fun Long.roundedAarTenths(): Long {
    val tenths = this / BYTES_PER_TENTH_MB
    return if (this % BYTES_PER_TENTH_MB >= BYTES_PER_TENTH_MB / 2) tenths + 1 else tenths
}

internal fun Long.formatRoundedAarMegabytes(groupThousands: Boolean = false): String {
    val tenths = roundedAarTenths()
    val digits = (tenths / 10).toString()
    val whole = if (groupThousands) digits.reversed().chunked(3).joinToString(",").reversed() else digits
    return "$whole.${tenths % 10}"
}

internal fun Long.formatRoundedAarSize(): String = "${formatRoundedAarMegabytes()} MB"

internal fun Long.formatAarMegabytes(): String {
    val whole = (this / JunkAarSizeRange.BYTES_PER_MB).toString()
    val fraction = (this % JunkAarSizeRange.BYTES_PER_MB).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole else "$whole.$fraction"
}

internal fun Long.formatAarSize(): String = "${formatAarMegabytes()} MB"

internal const val JUNK_AAR_VERSION = "3.1.0"
const val CURRENT_JUNK_GENERATOR_VERSION = 26

data class JunkGenerationStrategy(val generatorVersion: Int = CURRENT_JUNK_GENERATOR_VERSION)

data class GenerateJunkCodeRequest(
    val outputPath: String,
    val configuration: JunkConfiguration,
    val seed: Long? = null,
    val strategy: JunkGenerationStrategy? = null,
)
data class GeneratedJunkCode(val outputPath: String, val archivePaths: List<String>, val totalBytes: Long)
sealed interface GenerateJunkCodeOutcome {
    data class Success(val result: GeneratedJunkCode) : GenerateJunkCodeOutcome
    data class Failure(val message: String?) : GenerateJunkCodeOutcome
}

/** 预计输出字节数；maximum 为 null 表示单个估计值，否则表示大小区间。 */
data class JunkSizeEstimate(val minimum: Long, val maximum: Long? = null)
