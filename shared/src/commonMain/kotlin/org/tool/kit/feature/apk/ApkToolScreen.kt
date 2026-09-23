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
import org.tool.kit.shared.generated.resources.icon_file
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_password
import org.tool.kit.shared.generated.resources.key_store_file
import org.tool.kit.shared.generated.resources.key_store_password
import org.tool.kit.shared.generated.resources.signing_the_apk_after_it_is_generated
import org.tool.kit.shared.generated.resources.start_generating

/** 根据构建状态展示字段和校验结果，通过回调请求文件选择及提交。 */
@Composable
fun ApkToolScreen(
    state: ApkToolUiState, onIntent: (ApkToolIntent) -> Unit,
    pickOutput: () -> Unit, pickIcon: () -> Unit, pickKey: () -> Unit,
    dragging: Boolean, target: androidx.compose.ui.draganddrop.DragAndDropTarget
) {
    Card(
        modifier = Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = { true }, target = target
        ).padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val outputPathError = state.validation.outputError
        val iconFileError = state.validation.iconError
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(8.dp))
                FolderInput(
                    value = state.form.outputPath,
                    label = stringResource(Res.string.apk_output_path),
                    isError = outputPathError, onPickerRequest = pickOutput,
                    onValueChange = { path ->
                        onIntent(ApkToolIntent.OutputPathChanged(path))
                    })
            }
            item {
                Spacer(Modifier.size(4.dp))
                Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp)) {
                    FileInput(
                        value = state.form.icon,
                        label = stringResource(Res.string.icon_file),
                        isError = iconFileError,
                        modifier = Modifier.padding(end = 8.dp, bottom = 3.dp),
                        trailingIcon = null,
                        onPickerRequest = pickIcon
                    ) { path ->
                        onIntent(ApkToolIntent.IconPathChanged(path))
                    }
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = state.form.packageName,
                    label = stringResource(Res.string.apktool_package_name),
                    isError = state.form.packageName.isBlank(),
                    onValueChange = { packageName ->
                        onIntent(ApkToolIntent.PackageNameChanged(packageName))
                    })
            }
            item {
                Spacer(Modifier.size(4.dp))
                SdkVersionInput(state.form, onIntent)
            }
            item {
                Spacer(Modifier.size(4.dp))
                VersionInput(state.form, onIntent)
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = state.form.appName,
                    label = stringResource(Res.string.apktool_app_name),
                    isError = state.form.appName.isBlank(),
                    onValueChange = { appName ->
                        onIntent(ApkToolIntent.AppNameChanged(appName))
                    })
            }
            item {
                Sign(state, onIntent, pickKey)
            }
            item {
                Spacer(Modifier.size(4.dp))
                Generate { onIntent(ApkToolIntent.Submit) }
                Spacer(Modifier.size(16.dp))
            }
        }
    }
    UploadAnimate(dragging)
}

@Composable
private fun SdkVersionInput(form: ApkToolForm, onIntent: (ApkToolIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = form.targetSdkVersion,
            onValueChange = { targetSdkVersion ->
                onIntent(ApkToolIntent.TargetSdkChanged(targetSdkVersion))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_target_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.targetSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = form.minSdkVersion,
            onValueChange = { minSdkVersion ->
                onIntent(ApkToolIntent.MinSdkChanged(minSdkVersion))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_min_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.minSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun VersionInput(form: ApkToolForm, onIntent: (ApkToolIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = form.versionCode,
            onValueChange = { versionCode ->
                onIntent(ApkToolIntent.VersionCodeChanged(versionCode))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_code),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.versionCode.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = form.versionName,
            onValueChange = { versionName ->
                onIntent(ApkToolIntent.VersionNameChanged(versionName))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.versionName.isBlank(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sign(state: ApkToolUiState, onIntent: (ApkToolIntent) -> Unit, pickKey: () -> Unit) {
    val enableSign = state.form.enableSign
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
                onIntent(ApkToolIntent.EnableSignChanged(it))
            })
        }
        AnimatedVisibility(enableSign) {
            Signature(state, onIntent, pickKey)
        }
    }
}

@Composable
private fun Signature(
    state: ApkToolUiState,
    onIntent: (ApkToolIntent) -> Unit,
    pickKey: () -> Unit
) {
    val signatureError = state.validation.keyError
    val signaturePasswordError = state.storePasswordError
    val signatureAlisaPasswordError = state.aliasPasswordError
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.size(6.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            FileInput(
                value = state.form.credentials.path,
                label = stringResource(Res.string.key_store_file),
                isError = signatureError,
                onPickerRequest = pickKey
            ) { keyStorePath ->
                onIntent(ApkToolIntent.KeyPathChanged(keyStorePath))
            }
        }
        Spacer(Modifier.size(6.dp))
        PasswordInput(
            value = state.form.credentials.storePassword,
            label = stringResource(Res.string.key_store_password),
            isError = signaturePasswordError
        ) { password ->
            onIntent(ApkToolIntent.StorePasswordChanged(password))
        }
        Spacer(Modifier.size(6.dp))
        SignatureAlisa(state.form, onIntent)
        Spacer(Modifier.size(6.dp))
        PasswordInput(
            value = state.form.credentials.aliasPassword,
            label = stringResource(Res.string.key_password),
            isError = signatureAlisaPasswordError
        ) { password ->
            onIntent(ApkToolIntent.AliasPasswordChanged(password))
        }
    }
}

/**
 * 签名别名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureAlisa(form: ApkToolForm, onIntent: (ApkToolIntent) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = form.credentials.aliases
    val selectedOptionText =
        options?.getOrNull(form.credentials.aliasIndex) ?: ""
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
                        onIntent(ApkToolIntent.AliasChanged(index))
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun Generate(onSubmit: () -> Unit) {
    Button(onClick = onSubmit) {
        Text(
            text = stringResource(Res.string.start_generating),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}
