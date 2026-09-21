@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package org.tool.kit.feature.apk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.*
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.utils.formatFileSize

@Composable
internal fun ApkDetailSearch(
    query: String,
    onQuery: (String) -> Unit,
    enabled: Boolean,
    label: String,
    tag: String = "apk-detail-search",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        query,
        onQuery,
        modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = label },
        enabled = enabled,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                AnimatedVisibility(
                    query.isNotEmpty(),
                    enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
                ) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(
                            Icons.Outlined.Close,
                            stringResource(Res.string.apk_info_clear_search)
                        )
                    }
                }
            }
        })
}

@Composable
internal fun ApkDetailCount(matches: Int, total: Int?) {
    if (total != null) Text(
        stringResource(Res.string.apk_info_detail_matches, matches, total),
        Modifier.padding(vertical = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun ApkDetailEmpty(unavailable: Boolean, empty: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            stringResource(
                when {
                    unavailable -> Res.string.apk_info_analysis_unavailable
                    empty -> Res.string.apk_info_no_entries
                    else -> Res.string.apk_info_no_detail_matches
                }
            ), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
internal fun ApkDetailNote(value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun ApkDetailField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCopy: ((String) -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                apkDisplayValue(value), style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (onCopy != null) FontFamily.Monospace else FontFamily.Default
            )
        }
        if (onCopy != null) ApkCopyButton(
            stringResource(Res.string.apk_info_copy, label),
            { onCopy(value) },
            value.isNotBlank()
        )
    }
}

@Composable
internal fun ApkAlignmentText(label: String, status: ApkAlignment) {
    val icon = when (status) {
        ApkAlignment.Aligned -> Icons.Outlined.CheckCircle
        ApkAlignment.Unaligned -> Icons.Outlined.WarningAmber
        ApkAlignment.NotApplicable -> Icons.Outlined.RemoveCircleOutline
        ApkAlignment.Unknown -> Icons.Outlined.HelpOutline
    }
    val color =
        if (status == ApkAlignment.Unaligned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = color)
        Text(
            "$label · ${apkAlignmentLabel(status)}",
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
internal fun apkAlignmentLabel(status: ApkAlignment): String = stringResource(
    when (status) {
        ApkAlignment.Aligned -> Res.string.apk_info_alignment_pass
        ApkAlignment.Unaligned -> Res.string.apk_info_alignment_fail
        ApkAlignment.NotApplicable -> Res.string.apk_info_not_applicable
        ApkAlignment.Unknown -> Res.string.apk_info_analysis_unavailable
    }
)

@Composable
internal fun apkExportedLabel(status: ApkExportedDeclaration): String = stringResource(
    when (status) {
        ApkExportedDeclaration.Enabled -> Res.string.apk_info_declared_true
        ApkExportedDeclaration.Disabled -> Res.string.apk_info_declared_false
        ApkExportedDeclaration.Unspecified -> Res.string.apk_info_undeclared
        ApkExportedDeclaration.Unknown -> Res.string.apk_info_analysis_unavailable
    }
)

@Composable
internal fun apkCategoryLabel(category: ApkFileCategory): String = stringResource(
    when (category) {
        ApkFileCategory.Dex -> Res.string.apk_info_category_dex
        ApkFileCategory.Native -> Res.string.apk_info_category_native
        ApkFileCategory.Resources -> Res.string.apk_info_category_resources
        ApkFileCategory.Assets -> Res.string.apk_info_category_assets
        ApkFileCategory.Metadata -> Res.string.apk_info_category_metadata
        ApkFileCategory.Other -> Res.string.apk_info_category_other
    }
)

internal fun Long.apkBytes() =
    if (this < 1024) "$this B" else formatFileSize(scale = 1, withInterval = true)

/** Compact native row; the trailing chevron opens a dedicated detail page instead of expanding in place. */
@Composable
internal fun ApkNavigationRow(
    title: String, subtitle: String, fullValue: String, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    ListItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shapes = apkInfoRowShapes(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ) {
        ApkInfoTooltip(fullValue) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
