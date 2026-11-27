package org.tool.kit.feature.junk

import org.tool.kit.domain.junk.*
import org.tool.kit.domain.junk.JunkAarSizeRange.Companion.BYTES_PER_MB
import org.tool.kit.model.JunkMode

private const val MINIMUM_AAR_SIZE_BYTES = BYTES_PER_MB / 2
private val singleAarSizeBounds = MINIMUM_AAR_SIZE_BYTES..(100 * BYTES_PER_MB)
private val multiAarSizeBounds = MINIMUM_AAR_SIZE_BYTES..(10 * BYTES_PER_MB)
private val defaultSingleSize = JunkAarSizeRange(17_500_000, 22_500_000)
private val defaultMultiSize = JunkAarSizeRange(500_000, 1_500_000)
private val aarSizeInput = Regex("[0-9]*[.,]?[0-9]*")

internal fun String.isAarSizeInput(): Boolean = aarSizeInput.matches(this)

private fun String.toRoundedAarSizeBytes(): Long? {
    val input = trim()
    if (!input.isAarSizeInput()) return null
    val separator = input.indexOfFirst { it == '.' || it == ',' }
    // Only the first two decimal digits affect rounding to tenths.
    val number = if (separator < 0) input else input.take(separator + 3)
    val bytes = JunkAarSizeRange.megabytesToBytes(number) ?: return null
    return bytes.roundedAarTenths().takeIf { it <= Long.MAX_VALUE / BYTES_PER_TENTH_MB }?.times(BYTES_PER_TENTH_MB)
}

internal val JunkMode.sizeBoundsBytes: LongRange
    get() = when (this) {
        JunkMode.SINGLE -> singleAarSizeBounds
        JunkMode.MULTI -> multiAarSizeBounds
    }

internal val JunkMode.minimumSizeGapBytes: Long
    get() = when (this) {
        JunkMode.SINGLE -> 2 * BYTES_PER_MB
        JunkMode.MULTI -> BYTES_PER_MB / 2
    }

internal fun JunkMode.sizeRangeMessageArgs(): Array<String> = arrayOf(
    sizeBoundsBytes.first.formatRoundedAarMegabytes(),
    sizeBoundsBytes.last.formatRoundedAarMegabytes(),
    minimumSizeGapBytes.formatAarMegabytes(),
)

data class SingleJunkForm(
    val aarName: String = "junk_com_dev_junk_plugin_TT$JUNK_AAR_VERSION.aar",
    val packageName: String = "com.dev.junk",
    val suffix: String = "plugin",
    val resPrefix: String = "junk_",
    val size: JunkAarSizeForm = JunkAarSizeForm(),
)

data class MultiJunkForm(
    val outputDir: String = "junk",
    val aarCount: String = "50",
    val size: JunkAarSizeForm = JunkAarSizeForm(
        minimumMb = defaultMultiSize.minimumBytes.formatRoundedAarMegabytes(),
        maximumMb = defaultMultiSize.maximumBytes.formatRoundedAarMegabytes(),
        lastValidRange = defaultMultiSize,
    ),
)

data class JunkAarSizeForm(
    val minimumMb: String = defaultSingleSize.minimumBytes.formatRoundedAarMegabytes(),
    val maximumMb: String = defaultSingleSize.maximumBytes.formatRoundedAarMegabytes(),
    private val lastValidRange: JunkAarSizeRange = defaultSingleSize,
) {
    private val minimumBytes = minimumMb.toRoundedAarSizeBytes()
    private val maximumBytes = maximumMb.toRoundedAarSizeBytes()
    private val parsedRange = if (minimumBytes != null && maximumBytes != null && minimumBytes > 0 && minimumBytes <= maximumBytes) {
        JunkAarSizeRange(minimumBytes, maximumBytes)
    } else null

    internal fun range(mode: JunkMode): JunkAarSizeRange? = parsedRange?.takeIf { it.isValidFor(mode) }

    internal fun sliderSelection(mode: JunkMode): JunkAarSizeRange =
        range(mode) ?: lastValidRange.takeIf { it.isValidFor(mode) } ?: when (mode) {
            JunkMode.SINGLE -> defaultSingleSize
            JunkMode.MULTI -> defaultMultiSize
        }

    internal fun finishEditing(mode: JunkMode): JunkAarSizeForm {
        val minimum = minimumBytes?.formatRoundedAarMegabytes() ?: minimumMb
        val maximum = maximumBytes?.formatRoundedAarMegabytes() ?: maximumMb
        return if (minimum == minimumMb && maximum == maximumMb) this else withInputs(mode, minimum, maximum)
    }

    /** Keep the last usable selection during text edits. */
    internal fun withInputs(
        mode: JunkMode,
        minimumMb: String = this.minimumMb,
        maximumMb: String = this.maximumMb,
    ): JunkAarSizeForm = copy(
        minimumMb = minimumMb,
        maximumMb = maximumMb,
        lastValidRange = sliderSelection(mode),
    )
}

private fun JunkAarSizeRange.isValidFor(mode: JunkMode): Boolean {
    val bounds = mode.sizeBoundsBytes
    return minimumBytes >= bounds.first && maximumBytes <= bounds.last &&
        maximumBytes - minimumBytes >= mode.minimumSizeGapBytes
}

data class JunkCodeUiState(
    val outputPath: String,
    val mode: JunkMode,
    val single: SingleJunkForm = SingleJunkForm(),
    val multi: MultiJunkForm = MultiJunkForm(),
    val estimatedSize: String = "",
    val outputValidation: JunkOutputCheck = JunkOutputCheck(),
    val busy: Boolean = false,
)

sealed interface JunkCodeIntent {
    data class OutputPathChanged(val value: String) : JunkCodeIntent
    data class ModeChanged(val value: JunkMode) : JunkCodeIntent
    data class PackageNameChanged(val value: String) : JunkCodeIntent
    data class SuffixChanged(val value: String) : JunkCodeIntent
    data class ResPrefixChanged(val value: String) : JunkCodeIntent
    data class OutputDirChanged(val value: String) : JunkCodeIntent
    data class AarCountChanged(val value: String) : JunkCodeIntent
    data class MinimumSizeChanged(val value: String) : JunkCodeIntent
    data class MaximumSizeChanged(val value: String) : JunkCodeIntent
    data object SizeEditingFinished : JunkCodeIntent
    data class SizeRangeChanged(val value: JunkAarSizeRange) : JunkCodeIntent
    data object RandomSuffix : JunkCodeIntent
    data object RandomPrefix : JunkCodeIntent
    data object Refresh : JunkCodeIntent
    data object Submit : JunkCodeIntent
}

object JunkFormReducer {
    /** 更新单个 AAR 草稿，包名或后缀变化时同步重算展示文件名。 */
    fun single(form: SingleJunkForm, intent: JunkCodeIntent): SingleJunkForm = when (intent) {
        is JunkCodeIntent.PackageNameChanged -> form.copy(packageName = intent.value,
            aarName = displayName(intent.value, form.suffix))
        is JunkCodeIntent.SuffixChanged -> form.copy(suffix = intent.value,
            aarName = displayName(form.packageName, intent.value))
        is JunkCodeIntent.ResPrefixChanged -> form.copy(resPrefix = intent.value)
        else -> form
    }
    /** 更新批量 AAR 草稿，保留字符串形式的输入以支持编辑中间态。 */
    fun multi(form: MultiJunkForm, intent: JunkCodeIntent): MultiJunkForm = when (intent) {
        is JunkCodeIntent.OutputDirChanged -> form.copy(outputDir = intent.value)
        is JunkCodeIntent.AarCountChanged -> form.copy(aarCount = intent.value)
        else -> form
    }
    fun size(state: JunkCodeUiState, intent: JunkCodeIntent): JunkCodeUiState = when (state.mode) {
        JunkMode.SINGLE -> state.copy(single = state.single.copy(size = size(state.single.size, state.mode, intent)))
        JunkMode.MULTI -> state.copy(multi = state.multi.copy(size = size(state.multi.size, state.mode, intent)))
    }

    private fun size(form: JunkAarSizeForm, mode: JunkMode, intent: JunkCodeIntent): JunkAarSizeForm = when (intent) {
        is JunkCodeIntent.MinimumSizeChanged -> form.withInputs(mode, minimumMb = intent.value)
        is JunkCodeIntent.MaximumSizeChanged -> form.withInputs(mode, maximumMb = intent.value)
        JunkCodeIntent.SizeEditingFinished -> form.finishEditing(mode)
        is JunkCodeIntent.SizeRangeChanged -> form.withInputs(
            mode,
            minimumMb = intent.value.minimumBytes.formatRoundedAarMegabytes(),
            maximumMb = intent.value.maximumBytes.formatRoundedAarMegabytes(),
        )
        else -> form
    }
    private fun displayName(packageName: String, suffix: String) = "junk_${packageName.replace('.', '_')}_${suffix}_TT$JUNK_AAR_VERSION.aar"
}

fun JunkCodeUiState.configuration(): JunkConfiguration? = when (mode) {
    JunkMode.SINGLE -> single.size.range(mode)?.let { JunkConfiguration.SingleBySize(single.packageName + "." + single.suffix, it, single.resPrefix) }
    JunkMode.MULTI -> multi.size.range(mode)?.let { JunkConfiguration.MultiBySize(multi.outputDir, multi.aarCount.toIntOrNull() ?: 0, it) }
}

fun JunkCodeUiState.hasMissingFields(): Boolean = outputPath.isBlank() || with(single) {
    packageName.isBlank() || suffix.isBlank() || resPrefix.isBlank()
} || with(multi) {
    outputDir.isBlank() || aarCount.isBlank()
}
