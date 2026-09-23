@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.ApkNativeLibrary
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_alignment_help
import org.tool.kit.shared.generated.resources.apk_info_alignment_title
import org.tool.kit.shared.generated.resources.apk_info_all
import org.tool.kit.shared.generated.resources.apk_info_archive_size
import org.tool.kit.shared.generated.resources.apk_info_compressed_library
import org.tool.kit.shared.generated.resources.apk_info_file
import org.tool.kit.shared.generated.resources.apk_info_file_path
import org.tool.kit.shared.generated.resources.apk_info_original_size
import org.tool.kit.shared.generated.resources.apk_info_search_libraries
import org.tool.kit.shared.generated.resources.apk_info_size_pair
import org.tool.kit.shared.generated.resources.apk_info_stored_library
import org.tool.kit.shared.generated.resources.apk_info_tab_libraries

@Composable
internal fun ApkLibraryPage(
    libraries: List<ApkNativeLibrary>?,
    inset: Dp,
    onCopy: (String) -> Unit
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var abi by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val abis = remember(libraries) {
        libraries.orEmpty().groupingBy { it.abi }.eachCount().entries.sortedBy { it.key }
    }
    val filtered = remember(libraries, query, abi) {
        libraries.orEmpty().filter {
            (abi == null || it.abi == abi) && it.path.contains(query.trim(), true)
        }.sortedWith(compareBy({ it.abi }, { it.path }))
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val placement =
        MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    ApkPageMotion(selected, forward = { _, next -> next != null }) { path ->
        val library = libraries?.firstOrNull { it.path == path }
        if (library != null) Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApkBackHeading(
                library.path.substringAfterLast('/'),
                stringResource(Res.string.apk_info_tab_libraries),
                { selected = null })
            Row(
                Modifier.weight(1f).fillMaxWidth().testTag("apk-library-detail"),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ApkPanel(
                    Modifier.weight(1.2f).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(Res.string.apk_info_file),
                        style = MaterialTheme.typography.titleMedium
                    )
                    ApkCopyField(
                        stringResource(Res.string.apk_info_file_path),
                        library.path,
                        onCopy,
                        technical = true
                    )
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ApkDetailField(
                            stringResource(Res.string.apk_info_original_size),
                            library.size.apkBytes(),
                            Modifier.weight(1f)
                        )
                        ApkDetailField(
                            stringResource(Res.string.apk_info_archive_size),
                            library.compressedSize.apkBytes(),
                            Modifier.weight(1f)
                        )
                    }
                    ApkCopyField("ABI", library.abi, onCopy, technical = true)
                    Text(
                        stringResource(if (library.compressed) Res.string.apk_info_compressed_library else Res.string.apk_info_stored_library),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ApkPanel(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(Res.string.apk_info_alignment_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    ApkAlignmentCard("ELF 16 KB", library.elfAlignment)
                    ApkAlignmentCard("ZIP 16 KB", library.zipAlignment)
                    Text(
                        stringResource(Res.string.apk_info_alignment_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else Column(
            Modifier.fillMaxSize().padding(start = inset, end = inset, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ApkExpandableSearch(
                query,
                { query = it; scope.launch { listState.scrollToItem(0) } },
                !libraries.isNullOrEmpty(),
                stringResource(Res.string.apk_info_search_libraries),
                searchExpanded,
                { searchExpanded = it }) {
                ApkFilterButtons(
                    abi,
                    listOf(ApkFilterOption(null, stringResource(Res.string.apk_info_all))) +
                            abis.map { (option, count) ->
                                ApkFilterOption(
                                    option,
                                    "$option $count"
                                )
                            },
                    "apk-abi-filter"
                ) {
                    abi = it; scope.launch { listState.scrollToItem(0) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ApkDetailCount(filtered.size, libraries?.size)
                if (libraries != null) Text(
                    stringResource(
                        Res.string.apk_info_size_pair,
                        filtered.sumOf { it.size }.apkBytes(),
                        filtered.sumOf { it.compressedSize }.apkBytes()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ApkListContainer(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxWidth().testTag("apk-libraries-list"),
                    state = listState
                ) {
                    if (filtered.isEmpty()) item("empty") {
                        ApkDetailEmpty(
                            libraries == null,
                            libraries?.isEmpty() == true
                        )
                    }
                    items(filtered, key = { it.path }) { item ->
                        ApkNavigationRow(
                            item.path.substringAfterLast('/'),
                            "${item.abi} · ${item.compressedSize.apkBytes()} · ELF 16 KB ${
                                apkAlignmentLabel(item.elfAlignment)
                            }",
                            item.path,
                            Icons.Outlined.Memory,
                            Modifier.animateItem(effects, placement, effects)
                                .testTag("apk-library-${item.path}")
                        ) {
                            focus.clearFocus(); selected = item.path
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkAlignmentCard(label: String, status: org.tool.kit.domain.apk.ApkAlignment) {
    val failed = status == org.tool.kit.domain.apk.ApkAlignment.Unaligned
    Surface(
        Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Box(Modifier.padding(16.dp)) { ApkAlignmentText(label, status) }
    }
}
