@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package org.tool.kit.feature.apk

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
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
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_abi
import org.tool.kit.shared.generated.resources.apk_info_back
import org.tool.kit.shared.generated.resources.apk_info_channel
import org.tool.kit.shared.generated.resources.apk_info_compatibility
import org.tool.kit.shared.generated.resources.apk_info_compile_sdk
import org.tool.kit.shared.generated.resources.apk_info_composition
import org.tool.kit.shared.generated.resources.apk_info_copy
import org.tool.kit.shared.generated.resources.apk_info_file_path
import org.tool.kit.shared.generated.resources.apk_info_filename
import org.tool.kit.shared.generated.resources.apk_info_icon_preview
import org.tool.kit.shared.generated.resources.apk_info_icon_unavailable
import org.tool.kit.shared.generated.resources.apk_info_launch_file
import org.tool.kit.shared.generated.resources.apk_info_launchable_activity
import org.tool.kit.shared.generated.resources.apk_info_md5
import org.tool.kit.shared.generated.resources.apk_info_min_sdk
import org.tool.kit.shared.generated.resources.apk_info_package
import org.tool.kit.shared.generated.resources.apk_info_path_checksums
import org.tool.kit.shared.generated.resources.apk_info_profile
import org.tool.kit.shared.generated.resources.apk_info_sha256
import org.tool.kit.shared.generated.resources.apk_info_size
import org.tool.kit.shared.generated.resources.apk_info_tab_components
import org.tool.kit.shared.generated.resources.apk_info_tab_libraries
import org.tool.kit.shared.generated.resources.apk_info_tab_permissions
import org.tool.kit.shared.generated.resources.apk_info_target_sdk
import org.tool.kit.shared.generated.resources.apk_info_version_code
import org.tool.kit.shared.generated.resources.apk_info_version_name
import org.tool.kit.shared.generated.resources.apk_info_view_icon
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.theme.AppTheme
import org.tool.kit.utils.getImageRequest

/** A new APK resets the workspace; every tab retains its own queries, detail and scroll position. */
@Composable
internal fun ApkInformationResult(
    result: ApkInformationResultUi,
    inputFile: String?,
    dark: Boolean,
    active: Boolean = true,
    onPickFile: () -> Unit,
    onCopy: (String) -> Unit
) {
    key(inputFile, result) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var checksOrigin by rememberSaveable { mutableStateOf<Int?>(null) }
        var iconOpen by rememberSaveable { mutableStateOf(false) }
        val pages = rememberSaveableStateHolder()
        val focus = LocalFocusManager.current
        val select: (Int) -> Unit = { focus.clearFocus(); checksOrigin = null; tab = it }
        val checks = { focus.clearFocus(); checksOrigin = tab; tab = 4 }
        Column(Modifier.fillMaxSize()) {
            ApkIdentity(result, onCopy, onPickFile) { iconOpen = true }
            SecondaryTabRow(tab) {
                val titles = listOf(
                    stringResource(Res.string.apk_info_composition),
                    stringResource(Res.string.apk_info_tab_permissions),
                    stringResource(Res.string.apk_info_tab_components),
                    stringResource(Res.string.apk_info_tab_libraries),
                    stringResource(Res.string.apk_info_profile)
                )
                val counts = listOf(
                    null,
                    result.usesPermissionList?.size,
                    result.components?.size,
                    result.archive?.nativeLibraries?.size,
                    null
                )
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { select(index) },
                        modifier = Modifier.testTag("apk-tab-$index"),
                        text = {
                            Text(title + (counts[index]?.let { " $it" } ?: ""),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        })
                }
            }
            ApkPageMotion(
                if (checksOrigin != null) 5 else tab,
                Modifier.weight(1f).testTag("apk-pages"),
                forward = { a, b -> b > a }) { page ->
                pages.SaveableStateProvider(page) {
                    when (page) {
                        0 -> ApkPackageWorkspace(result, onCopy, { select(3) }, checks)
                        1 -> ApkPermissionsPage(result.usesPermissionList, onCopy)
                        2 -> ApkComponentPage(result.components, 20.dp, onCopy)
                        3 -> ApkLibraryPage(result.archive?.nativeLibraries, 20.dp, onCopy)
                        4 -> ApkProfilePage(result, inputFile, onCopy, checks)
                        5 -> ApkChecksumsPage(result, inputFile, onCopy) {
                            select(
                                checksOrigin ?: 4
                            )
                        }
                    }
                }
            }
        }
        // Dispose the native icon window immediately when this file leaves the result state.
        if (active && iconOpen && result.icon != null) {
            Window(
                onCloseRequest = { iconOpen = false },
                state = rememberWindowState(size = DpSize(450.dp, 450.dp)),
                title = stringResource(Res.string.apk_info_icon_preview),
                icon = painterResource(Res.drawable.icon),
                alwaysOnTop = true
            ) {
                AppTheme(dark) {
                    Surface(Modifier.fillMaxSize()) {
                        CoilZoomAsyncImage(
                            model = getImageRequest(result.icon),
                            contentDescription = stringResource(Res.string.apk_info_icon_preview),
                            modifier = Modifier.fillMaxSize().testTag("apk-icon-preview")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkIdentity(
    result: ApkInformationResultUi,
    onCopy: (String) -> Unit,
    onPickFile: () -> Unit,
    onIcon: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(horizontal = 20.dp)
            .testTag("apk-overview"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val iconLabel =
            stringResource(if (result.icon == null) Res.string.apk_info_icon_unavailable else Res.string.apk_info_view_icon)
        ApkInfoTooltip(iconLabel) {
            Surface(
                onIcon,
                enabled = result.icon != null,
                modifier = Modifier.size(56.dp).testTag("apk-app-icon"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(Modifier.padding(6.dp), contentAlignment = Alignment.Center) {
                    if (result.icon != null) Image(
                        result.icon,
                        iconLabel,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    else Icon(Icons.Outlined.Android, iconLabel, Modifier.size(28.dp))
                }
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                ApkInfoTooltip(result.label) {
                    Surface(
                        onClick = { onCopy(result.label) },
                        enabled = result.label.isNotBlank(),
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.heightIn(min = 36.dp).testTag("apk-app-name")
                    ) {
                        Text(
                            apkDisplayValue(result.label),
                            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f, fill = false)) {
                        ApkInfoTooltip(result.packageName) {
                            Surface(
                                onClick = { onCopy(result.packageName) },
                                enabled = result.packageName.isNotBlank(),
                                color = Color.Transparent,
                                modifier = Modifier.heightIn(min = 32.dp)
                                    .testTag("apk-package-name"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    apkDisplayValue(result.packageName),
                                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    ApkCopyButton(
                        stringResource(
                            Res.string.apk_info_copy,
                            stringResource(Res.string.apk_info_package)
                        ),
                        { onCopy(result.packageName) },
                        result.packageName.isNotBlank(),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
        Column(
            Modifier.widthIn(max = 160.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "${apkDisplayValue(result.versionName)} · ${result.size.apkBytes()}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${stringResource(Res.string.apk_info_version_code)} ${apkDisplayValue(result.versionCode)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ApkSelectButton(onPickFile, replace = true)
    }
}

@Composable
internal fun ApkCopyField(
    label: String,
    value: String,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    technical: Boolean = false
) {
    Surface(
        onClick = { onCopy(value) },
        enabled = value.isNotBlank(),
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
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
                    apkDisplayValue(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = if (technical) FontFamily.Monospace else FontFamily.Default
                )
            }
            ApkCopyButton(
                stringResource(Res.string.apk_info_copy, label),
                { onCopy(value) },
                value.isNotBlank()
            )
        }
    }
}

@Composable
private fun ApkProfilePage(
    result: ApkInformationResultUi,
    inputFile: String?,
    onCopy: (String) -> Unit,
    onChecks: () -> Unit
) {
    Row(
        Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp)
            .testTag("apk-profile"),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ApkPanel(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .testTag("apk-compatibility")
        ) {
            Text(
                stringResource(Res.string.apk_info_compatibility),
                style = MaterialTheme.typography.titleMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApkCopyField(
                    stringResource(Res.string.apk_info_version_name),
                    result.versionName,
                    onCopy,
                    Modifier.weight(1f)
                )
                ApkCopyField(
                    stringResource(Res.string.apk_info_version_code),
                    result.versionCode,
                    onCopy,
                    Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            ApkCopyField(stringResource(Res.string.apk_info_min_sdk), result.minSdkVersion, onCopy)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApkCopyField(
                    stringResource(Res.string.apk_info_target_sdk),
                    result.targetSdkVersion,
                    onCopy,
                    Modifier.weight(1f)
                )
                ApkCopyField(
                    stringResource(Res.string.apk_info_compile_sdk),
                    result.compileSdkVersion,
                    onCopy,
                    Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            ApkCopyField(
                stringResource(Res.string.apk_info_abi),
                result.nativeCode,
                onCopy,
                technical = true
            )
        }
        ApkPanel(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .testTag("apk-file-information")
        ) {
            Text(
                stringResource(Res.string.apk_info_launch_file),
                style = MaterialTheme.typography.titleMedium
            )
            ApkCopyField(
                stringResource(Res.string.apk_info_launchable_activity),
                result.launchableActivity,
                onCopy,
                technical = true
            )
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApkDetailField(
                    stringResource(Res.string.apk_info_size),
                    result.size.apkBytes(),
                    Modifier.weight(1f)
                )
                ApkDetailField(
                    stringResource(Res.string.apk_info_channel),
                    result.channel.orEmpty(),
                    Modifier.weight(1f)
                )
            }
            ApkCopyField(
                stringResource(Res.string.apk_info_filename),
                inputFile?.substringAfterLast('/')?.substringAfterLast('\\').orEmpty(),
                onCopy,
                technical = true
            )
            OutlinedButton(onChecks, Modifier.fillMaxWidth().testTag("apk-open-checksums")) {
                Icon(Icons.Outlined.Fingerprint, null, Modifier.size(20.dp)); Spacer(
                Modifier.width(
                    8.dp
                )
            )
                Text(stringResource(Res.string.apk_info_path_checksums))
            }
        }
    }
}

@Composable
private fun ApkChecksumsPage(
    result: ApkInformationResultUi,
    inputFile: String?,
    onCopy: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ApkBackHeading(
            stringResource(Res.string.apk_info_path_checksums),
            stringResource(Res.string.apk_info_back),
            onBack
        )
        ApkPanel(Modifier.fillMaxWidth().testTag("apk-checksums")) {
            ApkCopyField(
                stringResource(Res.string.apk_info_file_path),
                inputFile.orEmpty(),
                onCopy,
                technical = true
            )
            HorizontalDivider()
            ApkCopyField(
                stringResource(Res.string.apk_info_md5),
                result.md5,
                onCopy,
                technical = true
            )
            HorizontalDivider()
            ApkCopyField(
                stringResource(Res.string.apk_info_sha256),
                result.sha256,
                onCopy,
                technical = true
            )
            HorizontalDivider()
            ApkDetailField(stringResource(Res.string.apk_info_size), result.size.apkBytes())
        }
    }
}
