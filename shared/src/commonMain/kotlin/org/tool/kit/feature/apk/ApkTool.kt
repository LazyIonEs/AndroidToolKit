package org.tool.kit.feature.apk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FileInput
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.PasswordInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.feature.ui.UploadAnimate
import org.tool.kit.feature.ui.dragAndDropTarget
import org.tool.kit.model.FileSelectorType
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_output_path
import org.tool.kit.shared.generated.resources.apk_tool_sign_tips
import org.tool.kit.shared.generated.resources.apk_tool_sign_v3
import org.tool.kit.shared.generated.resources.apktool_app_name
import org.tool.kit.shared.generated.resources.apktool_min_sdk_version
import org.tool.kit.shared.generated.resources.apktool_package_name
import org.tool.kit.shared.generated.resources.apktool_target_sdk_version
import org.tool.kit.shared.generated.resources.apktool_version_code
import org.tool.kit.shared.generated.resources.apktool_version_name
import org.tool.kit.shared.generated.resources.check_empty
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.icon_file
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_password
import org.tool.kit.shared.generated.resources.key_store_file
import org.tool.kit.shared.generated.resources.key_store_password
import org.tool.kit.shared.generated.resources.signing_the_apk_after_it_is_generated
import org.tool.kit.shared.generated.resources.start_generating
import org.tool.kit.utils.isImage
import org.tool.kit.utils.isKey
import org.tool.kit.vm.MainViewModel
import java.io.File
import kotlin.io.path.pathString

@Composable
fun ApkTool(viewModel: MainViewModel) {
    ApkToolBox(viewModel)
}

@Composable
private fun ApkToolBox(viewModel: MainViewModel) {
    var dragging by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
                dragging = it
            }, onFinish = { result ->
                result.onSuccess { fileList ->
                    fileList.firstOrNull()?.let {
                        val path = it.toAbsolutePath().pathString
                        if (path.isImage) {
                            viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(icon = path))
                        } else if (path.isKey) {
                            val apkSignature = viewModel.apkToolInfoState.copy()
                            apkSignature.keyStorePath = path
                            viewModel.updateApkToolInfo(apkSignature)
                        }
                    }
                }
            })
        ).padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val outputPathError =
            viewModel.apkToolInfoState.outputPath.isNotBlank() && !File(viewModel.apkToolInfoState.outputPath).isDirectory
        val iconFileError =
            viewModel.apkToolInfoState.icon.isNotBlank() && !File(viewModel.apkToolInfoState.icon).isFile
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(8.dp))
                FolderInput(
                    value = viewModel.apkToolInfoState.outputPath,
                    label = stringResource(Res.string.apk_output_path),
                    isError = outputPathError,
                    onValueChange = { path ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(outputPath = path))
                    })
            }
            item {
                Spacer(Modifier.size(4.dp))
                Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp)) {
                    FileInput(
                        value = viewModel.apkToolInfoState.icon,
                        label = stringResource(Res.string.icon_file),
                        isError = iconFileError,
                        modifier = Modifier.padding(end = 8.dp, bottom = 3.dp),
                        trailingIcon = null,
                        FileSelectorType.IMAGE
                    ) { path ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(icon = path))
                    }
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = viewModel.apkToolInfoState.packageName,
                    label = stringResource(Res.string.apktool_package_name),
                    isError = viewModel.apkToolInfoState.packageName.isBlank(),
                    onValueChange = { packageName ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(packageName = packageName))
                    })
            }
            item {
                Spacer(Modifier.size(4.dp))
                SdkVersionInput(viewModel)
            }
            item {
                Spacer(Modifier.size(4.dp))
                VersionInput(viewModel)
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = viewModel.apkToolInfoState.appName,
                    label = stringResource(Res.string.apktool_app_name),
                    isError = viewModel.apkToolInfoState.appName.isBlank(),
                    onValueChange = { appName ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(appName = appName))
                    })
            }
            item {
                Sign(viewModel)
            }
            item {
                Spacer(Modifier.size(4.dp))
                Generate(
                    viewModel, outputPathError, iconFileError
                )
                Spacer(Modifier.size(16.dp))
            }
        }
    }
    UploadAnimate(dragging)
}

@Composable
private fun SdkVersionInput(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = viewModel.apkToolInfoState.targetSdkVersion,
            onValueChange = { targetSdkVersion ->
                if (targetSdkVersion.isEmpty() || targetSdkVersion.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(targetSdkVersion = targetSdkVersion))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_target_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.targetSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = viewModel.apkToolInfoState.minSdkVersion,
            onValueChange = { minSdkVersion ->
                if (minSdkVersion.isEmpty() || minSdkVersion.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(minSdkVersion = minSdkVersion))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_min_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.minSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun VersionInput(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = viewModel.apkToolInfoState.versionCode,
            onValueChange = { versionCode ->
                if (versionCode.isEmpty() || versionCode.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(versionCode = versionCode))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_code),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.versionCode.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = viewModel.apkToolInfoState.versionName,
            onValueChange = { versionName ->
                viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(versionName = versionName))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.versionName.isBlank(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sign(viewModel: MainViewModel) {
    val enableSign = viewModel.apkToolInfoState.enableSign
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(Res.string.signing_the_apk_after_it_is_generated),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(2.dp))
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                        tooltip = { PlainTooltip { Text(stringResource(Res.string.apk_tool_sign_tips)) } },
                        state = rememberTooltipState(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = "Help",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.apk_tool_sign_v3),
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Switch(checked = enableSign, onCheckedChange = {
                val apkSignature = viewModel.apkToolInfoState.copy(enableSign = it)
                apkSignature.keyStorePath = ""
                viewModel.updateApkToolInfo(apkSignature)
            })
        }
        AnimatedVisibility(enableSign) {
            Signature(viewModel)
        }
    }
}

@Composable
fun Signature(viewModel: MainViewModel) {
    val signatureError =
        viewModel.apkToolInfoState.keyStorePath.isNotBlank() && !File(viewModel.apkToolInfoState.keyStorePath).isFile
    val signaturePasswordError =
        viewModel.apkToolInfoState.keyStorePassword.isNotBlank() && viewModel.apkToolInfoState.keyStoreAlisaList.isNullOrEmpty()
    val signatureAlisaPasswordError =
        !viewModel.apkToolInfoState.keyStoreAlisaList.isNullOrEmpty() && viewModel.apkToolInfoState.keyStoreAlisaPassword.isNotBlank() && !viewModel.verifyAlisaPassword(viewModel.apkToolInfoState)
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(6.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            FileInput(
                value = viewModel.apkToolInfoState.keyStorePath,
                label = stringResource(Res.string.key_store_file),
                isError = signatureError,
                FileSelectorType.KEY
            ) { keyStorePath ->
                val apkSignature = viewModel.apkToolInfoState.copy()
                apkSignature.keyStorePath = keyStorePath
                viewModel.updateApkToolInfo(apkSignature)
            }
        }
        Spacer(Modifier.size(6.dp))
        PasswordInput(
            value = viewModel.apkToolInfoState.keyStorePassword,
            label = stringResource(Res.string.key_store_password),
            isError = signaturePasswordError
        ) { password ->
            viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(keyStorePassword = password))
            if (viewModel.apkToolInfoState.keyStorePath.isNotBlank() && File(viewModel.apkToolInfoState.keyStorePath).isFile) {
                viewModel.updateApkToolInfo(
                    viewModel.apkToolInfoState.copy(
                        keyStoreAlisaList = viewModel.verifyAlisa(
                            viewModel.apkToolInfoState.keyStorePath,
                            viewModel.apkToolInfoState.keyStorePassword
                        )
                    )
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        SignatureAlisa(viewModel)
        Spacer(Modifier.size(6.dp))
        PasswordInput(
            value = viewModel.apkToolInfoState.keyStoreAlisaPassword,
            label = stringResource(Res.string.key_password),
            isError = signatureAlisaPasswordError
        ) { password ->
            viewModel.updateApkToolInfo(
                viewModel.apkToolInfoState.copy(
                    keyStoreAlisaPassword = password
                )
            )
        }
    }
}

/**
 * 签名别名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureAlisa(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val options = viewModel.apkToolInfoState.keyStoreAlisaList
    val selectedOptionText =
        options?.getOrNull(viewModel.apkToolInfoState.keyStoreAlisaIndex) ?: ""
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            value = selectedOptionText,
            readOnly = true,
            onValueChange = { },
            label = {
                Text(
                    stringResource(Res.string.key_alias),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            trailingIcon = { TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options?.forEach { selectionOption ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = selectionOption,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    onClick = {
                        val index = options.indexOf(selectionOption)
                        if (index != viewModel.apkToolInfoState.keyStoreAlisaIndex) {
                            viewModel.updateApkToolInfo(
                                viewModel.apkToolInfoState.copy(
                                    keyStoreAlisaPassword = ""
                                )
                            )
                        }
                        viewModel.updateApkToolInfo(
                            viewModel.apkToolInfoState.copy(
                                keyStoreAlisaIndex = index
                            )
                        )
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun Generate(
    viewModel: MainViewModel, outputPathError: Boolean, iconFileError: Boolean
) {
    Button(onClick = {
        if (outputPathError || iconFileError) {
            viewModel.updateSnackbarVisuals(Res.string.check_error)
            return@Button
        }
        if (viewModel.apkToolInfoState.outputPath.isBlank() || viewModel.apkToolInfoState.packageName.isBlank() || viewModel.apkToolInfoState.targetSdkVersion.isBlank() || viewModel.apkToolInfoState.minSdkVersion.isBlank() || viewModel.apkToolInfoState.versionCode.isEmpty() || viewModel.apkToolInfoState.versionName.isBlank() || viewModel.apkToolInfoState.appName.isBlank()) {
            viewModel.updateSnackbarVisuals(Res.string.check_empty)
            return@Button
        }
        viewModel.generateApktool()
    }) {
        Text(
            text = stringResource(Res.string.start_generating),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}