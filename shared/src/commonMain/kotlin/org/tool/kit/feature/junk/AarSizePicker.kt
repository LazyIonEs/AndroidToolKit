package org.tool.kit.feature.junk

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.junk.JunkAarSizeRange
import org.tool.kit.domain.junk.JunkAarSizeRange.Companion.BYTES_PER_MB
import org.tool.kit.domain.junk.formatAarMegabytes
import org.tool.kit.domain.junk.formatAarSize
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.aar_maximum_size
import org.tool.kit.shared.generated.resources.aar_minimum_size
import org.tool.kit.shared.generated.resources.aar_size_range_invalid
import org.tool.kit.shared.generated.resources.aar_size_slider_step

@Composable
internal fun AarSizeFields(
    form: JunkAarSizeForm,
    onIntent: (JunkCodeIntent) -> Unit,
    stackFields: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AarFormPair(
            stacked = stackFields,
            first = { modifier ->
                AarSizeField(form.minimumMb, stringResource(Res.string.aar_minimum_size), form.range == null, modifier) {
                    onIntent(JunkCodeIntent.MinimumSizeChanged(it))
                }
            },
            second = { modifier ->
                AarSizeField(form.maximumMb, stringResource(Res.string.aar_maximum_size), form.range == null, modifier) {
                    onIntent(JunkCodeIntent.MaximumSizeChanged(it))
                }
            },
        )
        if (form.range == null) {
            Text(
                stringResource(Res.string.aar_size_range_invalid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun AarSizeField(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    val decimal = remember { Regex("[0-9]*[.,]?[0-9]*") }
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { input -> input.trim().takeIf(decimal::matches)?.let(onChange) },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        suffix = { Text("MB") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
internal fun AarSizeSlider(form: JunkAarSizeForm, modifier: Modifier, onChange: (JunkAarSizeRange) -> Unit) {
    val selection = form.sliderSelection
    val bounds = form.sliderBounds
    val start = bounds.snapToSizeStep(selection.minimumBytes)
    val end = bounds.snapToSizeStep(selection.maximumBytes)
    val width = bounds.last - bounds.first
    val intervals = width / AAR_SIZE_STEP_BYTES
    // Compose materializes each discrete tick. Round drag values directly for large expansions.
    val steps = if (intervals in 2L..2048L && width % AAR_SIZE_STEP_BYTES == 0L) intervals.toInt() - 1 else 0
    val colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant)
    val thumbSize = DpSize(width = 6.dp, height = 36.dp)
    val startInteractionSource = remember { MutableInteractionSource() }
    val endInteractionSource = remember { MutableInteractionSource() }
    Column(modifier) {
        RangeSlider(
            modifier = Modifier.fillMaxWidth().testTag("aar-size-range"),
            value = start.toSliderMegabytes()..end.toSliderMegabytes(),
            valueRange = bounds.first.toSliderMegabytes()..bounds.last.toSliderMegabytes(),
            steps = steps,
            colors = colors,
            startInteractionSource = startInteractionSource,
            endInteractionSource = endInteractionSource,
            startThumb = {
                SliderDefaults.Thumb(
                    interactionSource = startInteractionSource,
                    colors = colors,
                    thumbSize = thumbSize,
                )
            },
            endThumb = {
                SliderDefaults.Thumb(
                    interactionSource = endInteractionSource,
                    colors = colors,
                    thumbSize = thumbSize,
                )
            },
            track = {
                SliderDefaults.Track(
                    rangeSliderState = it,
                    modifier = Modifier.height(8.dp),
                    colors = colors,
                    drawStopIndicator = null,
                    drawTick = { _, _ -> },
                    thumbTrackGapSize = 2.dp,
                    trackInsideCornerSize = 2.dp,
                )
            },
            onValueChange = { value ->
                val minimum = bounds.snapToSizeStep(value.start.toSliderBytes())
                val maximum = bounds.snapToSizeStep(value.endInclusive.toSliderBytes())
                val startDelta = abs(minimum - start)
                val endDelta = abs(maximum - end)
                // Moving one thumb preserves the exact byte count of the other.
                val updated = when {
                    startDelta > endDelta -> selection.copy(minimumBytes = minimum.coerceAtMost(selection.maximumBytes))
                    endDelta > 0 -> selection.copy(maximumBytes = maximum.coerceAtLeast(selection.minimumBytes))
                    else -> selection
                }
                if (updated != selection) onChange(updated)
            },
        )
        val labelStyle = MaterialTheme.typography.labelSmall
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bounds.first.formatAarSize(), Modifier.weight(1f), color = labelColor, style = labelStyle)
            Text(
                stringResource(Res.string.aar_size_slider_step, AAR_SIZE_STEP_BYTES.formatAarMegabytes()),
                Modifier.weight(1f),
                color = labelColor,
                textAlign = TextAlign.Center,
                style = labelStyle,
            )
            Text(bounds.last.formatAarSize(), Modifier.weight(1f), color = labelColor, textAlign = TextAlign.End, style = labelStyle)
        }
    }
}

private fun Long.toSliderMegabytes() = (toDouble() / BYTES_PER_MB).toFloat()

private fun Float.toSliderBytes() = (toDouble() * BYTES_PER_MB).roundToLong()

private fun LongRange.snapToSizeStep(bytes: Long): Long {
    val bounded = bytes.coerceIn(this)
    if (bounded == last) return last
    val remainder = (bounded - first) % AAR_SIZE_STEP_BYTES
    val lower = bounded - remainder
    return if (remainder < AAR_SIZE_STEP_BYTES / 2) lower
    else lower + minOf(last - lower, AAR_SIZE_STEP_BYTES)
}

