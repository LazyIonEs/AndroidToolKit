package org.tool.kit.feature.cleaner

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Keep the IME's selection and composing region locally; draft collection may lag a frame. */
@Composable
internal fun CleanerRuleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var previousModelText by remember { mutableStateOf(value) }
    val pendingEchoes = remember { mutableListOf<String>() }

    // Acknowledge our own draft updates without replacing newer input or ending composition.
    // Actual external replacements, including Restore defaults, still update the editor.
    if (value != previousModelText) {
        previousModelText = value
        val echo = pendingEchoes.indexOfLast { it == value }
        if (echo >= 0) {
            pendingEchoes.subList(0, echo + 1).clear()
        } else if (value != input.text) {
            pendingEchoes.clear()
            input = TextFieldValue(value, TextRange(value.length))
        }
    }
    OutlinedTextField(
        value = input,
        onValueChange = { next ->
            val textChanged = input.text != next.text
            input = next
            // Update the draft in the same callback, so Save/Copy/Remove see the latest text.
            if (textChanged) {
                pendingEchoes.add(next.text)
                onValueChange(next.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = label,
        supportingText = supportingText,
        isError = isError,
    )
}
