package ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.onClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.util.htmlReadyLicenseContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import model.DarkThemeConfig
import model.DestStoreSize
import model.DestStoreType
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.BuildConfig
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.about
import org.tool.kit.composeapp.generated.resources.apk_signature
import org.tool.kit.composeapp.generated.resources.appearance
import org.tool.kit.composeapp.generated.resources.application_author
import org.tool.kit.composeapp.generated.resources.application_copyright
import org.tool.kit.composeapp.generated.resources.application_description
import org.tool.kit.composeapp.generated.resources.application_name
import org.tool.kit.composeapp.generated.resources.application_version
import org.tool.kit.composeapp.generated.resources.author
import org.tool.kit.composeapp.generated.resources.check_for_updates
import org.tool.kit.composeapp.generated.resources.conventional
import org.tool.kit.composeapp.generated.resources.default_output_path
import org.tool.kit.composeapp.generated.resources.delete_repeat_file
import org.tool.kit.composeapp.generated.resources.delete_repeat_file_tips
import org.tool.kit.composeapp.generated.resources.enable_apk_generation_options
import org.tool.kit.composeapp.generated.resources.enable_clear_build_option
import org.tool.kit.composeapp.generated.resources.enable_extended_options
import org.tool.kit.composeapp.generated.resources.enable_file_alignment
import org.tool.kit.composeapp.generated.resources.enable_file_alignment_tips
import org.tool.kit.composeapp.generated.resources.enable_garbage_code_generation_option
import org.tool.kit.composeapp.generated.resources.enable_icon_factory_option
import org.tool.kit.composeapp.generated.resources.enable_signature_generation_option
import org.tool.kit.composeapp.generated.resources.icon
import org.tool.kit.composeapp.generated.resources.license
import org.tool.kit.composeapp.generated.resources.open_source_agreement
import org.tool.kit.composeapp.generated.resources.open_source_licenses
import org.tool.kit.composeapp.generated.resources.signature_generation
import org.tool.kit.composeapp.generated.resources.signature_suffix
import org.tool.kit.composeapp.generated.resources.signature_suffix_tips
import org.tool.kit.composeapp.generated.resources.source_code
import org.tool.kit.composeapp.generated.resources.start_check_update
import org.tool.kit.composeapp.generated.resources.target_key_size
import org.tool.kit.composeapp.generated.resources.target_key_size_tips
import org.tool.kit.composeapp.generated.resources.target_key_type
import org.tool.kit.composeapp.generated.resources.target_key_type_tips
import org.tool.kit.composeapp.generated.resources.toolkit_expand
import org.tool.kit.composeapp.generated.resources.version_information
import org.tool.kit.composeapp.generated.resources.whether_to_always_show_the_navigation_bar_label
import org.tool.kit.composeapp.generated.resources.whether_to_turn_off_file_alignment_function_when_signing_and_packaging_huawei_channel_package
import theme.AppTheme
import vm.MainViewModel
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/21 11:24
 * @Description : 设置页面
 * @Version     : 1.0
 */
@Composable
fun SetUp(viewModel: MainViewModel) {
    val developerMode by viewModel.isEnableDeveloperMode.collectAsState()
    Box(modifier = Modifier.padding(end = 14.dp)) {
        LazyColumn {
            item {
                Spacer(Modifier.size(20.dp))
                Conventional(viewModel)
            }
            item {
                Spacer(Modifier.size(16.dp))
                ApkSignatureSetUp(viewModel)
            }
            item {
                Spacer(Modifier.size(16.dp))
                KeyStore(viewModel)
            }
            item {
                AnimatedVisibility(
                    visible = developerMode,
                    enter = fadeIn() + expandVertically(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.size(16.dp))
                        DeveloperMode(viewModel)
                    }
                }
            }
            item {
                Spacer(Modifier.size(16.dp))
                About(viewModel)
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
    viewModel: MainViewModel
) {
    val userData by viewModel.userData.collectAsState()
    var signerSuffix by mutableStateOf(userData.defaultSignerSuffix)
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
                value = signerSuffix,
                label = stringResource(Res.string.signature_suffix),
                isError = userData.defaultSignerSuffix.isBlank(),
                onValueChange = { suffix ->
                    signerSuffix = suffix
                    viewModel.saveUserData(userData.copy(defaultSignerSuffix = suffix))
                })
            Spacer(Modifier.size(3.dp))
            Text(
                text = stringResource(
                    Res.string.signature_suffix_tips,
                    userData.defaultSignerSuffix
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
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
                    onCheckedChange = { viewModel.saveUserData(userData.copy(duplicateFileRemoval = it)) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
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
                    onCheckedChange = { viewModel.saveUserData(userData.copy(alignFileSize = it)) })
            }
        }
    }
}

/**
 * 签名生成设置页
 */
@Composable
private fun KeyStore(viewModel: MainViewModel) {
    val userData by viewModel.userData.collectAsState()
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
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.6f)) {
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
                Box(modifier = Modifier.weight(1f)) {
                    val options = listOf(DestStoreType.JKS.name, DestStoreType.PKCS12.name)
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.align(Alignment.CenterEnd).width(220.dp)
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                onClick = {
                                    viewModel.saveUserData(userData.copy(destStoreType = if (index == 1) DestStoreType.PKCS12 else DestStoreType.JKS))
                                },
                                selected = if (userData.destStoreType == DestStoreType.PKCS12) index == 1 else index == 0,
                                colors = SegmentedButtonDefaults.colors()
                                    .copy(inactiveContainerColor = Color.Transparent)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1.6f)) {
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
                Box(modifier = Modifier.weight(1f)) {
                    val options = listOf(
                        "${DestStoreSize.ONE_THOUSAND_TWENTY_FOUR.size}",
                        "${DestStoreSize.TWO_THOUSAND_FORTY_EIGHT.size}"
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.align(Alignment.CenterEnd).width(220.dp)
                    ) {
                        options.forEachIndexed { index, label ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                onClick = {
                                    viewModel.saveUserData(userData.copy(destStoreSize = if (index == 1) DestStoreSize.TWO_THOUSAND_FORTY_EIGHT else DestStoreSize.ONE_THOUSAND_TWENTY_FOUR))
                                },
                                selected = if (userData.destStoreSize == DestStoreSize.TWO_THOUSAND_FORTY_EIGHT) index == 1 else index == 0,
                                colors = SegmentedButtonDefaults.colors()
                                    .copy(inactiveContainerColor = Color.Transparent)
                            ) {
                                Text(label, style = MaterialTheme.typography.labelLarge)
                            }
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
    viewModel: MainViewModel
) {
    val userData by viewModel.userData.collectAsState()
    val themeConfig by viewModel.themeConfig.collectAsState()
    var outputPath by mutableStateOf(userData.defaultOutputPath)
    val outPutError =
        userData.defaultOutputPath.isNotBlank() && !File(userData.defaultOutputPath).isDirectory
    val isStartCheckUpdate by viewModel.isStartCheckUpdate.collectAsState()
    val isCheckUpdate by viewModel.checkUpdateState.collectAsState()
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
                value = outputPath,
                label = stringResource(Res.string.default_output_path),
                isError = outPutError,
                onValueChange = { path ->
                    outputPath = path
                    viewModel.apply {
                        saveUserData(userData.copy(defaultOutputPath = path))
                        updateApkSignature(viewModel.apkSignatureState.copy(outputPath = outputPath))
                        updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStorePath = outputPath))
                        updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(outputPath = outputPath))
                        updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(outputPath = outputPath))
                    }
                })
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.appearance),
                    modifier = Modifier.padding(start = 24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)
                ) {
                    val modeList = listOf(
                        DarkThemeConfig.FOLLOW_SYSTEM,
                        DarkThemeConfig.LIGHT,
                        DarkThemeConfig.DARK
                    )
                    modeList.forEach { theme ->
                        ElevatedFilterChip(
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            selected = themeConfig == theme,
                            onClick = { viewModel.saveThemeConfig(theme) },
                            label = {
                                Text(
                                    text = stringResource(theme.resource),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                )
                            },
                            leadingIcon = if (themeConfig == theme) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            })
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp),
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
                            viewModel.checkUpdate()
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
                    viewModel.saveStartCheckUpdate(!isStartCheckUpdate)
                })
        }
    }
}

@Composable
private fun DeveloperMode(viewModel: MainViewModel) {
    val developerMode by viewModel.isEnableDeveloperMode.collectAsState()
    val isHuaweiAlignFileSize by viewModel.isHuaweiAlignFileSize.collectAsState()
    val alwaysShowLabel by viewModel.isAlwaysShowLabel.collectAsState()
    val showApktool by viewModel.isShowApktool.collectAsState()
    val showJunkCode by viewModel.isShowJunkCode.collectAsState()
    val iconFactory by viewModel.isShowIconFactory.collectAsState()
    val clearBuild by viewModel.isShowClearBuild.collectAsState()
    val signatureGeneration by viewModel.isShowSignatureGeneration.collectAsState()
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
                    viewModel.saveDeveloperMode(!developerMode)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_signature_generation_option),
                checked = signatureGeneration,
                onCheckedChange = {
                    viewModel.saveSignatureGeneration(!signatureGeneration)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_apk_generation_options),
                checked = showApktool,
                onCheckedChange = {
                    viewModel.saveApkTool(!showApktool)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_garbage_code_generation_option),
                checked = showJunkCode,
                onCheckedChange = {
                    viewModel.saveJunkCode(!showJunkCode)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_icon_factory_option),
                checked = iconFactory,
                onCheckedChange = {
                    viewModel.saveIconFactory(!iconFactory)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.enable_clear_build_option),
                checked = clearBuild,
                onCheckedChange = {
                    viewModel.saveClearBuild(!clearBuild)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.whether_to_always_show_the_navigation_bar_label),
                checked = alwaysShowLabel,
                onCheckedChange = {
                    viewModel.saveIsAlwaysShowLabel(!alwaysShowLabel)
                })
            ExtensionsSwitch(
                title = stringResource(Res.string.whether_to_turn_off_file_alignment_function_when_signing_and_packaging_huawei_channel_package),
                checked = isHuaweiAlignFileSize,
                onCheckedChange = {
                    viewModel.saveIsHuaweiAlignFileSize(!isHuaweiAlignFileSize)
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun About(viewModel: MainViewModel) {
    var isOpenLibraries by remember { mutableStateOf(false) }
    if (isOpenLibraries) {
        AboutLibrariesWindow(viewModel) {
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
                viewModel.saveDeveloperMode(true)
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
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                thickness = 2.dp
            )
            ClickAbout(text = stringResource(Res.string.source_code)) {
                Desktop.getDesktop().browse(BuildConfig.APP_GITHUB_URI)
            }
            ClickAbout(text = stringResource(Res.string.author)) {
                Desktop.getDesktop().browse(BuildConfig.AUTHOR_GITHUB_URI)
            }
            ClickAbout(text = stringResource(Res.string.license)) {
                Desktop.getDesktop().browse(BuildConfig.APP_LICENSE_URI)
            }
            ClickAbout(text = stringResource(Res.string.open_source_licenses)) {
                isOpenLibraries = !isOpenLibraries
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
            val currentTime = System.currentTimeMillis()
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
                    delay(tapTimeoutMillis)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutLibrariesWindow(viewModel: MainViewModel, onCloseRequest: () -> Unit) {
    val windowState = rememberWindowState(size = DpSize(800.dp, 600.dp))
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "Open Source Licenses",
        icon = painterResource(Res.drawable.icon),
        alwaysOnTop = true
    ) {
        val themeConfig by viewModel.themeConfig.collectAsState()
        val useDarkTheme = when (themeConfig) {
            DarkThemeConfig.LIGHT -> false
            DarkThemeConfig.DARK -> true
            DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        }
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        AppTheme(useDarkTheme) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    var selectLibrary by remember { mutableStateOf<Library?>(null) }
                    LibrariesContainer(
                        libraries = libraries,
                        modifier = Modifier.fillMaxWidth(),
                        showAuthor = false,
                        showDescription = true,
                        onLibraryClick = { library ->
                            val license = library.licenses.firstOrNull()
                            if (!license?.htmlReadyLicenseContent.isNullOrBlank()) {
                                selectLibrary = library
                            } else if (!license?.url.isNullOrBlank()) {
                                license.url?.also {
                                    Desktop.getDesktop().browse(URI.create(it))
                                }
                            }
                        }
                    )
                    selectLibrary?.let { library ->
                        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ModalBottomSheet(
                            modifier = Modifier.fillMaxHeight().align(Alignment.BottomEnd),
                            sheetState = sheetState,
                            onDismissRequest = { selectLibrary = null }
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                item {
                                    Text(
                                        text = library.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                item {
                                    Spacer(Modifier.size(8.dp))
                                    val license = library.licenses.firstOrNull()
                                    license?.licenseContent?.let { content ->
                                        Text(
                                            text = content,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                    Spacer(Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
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
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 4.dp),
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
