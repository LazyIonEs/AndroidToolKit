package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_n_description
import org.tool.kit.shared.generated.resources.apk_info_n_drop
import org.tool.kit.shared.generated.resources.apk_info_n_more
import org.tool.kit.shared.generated.resources.apk_info_n_more_detail
import org.tool.kit.shared.generated.resources.apk_info_n_start
import org.tool.kit.shared.generated.resources.apk_info_n_step_files
import org.tool.kit.shared.generated.resources.apk_info_n_step_files_detail
import org.tool.kit.shared.generated.resources.apk_info_n_step_groups
import org.tool.kit.shared.generated.resources.apk_info_n_step_groups_detail
import org.tool.kit.shared.generated.resources.apk_info_n_step_libs
import org.tool.kit.shared.generated.resources.apk_info_n_step_libs_detail
import org.tool.kit.shared.generated.resources.apk_info_n_step_size
import org.tool.kit.shared.generated.resources.apk_info_n_step_size_detail
import org.tool.kit.shared.generated.resources.apk_info_n_steps
import org.tool.kit.shared.generated.resources.apk_info_usage_hint

@Composable
internal fun ApkInformationEmptyState(onPickFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("apk-empty")
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                Modifier.width(308.dp).fillMaxHeight().testTag("apk-import-card"),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Icon(Icons.Outlined.DataUsage, null, Modifier.size(48.dp))
                    Text(
                        stringResource(Res.string.apk_info_n_start),
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        stringResource(Res.string.apk_info_n_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    ApkSelectButton(onPickFile, Modifier.fillMaxWidth())
                    Text(
                        stringResource(Res.string.apk_info_n_drop),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            ApkPanel(Modifier.weight(1f).fillMaxHeight(), gap = 8) {
                Text(
                    stringResource(Res.string.apk_info_n_steps),
                    style = MaterialTheme.typography.titleMedium
                )
                val titles = listOf(
                    Res.string.apk_info_n_step_size,
                    Res.string.apk_info_n_step_groups,
                    Res.string.apk_info_n_step_files,
                    Res.string.apk_info_n_step_libs
                )
                val details = listOf(
                    Res.string.apk_info_n_step_size_detail,
                    Res.string.apk_info_n_step_groups_detail,
                    Res.string.apk_info_n_step_files_detail,
                    Res.string.apk_info_n_step_libs_detail
                )
                val icons = listOf(
                    Icons.Outlined.Inventory2,
                    Icons.Outlined.BarChart,
                    Icons.Outlined.Search,
                    Icons.Outlined.Memory
                )
                titles.indices.forEach { i ->
                    ListItem(
                        headlineContent = {
                        Text(
                            stringResource(titles[i]),
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                        supportingContent = {
                            Text(
                                stringResource(details[i]),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = { Icon(icons[i], null, Modifier.size(24.dp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (i < 3) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        ApkPanel(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Outlined.Description, null, Modifier.size(24.dp))
                Column {
                    Text(
                        stringResource(Res.string.apk_info_n_more),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(Res.string.apk_info_n_more_detail),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Row(
            Modifier.testTag("apk-usage-hint"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp))
            Text(
                stringResource(Res.string.apk_info_usage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
