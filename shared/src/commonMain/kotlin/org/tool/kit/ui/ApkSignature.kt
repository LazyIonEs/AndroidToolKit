package org.tool.kit.ui

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
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
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
import org.tool.kit.constant.ConfigConstant
import org.tool.kit.model.FileSelectorType
import org.tool.kit.model.SignaturePolicy
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_file
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_password
import org.tool.kit.shared.generated.resources.key_store_file
import org.tool.kit.shared.generated.resources.key_store_password
import org.tool.kit.shared.generated.resources.output_file_prefix
import org.tool.kit.shared.generated.resources.output_path
import org.tool.kit.shared.generated.resources.signature_strategy
import org.tool.kit.shared.generated.resources.start_signing
import org.tool.kit.shared.generated.resources.v2_tips
import org.tool.kit.shared.generated.resources.v4_signature_output_file_name
import org.tool.kit.utils.isApk
import org.tool.kit.utils.isKey
import org.tool.kit.vm.MainViewModel
import java.io.File
import kotlin.io.path.pathString

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/6 10:43
 * @Description : Apk签名
 * @Version     : 1.0
 */
@Composable
fun ApkSignature(viewModel: MainViewModel) {
    SignatureCard(viewModel)
    SignatureBox(viewModel)
}

/**
 * 签名主页
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignatureBox(
    viewModel: MainViewModel
) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = accept@{ true },
                target = dragAndDropTarget(dragging = {
                    dragging = it
                }, onFinish = { result ->
                    result.onSuccess { fileList ->
                        var mApkPath = ""
                        var mSignaturePath = ""
                        fileList.forEach {
                            val path = it.toAbsolutePath().pathString
                            if (path.isApk && mApkPath.isBlank()) {
                                mApkPath = path
                            } else if (path.isKey && mSignaturePath.isBlank()) {
                                mSignaturePath = path
                            }
                        }
                        if (mApkPath.isNotBlank()) {
                            val apkSignatureState = viewModel.apkSignatureState.copy()
                            apkSignatureState.apkPath = mApkPath
                            viewModel.updateApkSignature(apkSignatureState)
                        }
                        if (mSignaturePath.isNotBlank()) {
                            val apkSignature = viewModel.apkSignatureState.copy()
                            apkSignature.keyStorePath = mSignaturePath
                            viewModel.updateApkSignature(apkSignature)
                        }
                    }
                })
            )
    )
    UploadAnimate(dragging)
}

@Composable
private fun SignatureCard(viewModel: MainViewModel) {
    val isApkError =
        viewModel.apkSignatureState.apkPath.isNotBlank() && !File(viewModel.apkSignatureState.apkPath).isFile
    val apkError = isApkError && viewModel.apkSignatureState.apkPath != ConfigConstant.APK.All.path
    val outputError =
        viewModel.apkSignatureState.outputPath.isNotBlank() && !File(viewModel.apkSignatureState.outputPath).isDirectory
    val signatureError =
        viewModel.apkSignatureState.keyStorePath.isNotBlank() && !File(viewModel.apkSignatureState.keyStorePath).isFile
    val signaturePasswordError =
        viewModel.apkSignatureState.keyStorePassword.isNotBlank() && viewModel.apkSignatureState.keyStoreAlisaList.isNullOrEmpty()
    val signatureAlisaPasswordError =
        !viewModel.apkSignatureState.keyStoreAlisaList.isNullOrEmpty() && viewModel.apkSignatureState.keyStoreAlisaPassword.isNotBlank() && !viewModel.verifyAlisaPassword()
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                SignatureApkPath(viewModel, apkError)
            }
            item {
                Spacer(Modifier.size(6.dp))
                FolderInput(
                    value = viewModel.apkSignatureState.outputPath,
                    label = stringResource(Res.string.output_path),
                    isError = outputError
                ) { outputPath ->
                    viewModel.updateApkSignature(viewModel.apkSignatureState.copy(outputPath = outputPath))
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                StringInput(
                    value = viewModel.apkSignatureState.outputPrefix,
                    label = stringResource(Res.string.output_file_prefix),
                    isError = false
                ) { outputPrefix ->
                    val apkSignatureState = viewModel.apkSignatureState.copy()
                    apkSignatureState.outputPrefix = outputPrefix
                    viewModel.updateApkSignature(apkSignatureState)
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                SignaturePolicy(viewModel)
            }
            item {
                Spacer(Modifier.size(6.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FileInput(
                        value = viewModel.apkSignatureState.keyStorePath,
                        label = stringResource(Res.string.key_store_file),
                        isError = signatureError,
                        FileSelectorType.KEY
                    ) { keyStorePath ->
                        val apkSignature = viewModel.apkSignatureState.copy()
                        apkSignature.keyStorePath = keyStorePath
                        viewModel.updateApkSignature(apkSignature)
                    }
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                PasswordInput(
                    value = viewModel.apkSignatureState.keyStorePassword,
                    label = stringResource(Res.string.key_store_password),
                    isError = signaturePasswordError
                ) { password ->
                    viewModel.updateApkSignature(viewModel.apkSignatureState.copy(keyStorePassword = password))
                    if (viewModel.apkSignatureState.keyStorePath.isNotBlank() && File(viewModel.apkSignatureState.keyStorePath).isFile) {
                        viewModel.updateApkSignature(
                            viewModel.apkSignatureState.copy(
                                keyStoreAlisaList = viewModel.verifyAlisa(
                                    viewModel.apkSignatureState.keyStorePath,
                                    viewModel.apkSignatureState.keyStorePassword
                                )
                            )
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                SignatureAlisa(viewModel)
            }
            item {
                Spacer(Modifier.size(6.dp))
                PasswordInput(
                    value = viewModel.apkSignatureState.keyStoreAlisaPassword,
                    label = stringResource(Res.string.key_password),
                    isError = signatureAlisaPasswordError
                ) { password ->
                    viewModel.updateApkSignature(
                        viewModel.apkSignatureState.copy(
                            keyStoreAlisaPassword = password
                        )
                    )
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Signature(
                    viewModel,
                    apkError,
                    outputError,
                    signatureError,
                    signaturePasswordError,
                    signatureAlisaPasswordError
                )
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
private fun SignatureApkPath(viewModel: MainViewModel, apkError: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    val options = ConfigConstant.APK.entries
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it }) {
        val apk = options.find { it.path == viewModel.apkSignatureState.apkPath }
        val value = if (apk != null) {
            "${apk.title}.apk"
        } else {
            viewModel.apkSignatureState.apkPath
        }
        FileInput(
            value = value,
            label = stringResource(Res.string.apk_file),
            isError = apkError,
            modifier = Modifier.padding(end = 8.dp, bottom = 3.dp).menuAnchor(
                ExposedDropdownMenuAnchorType.PrimaryEditable
            ),
            trailingIcon = { TrailingIcon(expanded = expanded) },
            FileSelectorType.APK
        ) { path ->
            val apkSignatureState = viewModel.apkSignatureState.copy()
            apkSignatureState.apkPath = path
            viewModel.updateApkSignature(apkSignatureState)
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
                        val apkSignatureState = viewModel.apkSignatureState.copy()
                        apkSignatureState.apkPath = selectionOption.path
                        viewModel.updateApkSignature(apkSignatureState)
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
    viewModel: MainViewModel
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
                text = stringResource(viewModel.apkSignatureState.keyStorePolicy.value),
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
                    checked = policy == viewModel.apkSignatureState.keyStorePolicy,
                    onCheckedChange = {
                        if (policy == SignaturePolicy.V2Only) {
                            viewModel.updateSnackbarVisuals(Res.string.v2_tips)
                        }
                        viewModel.updateApkSignature(viewModel.apkSignatureState.copy(keyStorePolicy = policy))
                    },
                    modifier = Modifier.weight(1f),
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            policyList.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                ) {
                    AnimatedVisibility(policy == viewModel.apkSignatureState.keyStorePolicy) {
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
        AnimatedVisibility(viewModel.apkSignatureState.keyStorePolicy == SignaturePolicy.V4) {
            Column {
                Spacer(Modifier.size(6.dp))
                StringInput(
                    value = viewModel.apkSignatureState.v4SignatureOutputFileName,
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
private fun SignatureAlisa(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val options = viewModel.apkSignatureState.keyStoreAlisaList
    val selectedOptionText =
        options?.getOrNull(viewModel.apkSignatureState.keyStoreAlisaIndex) ?: ""
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
                        if (index != viewModel.apkSignatureState.keyStoreAlisaIndex) {
                            viewModel.updateApkSignature(
                                viewModel.apkSignatureState.copy(
                                    keyStoreAlisaPassword = ""
                                )
                            )
                        }
                        viewModel.updateApkSignature(
                            viewModel.apkSignatureState.copy(
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

/**
 * 开始签名按钮
 */
@Composable
private fun Signature(
    viewModel: MainViewModel,
    apkError: Boolean,
    outputError: Boolean,
    signatureError: Boolean,
    signaturePasswordError: Boolean,
    signatureAlisaPasswordError: Boolean
) {
    Button(onClick = {
        if (apkError || outputError || signatureError || signaturePasswordError || signatureAlisaPasswordError) {
            viewModel.updateSnackbarVisuals(Res.string.check_error)
            return@Button
        }
        signatureApk(viewModel)
    }) {
        Text(
            text = stringResource(Res.string.start_signing),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

private fun signatureApk(
    viewModel: MainViewModel
) {
    if (viewModel.apkSignatureState.apkPath.isBlank() || viewModel.apkSignatureState.outputPath.isBlank() || viewModel.apkSignatureState.keyStorePath.isBlank() || viewModel.apkSignatureState.keyStorePassword.isBlank() || viewModel.apkSignatureState.keyStoreAlisaList.isNullOrEmpty() || viewModel.apkSignatureState.keyStoreAlisaPassword.isBlank()) {
        viewModel.updateSnackbarVisuals(Res.string.check_error)
        return
    }
    viewModel.apkSigner()
}

