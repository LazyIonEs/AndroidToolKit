package org.tool.kit.feature.junk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.junk.BYTES_PER_TENTH_MB
import org.tool.kit.domain.junk.JunkAarSizeRange
import org.tool.kit.domain.junk.JunkAarSizeRange.Companion.BYTES_PER_MB
import org.tool.kit.domain.junk.formatRoundedAarSize
import org.tool.kit.model.JunkMode
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.aar_maximum_size
import org.tool.kit.shared.generated.resources.aar_minimum_size
import org.tool.kit.shared.generated.resources.aar_size_range_invalid
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
internal fun AarSizeFields(
    form: JunkAarSizeForm,
    mode: JunkMode,
    onIntent: (JunkCodeIntent) -> Unit,
    stackFields: Boolean,
) {
    val isError = form.range(mode) == null
    val finishEditing = { onIntent(JunkCodeIntent.SizeEditingFinished) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AarFormPair(
            stacked = stackFields,
            first = { modifier ->
                AarSizeField(form.minimumMb, stringResource(Res.string.aar_minimum_size), isError, modifier, finishEditing) {
                    onIntent(JunkCodeIntent.MinimumSizeChanged(it))
                }
            },
            second = { modifier ->
                AarSizeField(form.maximumMb, stringResource(Res.string.aar_maximum_size), isError, modifier, finishEditing) {
                    onIntent(JunkCodeIntent.MaximumSizeChanged(it))
                }
            },
        )
        if (isError) {
            Text(
                stringResource(Res.string.aar_size_range_invalid, *mode.sizeRangeMessageArgs()),
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
    onEditFinished: () -> Unit,
    onChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        modifier = modifier.onFocusChanged { focus ->
            val lostFocus = focused && !focus.isFocused
            focused = focus.isFocused
            if (lostFocus) onEditFinished()
        },
        value = value,
        onValueChange = { input -> input.trim().takeIf(String::isAarSizeInput)?.let(onChange) },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        suffix = { Text("MB") },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
internal fun AarSizeSlider(
    form: JunkAarSizeForm,
    mode: JunkMode,
    modifier: Modifier,
    onChange: (JunkAarSizeRange) -> Unit,
) {
    val selection = form.sliderSelection(mode)
    val bounds = mode.sizeBoundsBytes
    val start = selection.minimumBytes.toSliderMegabytes()
    val end = selection.maximumBytes.toSliderMegabytes()
    val minimumGap = mode.minimumSizeGapBytes
    val colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant)
    Column(modifier) {
        RangeSlider(
            modifier = Modifier.fillMaxWidth().testTag("aar-size-range"),
            value = start..end,
            valueRange = bounds.first.toSliderMegabytes()..bounds.last.toSliderMegabytes(),
            steps = 0,
            colors = colors,
            onValueChange = { value ->
                val startDelta = abs(value.start - start)
                val endDelta = abs(value.endInclusive - end)
                // Moving one thumb preserves the exact byte count of the other.
                val updated = when {
                    startDelta > endDelta -> selection.copy(
                        minimumBytes = value.start.toSliderBytes().coerceIn(bounds.first, selection.maximumBytes - minimumGap),
                    )
                    endDelta > 0f -> selection.copy(
                        maximumBytes = value.endInclusive.toSliderBytes().coerceIn(selection.minimumBytes + minimumGap, bounds.last),
                    )
                    else -> selection
                }
                if (updated != selection) onChange(updated)
            },
        )
        val labelStyle = MaterialTheme.typography.labelSmall
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bounds.first.formatRoundedAarSize(), Modifier.weight(1f), color = labelColor, style = labelStyle)
            Text(bounds.last.formatRoundedAarSize(), Modifier.weight(1f), color = labelColor, textAlign = TextAlign.End, style = labelStyle)
        }
    }
}

private fun Long.toSliderMegabytes() = (toDouble() / BYTES_PER_MB).toFloat()

private fun Float.toSliderBytes() = (this * 10).roundToLong() * BYTES_PER_TENTH_MB
