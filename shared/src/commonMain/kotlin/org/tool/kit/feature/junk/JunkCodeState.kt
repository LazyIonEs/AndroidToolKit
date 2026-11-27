package org.tool.kit.feature.junk

import org.tool.kit.domain.junk.*
import org.tool.kit.model.JunkMode

internal const val AAR_SIZE_STEP_BYTES = JunkAarSizeRange.BYTES_PER_MB / 2
private const val SLIDER_EXPANSION_BYTES = 50 * JunkAarSizeRange.BYTES_PER_MB
private val defaultSingleSliderBounds = AAR_SIZE_STEP_BYTES..(100 * JunkAarSizeRange.BYTES_PER_MB)
private val defaultSingleSize = JunkAarSizeRange(17_500_000, 22_500_000)
private val defaultMultiSize = JunkAarSizeRange(500_000, 1_500_000)

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
        minimumMb = defaultMultiSize.minimumBytes.formatAarMegabytes(),
        maximumMb = defaultMultiSize.maximumBytes.formatAarMegabytes(),
        lastValidRange = defaultMultiSize,
        trackBounds = AAR_SIZE_STEP_BYTES..(10 * JunkAarSizeRange.BYTES_PER_MB),
    ),
)

data class JunkAarSizeForm(
    val minimumMb: String = defaultSingleSize.minimumBytes.formatAarMegabytes(),
    val maximumMb: String = defaultSingleSize.maximumBytes.formatAarMegabytes(),
    private val lastValidRange: JunkAarSizeRange = defaultSingleSize,
    private val trackBounds: LongRange = defaultSingleSliderBounds,
) {
    val range = JunkAarSizeRange.fromMegabytes(minimumMb, maximumMb)
    internal val sliderSelection = range ?: lastValidRange
    internal val sliderBounds: LongRange = run {
        val lower = minOf(trackBounds.first, sliderSelection.minimumBytes)
        val upper = if (sliderSelection.maximumBytes > trackBounds.last) {
            sliderSelection.maximumBytes.roundUp(SLIDER_EXPANSION_BYTES)
        } else trackBounds.last
        lower..upper.roundUp(AAR_SIZE_STEP_BYTES, lower)
    }

    /** Keep the last usable selection during text edits and keep the track stable during drags. */
    internal fun withInputs(minimumMb: String = this.minimumMb, maximumMb: String = this.maximumMb): JunkAarSizeForm =
        copy(
            minimumMb = minimumMb,
            maximumMb = maximumMb,
            lastValidRange = sliderSelection,
            trackBounds = sliderBounds,
        )
}

private fun Long.roundUp(step: Long, origin: Long = 0): Long {
    val remainder = (this - origin) % step
    return if (remainder == 0L) this else this + minOf(step - remainder, Long.MAX_VALUE - this)
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
    fun size(form: JunkAarSizeForm, intent: JunkCodeIntent): JunkAarSizeForm = when (intent) {
        is JunkCodeIntent.MinimumSizeChanged -> form.withInputs(minimumMb = intent.value)
        is JunkCodeIntent.MaximumSizeChanged -> form.withInputs(maximumMb = intent.value)
        is JunkCodeIntent.SizeRangeChanged -> form.withInputs(
            minimumMb = form.minimumMb.takeIf { form.range?.minimumBytes == intent.value.minimumBytes }
                ?: intent.value.minimumBytes.formatAarMegabytes(),
            maximumMb = form.maximumMb.takeIf { form.range?.maximumBytes == intent.value.maximumBytes }
                ?: intent.value.maximumBytes.formatAarMegabytes(),
        )
        else -> form
    }
    private fun displayName(packageName: String, suffix: String) = "junk_${packageName.replace('.', '_')}_${suffix}_TT$JUNK_AAR_VERSION.aar"
}

fun JunkCodeUiState.configuration(): JunkConfiguration? = when (mode) {
    JunkMode.SINGLE -> single.size.range?.let { JunkConfiguration.SingleBySize(single.packageName + "." + single.suffix, it, single.resPrefix) }
    JunkMode.MULTI -> multi.size.range?.let { JunkConfiguration.MultiBySize(multi.outputDir, multi.aarCount.toIntOrNull() ?: 0, it) }
}

fun JunkCodeUiState.hasMissingFields(): Boolean = outputPath.isBlank() || with(single) {
    packageName.isBlank() || suffix.isBlank() || resPrefix.isBlank()
} || with(multi) {
    outputDir.isBlank() || aarCount.isBlank()
}
