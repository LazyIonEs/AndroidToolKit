@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.ApkArchiveInformation
import org.tool.kit.domain.apk.ApkFileCategory
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_abi_count
import org.tool.kit.shared.generated.resources.apk_info_all
import org.tool.kit.shared.generated.resources.apk_info_analysis_unavailable
import org.tool.kit.shared.generated.resources.apk_info_apk_size
import org.tool.kit.shared.generated.resources.apk_info_archive_overhead
import org.tool.kit.shared.generated.resources.apk_info_breakdown
import org.tool.kit.shared.generated.resources.apk_info_browse_files
import org.tool.kit.shared.generated.resources.apk_info_composition
import org.tool.kit.shared.generated.resources.apk_info_copy
import org.tool.kit.shared.generated.resources.apk_info_detail_matches
import org.tool.kit.shared.generated.resources.apk_info_file_count
import org.tool.kit.shared.generated.resources.apk_info_files_sorted
import org.tool.kit.shared.generated.resources.apk_info_original_size
import org.tool.kit.shared.generated.resources.apk_info_metadata_zip_label
import org.tool.kit.shared.generated.resources.apk_info_abi
import org.tool.kit.shared.generated.resources.apk_info_no_native_libraries
import org.tool.kit.shared.generated.resources.apk_info_sdk_minimum
import org.tool.kit.shared.generated.resources.apk_info_sdk_target
import org.tool.kit.shared.generated.resources.apk_info_sdk_compile
import org.tool.kit.shared.generated.resources.apk_info_path_checksums
import org.tool.kit.shared.generated.resources.apk_info_runtime
import org.tool.kit.shared.generated.resources.apk_info_search_details
import org.tool.kit.shared.generated.resources.apk_info_size
import org.tool.kit.shared.generated.resources.apk_info_size_help
import org.tool.kit.shared.generated.resources.apk_info_tab_libraries
import org.tool.kit.shared.generated.resources.apk_info_total_archive
import kotlin.math.roundToInt

@Composable
internal fun ApkPackageWorkspace(
    result: ApkInformationResultUi,
    onCopy: (String) -> Unit,
    onLibraries: () -> Unit,
    onChecks: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val pages = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val navigate: (Int) -> Unit = { focus.clearFocus(); page = it }
    ApkPageMotion(page, forward = { a, b -> b > a }) { current ->
        pages.SaveableStateProvider(current) {
            when (current) {
                0 -> ApkPackageOverview(
                    result,
                    onCopy,
                    { navigate(1) },
                    { navigate(2) },
                    onLibraries,
                    onChecks
                )

                1 -> ApkPackagePage(result.archive, 20.dp, onCopy) { navigate(0) }
                2 -> ApkFullBreakdown(result.archive, { navigate(0) }, { navigate(1) })
            }
        }
    }
}

@Composable
private fun ApkPackageOverview(
    result: ApkInformationResultUi, onCopy: (String) -> Unit, onFiles: () -> Unit,
    onBreakdown: () -> Unit, onLibraries: () -> Unit, onChecks: () -> Unit
) {
    val archive = result.archive
    val sizes = remember(archive) {
        archive?.files.orEmpty().groupBy { it.category }
            .mapValues { (_, files) -> files.sumOf { it.compressedSize } }
    }
    // Always include resources, even when another category or metadata is larger.
    val categories = remember(sizes) {
        ApkFileCategory.entries.filter { it != ApkFileCategory.Metadata }
            .sortedByDescending { sizes[it] ?: 0L }
    }
    val total = archive?.let { it.files.sumOf { file -> file.compressedSize } + it.overheadBytes }
        ?: result.size
    Row(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            .testTag("apk-package-overview"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            Modifier.weight(1f).fillMaxHeight(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(Res.string.apk_info_apk_size), style = MaterialTheme.typography.bodySmall)
                    archive?.let { Text(stringResource(Res.string.apk_info_file_count, it.files.size),
                        style = MaterialTheme.typography.bodySmall) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.size.apkBytes(), Modifier.weight(1f), style = MaterialTheme.typography.displaySmall)
                    ApkCopyButton(stringResource(Res.string.apk_info_copy, stringResource(Res.string.apk_info_size)),
                        { onCopy(result.size.apkBytes()) })
                }
                ApkFillScrollColumn(Modifier.weight(1f).fillMaxWidth().testTag("apk-size-categories")) {
                    if (archive == null) ApkDetailEmpty(true, false) else categories.forEach { category ->
                        ApkSizeBar(apkCategoryLabel(category), sizes[category] ?: 0L, total,
                            Modifier.padding(bottom = if (category == categories.last()) 0.dp else 8.dp)
                                .testTag("apk-overview-size-${category.name}"))
                    }
                }
                if (archive != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(Res.string.apk_info_metadata_zip_label), style = MaterialTheme.typography.bodySmall)
                                Text(((sizes[ApkFileCategory.Metadata] ?: 0L) + archive.overheadBytes).apkBytes(),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            TextButton(onBreakdown, Modifier.testTag("apk-open-breakdown")) {
                                Text(stringResource(Res.string.apk_info_breakdown))
                            }
                        }
                    }
                }
            }
        }

        Column(
            Modifier.width(244.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // The runtime panel fills the remaining space; the checksum action stays outside its scroll area.
            Column(Modifier.weight(1f).fillMaxWidth().testTag("apk-overview-details"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ApkPanel(Modifier.fillMaxWidth(), padding = 12, gap = 0) {
                    TextButton(onLibraries, Modifier.fillMaxWidth().testTag("apk-open-Libraries")) {
                        Icon(
                            Icons.Outlined.Memory,
                            null,
                            Modifier.size(22.dp)
                        ); Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(Res.string.apk_info_tab_libraries))
                            Text(archive?.nativeLibraries?.let {
                                "${it.size} · ${
                                    stringResource(
                                        Res.string.apk_info_abi_count,
                                        it.map { lib -> lib.abi }.distinct().size
                                    )
                                }"
                            }
                                ?: stringResource(Res.string.apk_info_analysis_unavailable),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                    }
                    HorizontalDivider()
                    TextButton(onFiles, Modifier.fillMaxWidth().testTag("apk-open-Package")) {
                        Text(stringResource(Res.string.apk_info_browse_files)); Spacer(
                        Modifier.width(
                            ButtonDefaults.IconSpacing
                        )
                    ); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
                ApkRuntimeRequirements(result, Modifier.weight(1f).fillMaxWidth())
            }
            OutlinedButton(onChecks, Modifier.fillMaxWidth().testTag("apk-overview-checksums")) {
                Icon(Icons.Outlined.Fingerprint, null, Modifier.size(18.dp)); Spacer(
                Modifier.width(
                    8.dp
                )
            )
                Text(stringResource(Res.string.apk_info_path_checksums))
            }
        }
    }
}

/** Three comparable SDK values and a separate architecture readout make the compact panel scannable. */
@Composable
private fun ApkRuntimeRequirements(result: ApkInformationResultUi, modifier: Modifier) {
    val abis = result.archive?.nativeLibraries?.map { it.abi }?.distinct()?.sorted()
    Surface(modifier.testTag("apk-runtime"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        ApkFillScrollColumn(Modifier.fillMaxSize(), padding = 12) {
            Text(stringResource(Res.string.apk_info_runtime), Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Res.string.apk_info_sdk_minimum to result.minSdkVersion,
                    Res.string.apk_info_sdk_target to result.targetSdkVersion,
                    Res.string.apk_info_sdk_compile to result.compileSdkVersion
                ).forEachIndexed { index, (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(label), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(apkDisplayValue(value), style = MaterialTheme.typography.titleLarge,
                            color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.apk_info_abi), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when {
                        abis == null -> apkDisplayValue(result.nativeCode)
                        abis.isEmpty() -> stringResource(Res.string.apk_info_no_native_libraries)
                        else -> abis.joinToString(", ")
                    }, style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (abis?.isEmpty() == true) FontFamily.Default else FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
internal fun ApkPackagePage(
    archive: ApkArchiveInformation?,
    inset: Dp,
    onCopy: (String) -> Unit,
    onBack: () -> Unit = {}
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val files = archive?.files
    val sorted = remember(files) { files.orEmpty().sortedByDescending { it.compressedSize } }
    val filtered = remember(
        sorted,
        query,
        category
    ) {
        sorted.filter {
            (category == null || it.category.name == category) && it.path.contains(
                query.trim(),
                true
            )
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement =
        MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    Column(
        Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ApkBackHeading(
            stringResource(Res.string.apk_info_files_sorted),
            stringResource(Res.string.apk_info_composition),
            onBack,
            endText = files?.let {
                stringResource(
                    Res.string.apk_info_detail_matches,
                    filtered.size,
                    it.size
                )
            })
        ApkExpandableSearch(
            query,
            { query = it; scope.launch { listState.scrollToItem(0) } },
            !files.isNullOrEmpty(),
            stringResource(Res.string.apk_info_search_details),
            searchExpanded,
            { searchExpanded = it }) {
            ApkFilterButtons(
                category,
                listOf(ApkFilterOption(null, stringResource(Res.string.apk_info_all))) +
                        ApkFileCategory.entries.map {
                            ApkFilterOption(
                                it.name,
                                apkCategoryLabel(it)
                            )
                        },
                "apk-file-filter"
            ) {
                category = it; scope.launch { listState.scrollToItem(0) }
            }
        }
        ApkListContainer(Modifier.weight(1f, fill = false).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxWidth().testTag("apk-files-list"), state = listState) {
                if (filtered.isEmpty()) item("empty") {
                    ApkDetailEmpty(
                        files == null,
                        files?.isEmpty() == true
                    )
                }
                items(filtered, key = { it.path }) { file ->
                    ListItem(
                        onClick = { onCopy(file.path) },
                        modifier = Modifier.animateItem(effects, placement, effects).fillMaxWidth()
                            .testTag("apk-file-${file.path}"),
                        shapes = apkInfoRowShapes(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        supportingContent = {
                            Text(
                                "${apkCategoryLabel(file.category)} · ${
                                    stringResource(
                                        Res.string.apk_info_original_size
                                    )
                                } ${file.size.apkBytes()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    file.compressedSize.apkBytes(),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                ApkCopyButton(
                                    stringResource(Res.string.apk_info_copy, file.path),
                                    { onCopy(file.path) })
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    ) {
                        Text(
                            file.path,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkFullBreakdown(
    archive: ApkArchiveInformation?,
    onBack: () -> Unit,
    onFiles: () -> Unit
) {
    Column(
        Modifier.fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ApkBackHeading(
            stringResource(Res.string.apk_info_breakdown),
            stringResource(Res.string.apk_info_composition),
            onBack
        )
        if (archive == null) ApkDetailEmpty(true, false) else {
            val groups = remember(archive) { archive.files.groupBy { it.category } }
            val total = archive.files.sumOf { it.compressedSize } + archive.overheadBytes
            Surface(Modifier.weight(1f).fillMaxWidth().testTag("apk-size-breakdown"),
                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                ApkFillScrollColumn(Modifier.fillMaxSize(), padding = 16) {
                    Text(stringResource(Res.string.apk_info_total_archive, total.apkBytes()),
                        Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ApkFileCategory.entries.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                                pair.forEach { category ->
                                    Box(Modifier.weight(1f)) {
                                        ApkSizeBar(apkCategoryLabel(category), groups[category].orEmpty().sumOf { it.compressedSize }, total)
                                    }
                                }
                            }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(Res.string.apk_info_archive_overhead), style = MaterialTheme.typography.bodyMedium)
                            Text(archive.overheadBytes.apkBytes(), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(stringResource(Res.string.apk_info_size_help), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        TextButton(onFiles, Modifier.align(Alignment.End).testTag("apk-breakdown-files")) {
            Text(stringResource(Res.string.apk_info_browse_files)); Spacer(
            Modifier.width(
                ButtonDefaults.IconSpacing
            )
        ); Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun ApkSizeBar(label: String, bytes: Long, total: Long, modifier: Modifier = Modifier) {
    val share = bytes.toDouble() / total.coerceAtLeast(1)
    val percent = if (share > 0 && share < .01) "<1" else (share * 100).roundToInt().toString()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${bytes.apkBytes()} · $percent%", style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { share.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .20f)
        )
    }
}
