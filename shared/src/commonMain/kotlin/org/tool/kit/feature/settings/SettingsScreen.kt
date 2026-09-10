package org.tool.kit.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.onClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.BuildConfig
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.DestStoreSize
import org.tool.kit.model.DestStoreType
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.about
import org.tool.kit.shared.generated.resources.apk_signature
import org.tool.kit.shared.generated.resources.appearance
import org.tool.kit.shared.generated.resources.application_author
import org.tool.kit.shared.generated.resources.application_copyright
import org.tool.kit.shared.generated.resources.application_description
import org.tool.kit.shared.generated.resources.application_name
import org.tool.kit.shared.generated.resources.application_version
import org.tool.kit.shared.generated.resources.author
import org.tool.kit.shared.generated.resources.check_for_updates
import org.tool.kit.shared.generated.resources.conventional
import org.tool.kit.shared.generated.resources.default_output_path
import org.tool.kit.shared.generated.resources.delete_repeat_file
import org.tool.kit.shared.generated.resources.delete_repeat_file_tips
import org.tool.kit.shared.generated.resources.enable_extended_options
import org.tool.kit.shared.generated.resources.enable_file_alignment
import org.tool.kit.shared.generated.resources.enable_file_alignment_tips
import org.tool.kit.shared.generated.resources.enable_garbage_code_generation_option
import org.tool.kit.shared.generated.resources.icon
import org.tool.kit.shared.generated.resources.license
import org.tool.kit.shared.generated.resources.open_source_agreement
import org.tool.kit.shared.generated.resources.open_source_licenses
import org.tool.kit.shared.generated.resources.signature_generation
import org.tool.kit.shared.generated.resources.signature_suffix
import org.tool.kit.shared.generated.resources.signature_suffix_tips
import org.tool.kit.shared.generated.resources.source_code
import org.tool.kit.shared.generated.resources.start_check_update
import org.tool.kit.shared.generated.resources.target_key_size
import org.tool.kit.shared.generated.resources.target_key_size_tips
import org.tool.kit.shared.generated.resources.target_key_type
import org.tool.kit.shared.generated.resources.target_key_type_tips
import org.tool.kit.shared.generated.resources.toolkit_expand
import org.tool.kit.shared.generated.resources.version_information
import org.tool.kit.shared.generated.resources.view_log_file
import org.tool.kit.shared.generated.resources.whether_to_always_show_the_navigation_bar_label
import org.tool.kit.shared.generated.resources.whether_to_turn_off_file_alignment_function_when_signing_and_packaging_huawei_channel_package
import kotlin.time.Duration.Companion.milliseconds

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/21 11:24
 * @Description : 设置页面
 * @Version     : 1.0
 */
@Composable
fun SettingsScreen(state: SettingsUiState, isCheckUpdate: Boolean, onIntent: (SettingsIntent) -> Unit,
    onCheckUpdate: () -> Unit, onPickOutput: () -> Unit, onBrowse: (String) -> Unit, onOpenLog: () -> Unit,
    librariesWindow: @Composable (() -> Unit) -> Unit) {
    val developerMode = state.preferences.isEnableDeveloperMode
    Box(modifier = Modifier.padding(end = 14.dp)) {
        LazyColumn {
            item {
                Spacer(Modifier.size(20.dp))
                Conventional(state, isCheckUpdate, onIntent, onCheckUpdate, onPickOutput)
            }
            item {
                Spacer(Modifier.size(16.dp))
                ApkSignatureSetUp(state, onIntent)
            }
            item {
                Spacer(Modifier.size(16.dp))
                KeyStore(state, onIntent)
            }
            item {
                AnimatedVisibility(
                    visible = developerMode,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.size(16.dp))
                        DeveloperMode(state, onIntent)
                    }
                }
            }
            item {
                Spacer(Modifier.size(16.dp))
                About(state, onIntent, onBrowse, onOpenLog, librariesWindow)
                Spacer(Modifier.size(20.dp))
            }
        }
    }
}

/**
 * APK签名设置页
 */
@Composable
private fun ApkSignatureSetUp(
    state: SettingsUiState, onIntent: (SettingsIntent) -> Unit
) {
    val userData = state.preferences.userData

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.apk_signature),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(20.dp))
            StringInput(
                value = state.preferences.userData.defaultSignerSuffix,
                label = stringResource(Res.string.signature_suffix),
                isError = state.preferences.userData.defaultSignerSuffix.isBlank(),
                onValueChange = { onIntent(SettingsIntent.SignerSuffix(it)) })
            Spacer(Modifier.size(3.dp))
            Text(
                text = stringResource(
                    Res.string.signature_suffix_tips,
                    state.preferences.userData.defaultSignerSuffix
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.delete_repeat_file),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AnimatedVisibility(!userData.duplicateFileRemoval) {
                        Text(
                            text = stringResource(Res.string.delete_repeat_file_tips),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(
                    checked = userData.duplicateFileRemoval,
                    onCheckedChange = { onIntent(SettingsIntent.DuplicateRemoval(it)) })
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.enable_file_alignment),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AnimatedVisibility(!userData.alignFileSize) {
                        Text(
                            text = stringResource(Res.string.enable_file_alignment_tips),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(
                    checked = userData.alignFileSize,
                    onCheckedChange = { onIntent(SettingsIntent.AlignFileSize(it)) })
            }
        }
    }
}

/**
 * 签名生成设置页
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeyStore(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    val userData = state.preferences.userData
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.signature_generation),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.target_key_type),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AnimatedVisibility(userData.destStoreType == DestStoreType.JKS) {
                        Text(
                            text = stringResource(Res.string.target_key_type_tips),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        ButtonGroupDefaults.ConnectedSpaceBetween,
                        Alignment.End
                    )
                ) {
                    val options = DestStoreType.entries
                    options.forEachIndexed { index, destStoreType ->
                        ToggleButton(
                            checked = destStoreType == userData.destStoreType,
                            onCheckedChange = {
                                onIntent(SettingsIntent.StoreType(destStoreType))
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                            colors = ToggleButtonDefaults.elevatedToggleButtonColors(),
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            AnimatedVisibility(destStoreType == userData.destStoreType) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                                }
                            }
                            Text(text = destStoreType.name)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.target_key_size),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    AnimatedVisibility(userData.destStoreSize == DestStoreSize.ONE_THOUSAND_TWENTY_FOUR) {
                        Text(
                            text = stringResource(Res.string.target_key_size_tips),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        ButtonGroupDefaults.ConnectedSpaceBetween,
                        Alignment.End
                    )
                ) {
                    val options = DestStoreSize.entries
                    options.forEachIndexed { index, destStoreSize ->
                        ToggleButton(
                            checked = destStoreSize == userData.destStoreSize,
                            onCheckedChange = {
                                onIntent(SettingsIntent.StoreSize(destStoreSize))
                            },
                            modifier = Modifier.defaultMinSize(minWidth = 120.dp),
                            colors = ToggleButtonDefaults.elevatedToggleButtonColors(),
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            AnimatedVisibility(destStoreSize == userData.destStoreSize) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                                }
                            }
                            Text(text = destStoreSize.size.toString())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Conventional(
    state: SettingsUiState, isCheckUpdate: Boolean, onIntent: (SettingsIntent) -> Unit, onCheckUpdate: () -> Unit, onPickOutput: () -> Unit
) {
    val themeConfig = DarkThemeConfig.valueOf(state.preferences.themeConfig.name)


    val outPutError = state.outputPathError
    val isStartCheckUpdate = state.preferences.isStartCheckUpdate

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.conventional),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(12.dp))
            FolderInput(
                value = state.preferences.userData.defaultOutputPath,
                label = stringResource(Res.string.default_output_path),
                isError = outPutError,
                onPickerRequest = onPickOutput,
                onValueChange = { onIntent(SettingsIntent.OutputPath(it)) })
            Spacer(Modifier.size(18.dp))
            Column {
                Text(
                    text = stringResource(Res.string.appearance),
                    modifier = Modifier.padding(start = 24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.size(4.dp))
                val modeList = DarkThemeConfig.entries
                Row(
                    modifier = Modifier.padding(start = 24.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    modeList.forEachIndexed { index, theme ->
                        ToggleButton(
                            checked = themeConfig == theme,
                            onCheckedChange = {
                                onIntent(SettingsIntent.Theme(theme))
                            },
                            modifier = Modifier.weight(1f),
                            colors = ToggleButtonDefaults.elevatedToggleButtonColors(),
                            shapes =
                                when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    modeList.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                },
                        ) {
                            AnimatedVisibility(themeConfig == theme) {
                                Row {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                                }
                            }
                            Text(text = stringResource(theme.resource))
                        }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.version_information),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = BuildConfig.APP_VERSION,
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AnimatedContent(targetState = isCheckUpdate) { checkUpdate ->
                    if (checkUpdate) {
                        ContainedLoadingIndicator()
                    } else {
                        Button(onClick = {
                            onCheckUpdate()
                        }) {
                            Text(text = stringResource(Res.string.check_for_updates))
                        }
                    }
                }
            }
            ExtensionsSwitch(
                title = stringResource(Res.string.start_check_update),
                checked = isStartCheckUpdate,
                onCheckedChange = {
                    onIntent(SettingsIntent.StartCheckUpdate(!isStartCheckUpdate))
                })
        }
    }
}

@Composable
private fun DeveloperMode(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
    val developerMode = state.preferences.isEnableDeveloperMode
    val isHuaweiAlignFileSize = state.preferences.isHuaweiAlignFileSize
    val alwaysShowLabel = state.preferences.isAlwaysShowLabel
    val showJunkCode = state.preferences.isShowJunkCode
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.toolkit_expand),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_extended_options),
                checked = developerMode,
                onCheckedChange = {
                    onIntent(SettingsIntent.DeveloperMode(!developerMode))
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_garbage_code_generation_option),
                checked = showJunkCode,
                onCheckedChange = {
                    onIntent(SettingsIntent.ShowJunkCode(!showJunkCode))
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.whether_to_always_show_the_navigation_bar_label),
                checked = alwaysShowLabel,
                onCheckedChange = {
                    onIntent(SettingsIntent.AlwaysShowLabel(!alwaysShowLabel))
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.whether_to_turn_off_file_alignment_function_when_signing_and_packaging_huawei_channel_package),
                checked = isHuaweiAlignFileSize,
                onCheckedChange = {
                    onIntent(SettingsIntent.HuaweiAlignment(!isHuaweiAlignFileSize))
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun About(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit, onBrowse: (String) -> Unit, onOpenLog: () -> Unit, librariesWindow: @Composable (() -> Unit) -> Unit) {
    var isOpenLibraries by remember { mutableStateOf(false) }
    if (isOpenLibraries) {
        librariesWindow {
            isOpenLibraries = false
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.about),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(8.dp))
            TextAbout(
                title = stringResource(Res.string.application_name),
                value = BuildConfig.APP_NAME
            )
            VersionInfo {
                onIntent(SettingsIntent.DeveloperMode(true))
            }
            TextAbout(
                title = stringResource(Res.string.application_description),
                value = BuildConfig.APP_DESCRIPTION
            )
            TextAbout(
                title = stringResource(Res.string.application_copyright),
                value = BuildConfig.APP_COPYRIGHT
            )
            TextAbout(
                title = stringResource(Res.string.application_author),
                value = BuildConfig.APP_VENDOR
            )
            TextAbout(
                title = stringResource(Res.string.open_source_agreement),
                value = BuildConfig.APP_LICENSE
            )
            HorizontalDivider(
                modifier = Modifier.padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
                thickness = 2.dp
            )
            ClickAbout(text = stringResource(Res.string.source_code)) {
                onBrowse(BuildConfig.APP_GITHUB_URI.toString())
            }
            ClickAbout(text = stringResource(Res.string.author)) {
                onBrowse(BuildConfig.AUTHOR_GITHUB_URI.toString())
            }
            ClickAbout(text = stringResource(Res.string.license)) {
                onBrowse(BuildConfig.APP_LICENSE_URI.toString())
            }
            ClickAbout(text = stringResource(Res.string.open_source_licenses)) {
                isOpenLibraries = !isOpenLibraries
            }
            if (state.logFilePath != null) {
                ClickAbout(text = stringResource(Res.string.view_log_file)) {
                    onOpenLog()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VersionInfo(
    tapThreshold: Int = 2, tapTimeoutMillis: Long = 1000, onActivateDeveloperMode: () -> Unit
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).onClick {
            val currentTime = kotlin.time.Clock.System.now().toEpochMilliseconds()
            if (currentTime - lastTapTime > tapTimeoutMillis) {
                tapCount = 0
            }
            lastTapTime = currentTime
            tapCount++
            if (tapCount >= tapThreshold) {
                onActivateDeveloperMode()
                tapCount = 0
            } else {
                coroutineScope.launch {
                    delay(tapTimeoutMillis.milliseconds)
                    tapCount = 0
                }
            }
        }, horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(Res.string.application_version),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        Text(
            text = BuildConfig.APP_VERSION,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun TextAbout(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
    }
}

@Composable
private fun ClickAbout(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 1.dp, bottom = 1.dp).height(36.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "ChevronRight",
            )
        }
    }
}

@Composable
private fun ExtensionsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked, onCheckedChange = onCheckedChange
        )
    }
}
