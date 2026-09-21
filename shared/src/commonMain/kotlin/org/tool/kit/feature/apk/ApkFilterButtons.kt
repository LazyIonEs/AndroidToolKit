package org.tool.kit.feature.apk

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal data class ApkFilterOption(val value: String?, val label: String)

/** Native chips keep their width when selected; extra ABI names remain horizontally reachable. */
@Composable
internal fun ApkFilterButtons(
    value: String?,
    options: List<ApkFilterOption>,
    tagPrefix: String,
    onSelect: (String?) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = value == option.value, onClick = { onSelect(option.value) },
                modifier = Modifier.testTag("$tagPrefix-${option.value ?: "All"}"),
                // Flat native chips keep hover feedback inside the container without a cast shadow.
                elevation = FilterChipDefaults.filterChipElevation(hoveredElevation = 0.dp),
                label = { Text(option.label, maxLines = 1) })
        }
    }
}
