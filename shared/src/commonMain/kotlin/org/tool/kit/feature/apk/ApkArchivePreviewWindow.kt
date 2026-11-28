@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.github.panpf.zoomimage.CoilZoomAsyncImage
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.apk.ApkArchivePreviewKind
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_copy
import org.tool.kit.shared.generated.resources.apk_info_file_path
import org.tool.kit.shared.generated.resources.apk_info_file_preview_loading
import org.tool.kit.shared.generated.resources.apk_info_file_preview_title
import org.tool.kit.shared.generated.resources.apk_info_file_preview_unavailable
import org.tool.kit.shared.generated.resources.apk_info_image_dimensions
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme
import org.tool.kit.utils.getImageRequest

@Composable
internal fun ApkArchivePreviewWindow(
    appLabel: String,
    state: ArchiveEntryPreviewState,
    dark: Boolean,
    onCopy: (String) -> Unit,
    onCloseRequest: () -> Unit,
) {
    val entryPath = state.previewEntryPath() ?: return
    val isXml = state.previewKind() == ApkArchivePreviewKind.Xml
    key(entryPath) {
        val fileName = entryPath.substringAfterLast('/')
        val baseTitle = fileName.ifBlank { stringResource(Res.string.apk_info_file_preview_title) }
        val windowTitle = if (appLabel.isBlank()) baseTitle else "$baseTitle — $appLabel"
        Window(
            onCloseRequest = onCloseRequest,
            state = rememberWindowState(
                size = if (isXml) DpSize(860.dp, 640.dp) else DpSize(680.dp, 560.dp),
            ),
            title = windowTitle,
            icon = painterResource(Res.drawable.icon),
        ) {
            AppTheme(dark) {
                Surface(Modifier.fillMaxSize()) {
                    ApkArchivePreviewContent(
                        state = state,
                        dark = dark,
                        onCopy = onCopy,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ApkArchivePreviewContent(
    state: ArchiveEntryPreviewState,
    dark: Boolean,
    onCopy: (String) -> Unit,
) {
    val entryPath = state.previewEntryPath().orEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f).testTag("apk-archive-preview-path")) {
                ApkInfoTooltip(entryPath) {
                    Text(
                        text = entryPath,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state is ArchiveEntryPreviewState.ImageLoaded) {
                Text(
                    text = stringResource(
                        Res.string.apk_info_image_dimensions,
                        state.bitmap.width,
                        state.bitmap.height,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ApkCopyButton(
                label = stringResource(Res.string.apk_info_copy, stringResource(Res.string.apk_info_file_path)),
                onClick = { onCopy(entryPath) },
                enabled = entryPath.isNotBlank(),
            )
        }
        HorizontalDivider()
        Box(Modifier.weight(1f).fillMaxWidth().testTag("apk-archive-preview-content")) {
            when (state) {
                ArchiveEntryPreviewState.Idle -> Unit
                is ArchiveEntryPreviewState.Loading -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(Res.string.apk_info_file_preview_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ArchiveEntryPreviewState.Unavailable -> {
                    Text(
                        stringResource(Res.string.apk_info_file_preview_unavailable),
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is ArchiveEntryPreviewState.XmlLoaded -> {
                    ApkManifestPreviewContent(
                        state = ManifestPreviewState.Loaded(state.xml),
                        dark = dark,
                        onCopy = onCopy,
                    )
                }

                is ArchiveEntryPreviewState.ImageLoaded -> {
                    Box(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        CoilZoomAsyncImage(
                            model = getImageRequest(state.bitmap),
                            contentDescription = state.entryPath,
                            modifier = Modifier.fillMaxSize().testTag("apk-archive-image-preview"),
                        )
                    }
                }
            }
        }
    }
}

private fun ArchiveEntryPreviewState.previewEntryPath(): String? = when (this) {
    ArchiveEntryPreviewState.Idle -> null
    is ArchiveEntryPreviewState.Loading -> entryPath
    is ArchiveEntryPreviewState.XmlLoaded -> entryPath
    is ArchiveEntryPreviewState.ImageLoaded -> entryPath
    is ArchiveEntryPreviewState.Unavailable -> entryPath
}

private fun ArchiveEntryPreviewState.previewKind(): ApkArchivePreviewKind? = when (this) {
    ArchiveEntryPreviewState.Idle -> null
    is ArchiveEntryPreviewState.Loading -> kind
    is ArchiveEntryPreviewState.XmlLoaded -> ApkArchivePreviewKind.Xml
    is ArchiveEntryPreviewState.ImageLoaded -> ApkArchivePreviewKind.Image
    is ArchiveEntryPreviewState.Unavailable -> kind
}
