@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_clear_search
import org.tool.kit.shared.generated.resources.apk_info_copy_all
import org.tool.kit.shared.generated.resources.apk_info_manifest_lines
import org.tool.kit.shared.generated.resources.apk_info_manifest_loading
import org.tool.kit.shared.generated.resources.apk_info_manifest_matches
import org.tool.kit.shared.generated.resources.apk_info_manifest_preview_title
import org.tool.kit.shared.generated.resources.apk_info_manifest_search
import org.tool.kit.shared.generated.resources.apk_info_manifest_unavailable
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme

private data class ManifestLine(val number: Int, val text: String)

@Composable
internal fun ApkManifestPreviewWindow(
    appLabel: String,
    state: ManifestPreviewState,
    dark: Boolean,
    onCopy: (String) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val baseTitle = stringResource(Res.string.apk_info_manifest_preview_title)
    val windowTitle = if (appLabel.isBlank()) baseTitle else "$baseTitle — $appLabel"
    Window(
        onCloseRequest = onCloseRequest,
        state = rememberWindowState(size = DpSize(860.dp, 640.dp)),
        title = windowTitle,
        icon = painterResource(Res.drawable.icon),
    ) {
        AppTheme(dark) {
            Surface(Modifier.fillMaxSize()) {
                ApkManifestPreviewContent(
                    state = state,
                    dark = dark,
                    onCopy = onCopy,
                )
            }
        }
    }
}

@Composable
internal fun ApkManifestPreviewContent(
    state: ManifestPreviewState,
    dark: Boolean,
    onCopy: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val xml = (state as? ManifestPreviewState.Loaded)?.xml
    val allLines = remember(xml) {
        xml?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.split('\n')
            ?.mapIndexed { index, text -> ManifestLine(index + 1, text) }
            .orEmpty()
    }
    val trimmedQuery = query.trim()
    val visibleLines = remember(allLines, trimmedQuery) {
        if (trimmedQuery.isEmpty()) allLines
        else allLines.filter { it.text.contains(trimmedQuery, ignoreCase = true) }
    }
    val listState = rememberLazyListState()
    val horizontalScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lineNumberDigits = remember(allLines.size) {
        allLines.size.coerceAtLeast(1).toString().length.coerceAtLeast(3)
    }
    val colors = rememberManifestSyntaxColors(dark)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier.weight(1f).testTag("apk-manifest-search"),
                enabled = state is ManifestPreviewState.Loaded,
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                placeholder = {
                    Text(
                        stringResource(Res.string.apk_info_manifest_search),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                stringResource(Res.string.apk_info_clear_search),
                            )
                        }
                    }
                },
            )
            if (state is ManifestPreviewState.Loaded) {
                Text(
                    text = if (trimmedQuery.isEmpty()) {
                        stringResource(Res.string.apk_info_manifest_lines, allLines.size)
                    } else {
                        stringResource(
                            Res.string.apk_info_manifest_matches,
                            visibleLines.size,
                            allLines.size,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = { if (xml != null) onCopy(xml) },
                enabled = xml != null,
                modifier = Modifier.testTag("apk-manifest-copy"),
            ) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.apk_info_copy_all))
            }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth().testTag("apk-manifest-content")) {
            when (state) {
                ManifestPreviewState.Loading -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(Res.string.apk_info_manifest_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ManifestPreviewState.Idle, ManifestPreviewState.Unavailable -> {
                    Text(
                        stringResource(Res.string.apk_info_manifest_unavailable),
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ManifestPreviewState.Loaded -> {
                    val numberWidth = (lineNumberDigits * 9 + 16).dp
                    Row(Modifier.fillMaxSize()) {
                        SelectionContainer(Modifier.weight(1f).fillMaxHeight()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                            ) {
                                items(visibleLines, key = { it.number }) { line ->
                                    val highlighted = remember(line.text, trimmedQuery, colors) {
                                        highlightXmlLine(line.text, trimmedQuery, colors)
                                    }
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = line.number.toString(),
                                            modifier = Modifier.widthIn(min = numberWidth).padding(end = 12.dp),
                                            textAlign = TextAlign.End,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            lineHeight = 20.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                        Box(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
                                            Text(
                                                text = highlighted,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp,
                                                softWrap = false,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.fillMaxHeight().padding(end = 4.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class ManifestSyntaxColors(
    val tag: Color,
    val attribute: Color,
    val value: Color,
    val comment: Color,
    val searchBackground: Color,
)

@Composable
private fun rememberManifestSyntaxColors(dark: Boolean): ManifestSyntaxColors {
    val highlight = MaterialTheme.colorScheme.tertiaryContainer
    return remember(dark, highlight) {
        if (dark) {
            ManifestSyntaxColors(
                tag = Color(0xFF7DD3FC),
                attribute = Color(0xFFC4B5FD),
                value = Color(0xFF86EFAC),
                comment = Color(0xFF94A3B8),
                searchBackground = highlight,
            )
        } else {
            ManifestSyntaxColors(
                tag = Color(0xFF0369A1),
                attribute = Color(0xFF6D28D9),
                value = Color(0xFF15803D),
                comment = Color(0xFF64748B),
                searchBackground = highlight,
            )
        }
    }
}

private val XML_TAG_REGEX = Regex("</?[A-Za-z0-9_.:-]+|/>|>")
private val XML_ATTR_REGEX = Regex("""\s([A-Za-z0-9_.:-]+)\s*=""")
private val XML_STRING_REGEX = Regex("\"[^\"]*\"")

private fun highlightXmlLine(
    line: String,
    query: String,
    colors: ManifestSyntaxColors,
): AnnotatedString = buildAnnotatedString {
    append(line)
    val trimmed = line.trimStart()
    if (trimmed.startsWith("<!--")) {
        addStyle(SpanStyle(color = colors.comment), 0, line.length)
    } else {
        XML_TAG_REGEX.findAll(line).forEach { match ->
            addStyle(SpanStyle(color = colors.tag), match.range.first, match.range.last + 1)
        }
        XML_ATTR_REGEX.findAll(line).forEach { match ->
            val group = match.groups[1] ?: return@forEach
            addStyle(SpanStyle(color = colors.attribute), group.range.first, group.range.last + 1)
        }
        XML_STRING_REGEX.findAll(line).forEach { match ->
            addStyle(SpanStyle(color = colors.value), match.range.first, match.range.last + 1)
        }
    }
    if (query.isNotEmpty()) {
        var index = line.indexOf(query, ignoreCase = true)
        while (index >= 0) {
            addStyle(
                SpanStyle(background = colors.searchBackground),
                index,
                index + query.length,
            )
            index = line.indexOf(query, startIndex = index + query.length, ignoreCase = true)
        }
    }
}
