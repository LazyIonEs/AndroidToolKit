@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_not_provided

@Composable
internal fun ApkInfoTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        content = content
    )
}

@Composable
internal fun ApkCopyButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    ApkInfoTooltip(label) {
        IconButton(onClick, enabled = enabled, modifier = modifier) {
            Icon(Icons.Outlined.ContentCopy, label, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ApkInputFile(path: String) {
    ApkInfoTooltip(path) {
        Text(
            path.substringAfterLast('/').substringAfterLast('\\'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun apkDisplayValue(value: String): String =
    value.ifBlank { stringResource(Res.string.apk_info_not_provided) }

/** Rows form one continuous list; the parent Surface clips its outer corners. */
@Composable
internal fun apkInfoRowShapes(): ListItemShapes {
    val shape = RectangleShape
    return ListItemDefaults.shapes(
        shape = shape, selectedShape = shape, pressedShape = shape,
        focusedShape = shape, hoveredShape = shape, draggedShape = shape
    )
}

/** A single rounded background for contiguous native list rows, with no per-row cards or dividers. */
@Composable
internal fun ApkListContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        content = content
    )
}
