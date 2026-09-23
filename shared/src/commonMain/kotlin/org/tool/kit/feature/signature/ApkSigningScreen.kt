package org.tool.kit.feature.signature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FileInput
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.PasswordInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.feature.ui.UploadAnimate
import org.tool.kit.model.SignaturePolicy
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_file
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_password
import org.tool.kit.shared.generated.resources.key_store_file
import org.tool.kit.shared.generated.resources.key_store_password
import org.tool.kit.shared.generated.resources.output_file_prefix
import org.tool.kit.shared.generated.resources.output_path
import org.tool.kit.shared.generated.resources.signature_strategy
import org.tool.kit.shared.generated.resources.start_signing
import org.tool.kit.shared.generated.resources.v4_signature_output_file_name

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/6 10:43
 * @Description : 展示 APK 签名表单、预设和校验结果，文件选择与签名交由回调处理
 * @Version     : 1.0
 */
@Composable
fun ApkSigningScreen(state: ApkSigningUiState, presets: List<SigningPreset>, onIntent: (ApkSigningIntent) -> Unit,
    pickApk: () -> Unit, pickOutput: () -> Unit, pickKey: () -> Unit,
    dragging: Boolean, target: androidx.compose.ui.draganddrop.DragAndDropTarget) {
    SignatureCard(state, presets, onIntent, pickApk, pickOutput, pickKey)
    SignatureBox(dragging, target)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignatureBox(dragging: Boolean, target: androidx.compose.ui.draganddrop.DragAndDropTarget) {
    Box(modifier = Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target))
    UploadAnimate(dragging)
}

@Composable
private fun SignatureCard(state: ApkSigningUiState, presets: List<SigningPreset>, onIntent: (ApkSigningIntent) -> Unit,
    pickApk: () -> Unit, pickOutput: () -> Unit, pickKey: () -> Unit) {
    val apkError = state.validation.apkError
    val outputError = state.validation.outputError
    val signatureError = state.validation.keyError
    val signaturePasswordError = state.storePasswordError
    val signatureAlisaPasswordError = state.aliasPasswordError
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                SignatureApkPath(state.form.apkPath, presets, apkError, pickApk) { onIntent(ApkSigningIntent.ApkPathChanged(it)) }
            }
            item {
                Spacer(Modifier.size(6.dp))
                FolderInput(
                    value = state.form.outputPath,
                    label = stringResource(Res.string.output_path),
                    isError = outputError, onPickerRequest = pickOutput
                ) { outputPath ->
                    onIntent(ApkSigningIntent.OutputPathChanged(outputPath))
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                StringInput(
                    value = state.form.outputPrefix,
                    label = stringResource(Res.string.output_file_prefix),
                    isError = false
                ) { outputPrefix ->
                    onIntent(ApkSigningIntent.PrefixChanged(outputPrefix))
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                SignaturePolicy(state.form.policy, state.form.v4FileName) { onIntent(ApkSigningIntent.PolicyChanged(it)) }
            }
            item {
                Spacer(Modifier.size(6.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FileInput(
                        value = state.form.credentials.path,
                        label = stringResource(Res.string.key_store_file),
                        isError = signatureError,
                        onPickerRequest = pickKey
                    ) { keyStorePath ->
                        onIntent(ApkSigningIntent.KeyPathChanged(keyStorePath))
                    }
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                PasswordInput(
                    value = state.form.credentials.storePassword,
                    label = stringResource(Res.string.key_store_password),
                    isError = signaturePasswordError
                ) { password ->
                    onIntent(ApkSigningIntent.StorePasswordChanged(password))
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                SignatureAlisa(state.form.credentials) { onIntent(ApkSigningIntent.AliasChanged(it)) }
            }
            item {
                Spacer(Modifier.size(6.dp))
                PasswordInput(
                    value = state.form.credentials.aliasPassword,
                    label = stringResource(Res.string.key_password),
                    isError = signatureAlisaPasswordError
                ) { password ->
                    onIntent(ApkSigningIntent.AliasPasswordChanged(password))
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Signature { onIntent(ApkSigningIntent.Submit) }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

/**
 * 签名路径
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureApkPath(apkPath: String, options: List<SigningPreset>, apkError: Boolean, pickApk: () -> Unit, onPathChanged: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it }) {
        val apk = options.find { it.path == apkPath }
        val value = if (apk != null) {
            "${apk.title}.apk"
        } else {
            apkPath
        }
        FileInput(
            value = value,
            label = stringResource(Res.string.apk_file),
            isError = apkError,
            modifier = Modifier.padding(end = 8.dp, bottom = 3.dp).menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryEditable
            ),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            onPickerRequest = pickApk
        ) { path ->
            onPathChanged(path)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = selectionOption.title,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    onClick = {
                        onPathChanged(selectionOption.path)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * 签名策略
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SignaturePolicy(
    selectedPolicy: SignaturePolicy, v4FileName: String, onPolicyChanged: (SignaturePolicy) -> Unit
) {
    val policyList = SignaturePolicy.entries
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(Res.string.signature_strategy),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp)
            )
            Text(
                text = stringResource(selectedPolicy.value),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.size(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 68.dp),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            policyList.forEachIndexed { index, policy ->
                ToggleButton(
                    checked = policy == selectedPolicy,
                    onCheckedChange = {
                        onPolicyChanged(policy)
                    },
                    colors = ToggleButtonDefaults.elevatedToggleButtonColors(),
                    modifier = Modifier.weight(1f),
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            policyList.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    AnimatedVisibility(policy == selectedPolicy) {
                        Row {
                            Icon(
                                imageVector = Icons.Rounded.Done,
                                contentDescription = "Done icon",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                        }
                    }
                    Text(text = policy.title)
                }
            }
        }
        AnimatedVisibility(selectedPolicy == SignaturePolicy.V4) {
            Column {
                Spacer(Modifier.size(6.dp))
                StringInput(
                    value = v4FileName,
                    label = stringResource(Res.string.v4_signature_output_file_name),
                    isError = false,
                    realOnly = true
                ) { }
            }
        }
    }
}

/**
 * 签名别名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureAlisa(credentials: SigningCredentialsUi, onAliasChanged: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = credentials.aliases
    val selectedOptionText =
        options?.getOrNull(credentials.aliasIndex) ?: ""
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                        onAliasChanged(index)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/**
 * 开始签名按钮
 */
@Composable
private fun Signature(onSubmit: () -> Unit) {
    Button(onClick = onSubmit) {
        Text(
            text = stringResource(Res.string.start_signing),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}
