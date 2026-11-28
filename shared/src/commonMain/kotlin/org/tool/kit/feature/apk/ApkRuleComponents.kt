@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.ApkLibrarySource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_sdk_contributors
import org.tool.kit.shared.generated.resources.apk_info_sdk_description
import org.tool.kit.shared.generated.resources.apk_info_sdk_detail_error
import org.tool.kit.shared.generated.resources.apk_info_sdk_detail_loading
import org.tool.kit.shared.generated.resources.apk_info_sdk_detail_retry
import org.tool.kit.shared.generated.resources.apk_info_sdk_detail_unavailable
import org.tool.kit.shared.generated.resources.apk_info_sdk_dev_team
import org.tool.kit.shared.generated.resources.apk_info_sdk_match_exact
import org.tool.kit.shared.generated.resources.apk_info_sdk_match_regex
import org.tool.kit.shared.generated.resources.apk_info_sdk_match_rule
import org.tool.kit.shared.generated.resources.apk_info_sdk_open_link
import org.tool.kit.shared.generated.resources.apk_info_sdk_recognition
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_action
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_activity
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_dex
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_link
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_native
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_provider
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_receiver
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_service
import org.tool.kit.shared.generated.resources.apk_info_sdk_source_static

@Composable
internal fun ApkRuleIcon(
    iconId: String?,
    isSimpleColorIcon: Boolean,
    icons: Map<String, ImageBitmap>,
    fallback: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val bitmap = iconId?.let(icons::get)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier.size(size),
            colorFilter = if (isSimpleColorIcon) ColorFilter.tint(LocalContentColor.current) else null,
        )
    } else {
        Icon(fallback, null, modifier.size(size))
    }
}

@Composable
internal fun ApkRuleNavigationRow(
    title: String,
    subtitle: String,
    fullValue: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shapes = apkInfoRowShapes(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        leadingContent = icon,
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        ApkInfoTooltip(fullValue) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ApkRuleDetailSection(
    uuid: String,
    ruleId: Int,
    label: String,
    iconId: String?,
    isSimpleColorIcon: Boolean,
    hasDetail: Boolean,
    matchedByRegex: Boolean?,
    ruleName: String?,
    icons: Map<String, ImageBitmap>,
    detailState: RuleDetailState?,
    onLoadDetail: (String, Int, Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
    fallbackIcon: ImageVector = Icons.Outlined.Extension,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uuid, ruleId, hasDetail) {
        if (hasDetail && detailState == null) {
            onLoadDetail(uuid, ruleId, false)
        }
    }
    Column(modifier.fillMaxWidth().testTag("apk-rule-detail-section"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.apk_info_sdk_recognition),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ApkRuleIcon(iconId, isSimpleColorIcon, icons, fallbackIcon, size = 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                if (matchedByRegex != null) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            stringResource(
                                if (matchedByRegex) Res.string.apk_info_sdk_match_regex
                                else Res.string.apk_info_sdk_match_exact
                            ),
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        if (!ruleName.isNullOrBlank()) {
            ApkCopyField(
                stringResource(Res.string.apk_info_sdk_match_rule),
                ruleName,
                onCopy,
                technical = true,
            )
        }
        if (!hasDetail) {
            ApkDetailNote(stringResource(Res.string.apk_info_sdk_detail_unavailable))
        } else {
            when (detailState) {
                null, RuleDetailState.Loading -> {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(Res.string.apk_info_sdk_detail_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                RuleDetailState.Unavailable -> {
                    ApkDetailNote(stringResource(Res.string.apk_info_sdk_detail_unavailable))
                }

                RuleDetailState.Error -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            stringResource(Res.string.apk_info_sdk_detail_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = { onLoadDetail(uuid, ruleId, true) }) {
                            Text(stringResource(Res.string.apk_info_sdk_detail_retry))
                        }
                    }
                }

                is RuleDetailState.Loaded -> {
                    val detail = detailState.detail
                    if (detail.devTeam.isNotBlank()) {
                        ApkCopyField(stringResource(Res.string.apk_info_sdk_dev_team), detail.devTeam, onCopy)
                    }
                    if (detail.description.isNotBlank()) {
                        ApkDetailField(stringResource(Res.string.apk_info_sdk_description), detail.description)
                    }
                    if (detail.sourceLink.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ApkCopyField(
                                stringResource(Res.string.apk_info_sdk_source_link),
                                detail.sourceLink,
                                onCopy,
                                Modifier.weight(1f),
                                technical = true,
                            )
                            Spacer(Modifier.width(4.dp))
                            ApkInfoTooltip(stringResource(Res.string.apk_info_sdk_open_link)) {
                                IconButton(
                                    onClick = { onOpenUrl(detail.sourceLink) },
                                    modifier = Modifier.testTag("apk-rule-open-link"),
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.OpenInNew,
                                        stringResource(Res.string.apk_info_sdk_open_link),
                                        Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (detail.contributors.isNotEmpty()) {
                        HorizontalDivider()
                        ApkDetailField(
                            stringResource(Res.string.apk_info_sdk_contributors),
                            detail.contributors.joinToString(", "),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun apkLibrarySourceLabel(source: ApkLibrarySource): String = stringResource(
    when (source) {
        ApkLibrarySource.Native -> Res.string.apk_info_sdk_source_native
        ApkLibrarySource.Activity -> Res.string.apk_info_sdk_source_activity
        ApkLibrarySource.Service -> Res.string.apk_info_sdk_source_service
        ApkLibrarySource.Receiver -> Res.string.apk_info_sdk_source_receiver
        ApkLibrarySource.Provider -> Res.string.apk_info_sdk_source_provider
        ApkLibrarySource.Dex -> Res.string.apk_info_sdk_source_dex
        ApkLibrarySource.Static -> Res.string.apk_info_sdk_source_static
        ApkLibrarySource.Action -> Res.string.apk_info_sdk_source_action
    }
)
