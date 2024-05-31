package ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import constant.ConfigConstant
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.SignatureEnum
import model.SignaturePolicy
import toast.ToastModel
import toast.ToastUIState
import utils.isApk
import utils.isKey
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/6 10:43
 * @Description : Apk签名
 * @Version     : 1.0
 */
@Composable
fun ApkSignature(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    SignatureCard(viewModel, toastState, scope)
    SignatureBox(viewModel, scope)
    LoadingAnimate(viewModel.apkSignatureUIState == UIState.Loading, scope)
    toast(viewModel.apkSignatureUIState, toastState, scope)
}

/**
 * 签名主页
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignatureBox(
    viewModel: MainViewModel, scope: CoroutineScope
) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize().onExternalDrag(
            onDragStart = { dragging = true },
            onDragExit = { dragging = false },
            onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    var mApkPath = ""
                    var mSignaturePath = ""
                    dragData.readFiles().forEach {
                        if (it.isApk && mApkPath.isBlank()) {
                            mApkPath = File(URI.create(it)).path
                        } else if (it.isKey && mSignaturePath.isBlank()) {
                            mSignaturePath = File(URI.create(it)).path
                        }
                    }
                    if (mApkPath.isNotBlank()) {
                        viewModel.updateApkSignature(SignatureEnum.APK_PATH, mApkPath)
                    }
                    if (mSignaturePath.isNotBlank()) {
                        viewModel.updateApkSignature(SignatureEnum.KEY_STORE_PATH, mSignaturePath)
                    }
                }
                dragging = false
            })
    )
    UploadAnimate(dragging, scope)
}

@Composable
private fun SignatureCard(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    val apkError =
        viewModel.apkSignatureState.apkPath.isNotBlank() && !File(viewModel.apkSignatureState.apkPath).isFile
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                SignatureApkPath(viewModel, apkError)
            }
            item {
                Spacer(Modifier.size(6.dp))
                FolderInput(
                    value = viewModel.apkSignatureState.outputPath,
                    label = "输出路径",
                    isError = outputError
                ) { path ->
                    viewModel.updateApkSignature(SignatureEnum.OUTPUT_PATH, path)
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                SignaturePolicy(viewModel, toastState, scope)
            }
            item {
                Spacer(Modifier.size(6.dp))
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    FileInput(
                        value = viewModel.apkSignatureState.keyStorePath,
                        label = "密钥文件",
                        isError = signatureError,
                        FileSelectorType.KEY
                    ) { path ->
                        viewModel.updateApkSignature(SignatureEnum.KEY_STORE_PATH, path)
                    }
                }
            }
            item {
                Spacer(Modifier.size(6.dp))
                PasswordInput(
                    value = viewModel.apkSignatureState.keyStorePassword,
                    label = "密钥密码",
                    isError = signaturePasswordError
                ) { password ->
                    viewModel.updateApkSignature(SignatureEnum.KEY_STORE_PASSWORD, password)
                    if (viewModel.apkSignatureState.keyStorePath.isNotBlank() && File(viewModel.apkSignatureState.keyStorePath).isFile) {
                        viewModel.updateApkSignature(
                            SignatureEnum.KEY_STORE_ALISA_LIST, viewModel.verifyAlisa(
                                viewModel.apkSignatureState.keyStorePath,
                                viewModel.apkSignatureState.keyStorePassword
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
                    label = "密钥别名密码",
                    isError = signatureAlisaPasswordError
                ) { password ->
                    viewModel.updateApkSignature(SignatureEnum.KEY_STORE_ALISA_PASSWORD, password)
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
                    signatureAlisaPasswordError,
                    toastState,
                    scope
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
    val options = ConfigConstant.unsignedApkList
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        FileInput(
            value = viewModel.apkSignatureState.apkPath,
            label = "APK文件",
            isError = apkError,
            modifier = Modifier.padding(end = 8.dp, bottom = 3.dp).menuAnchor(),
            trailingIcon = { TrailingIcon(expanded = expanded) },
            FileSelectorType.APK
        ) { path ->
            viewModel.updateApkSignature(SignatureEnum.APK_PATH, path)
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(text = selectionOption.title, style = MaterialTheme.typography.labelLarge) },
                    onClick = {
                        viewModel.updateApkSignature(SignatureEnum.APK_PATH, selectionOption.path)
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
@Composable
private fun SignaturePolicy(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    val policyList =
        listOf(SignaturePolicy.V1, SignaturePolicy.V2, SignaturePolicy.V2Only, SignaturePolicy.V3)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "签名策略：",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp)
            )
            Text(
                viewModel.apkSignatureState.keyStorePolicy.value,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.size(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)
        ) {
            policyList.forEach { signaturePolicy ->
                ElevatedFilterChip(modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    selected = viewModel.apkSignatureState.keyStorePolicy == signaturePolicy,
                    onClick = {
                        if (signaturePolicy == SignaturePolicy.V2Only) {
                            scope.launch {
                                toastState.show(
                                    ToastModel(
                                        "使用 V2 Only 签名的APK包仅支持Android 7及更高版本的系统安装和使用，请注意",
                                        ToastModel.Type.Warning
                                    ), 8000L
                                )
                            }
                        } else {
                            toastState.currentData?.dismiss()
                        }
                        viewModel.updateApkSignature(
                            SignatureEnum.KEY_STORE_POLICY, signaturePolicy
                        )
                    },
                    label = {
                        Text(
                            signaturePolicy.title,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                    },
                    leadingIcon = if (viewModel.apkSignatureState.keyStorePolicy == signaturePolicy) {
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
}

/**
 * 签名别名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureAlisa(viewModel: MainViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val options = viewModel.apkSignatureState.keyStoreAlisaList
    val selectedOptionText = options?.getOrNull(viewModel.apkSignatureState.keyStoreAlisaIndex) ?: ""
    ExposedDropdownMenuBox(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            value = selectedOptionText,
            readOnly = true,
            onValueChange = { },
            label = { Text("密钥别名", style = MaterialTheme.typography.labelLarge) },
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
                    text = { Text(text = selectionOption, style = MaterialTheme.typography.labelLarge) },
                    onClick = {
                        val index = options.indexOf(selectionOption)
                        if (index != viewModel.apkSignatureState.keyStoreAlisaIndex) {
                            viewModel.updateApkSignature(SignatureEnum.KEY_STORE_ALISA_PASSWORD, "")
                        }
                        viewModel.updateApkSignature(SignatureEnum.KEY_STORE_ALISA_INDEX, index)
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
    signatureAlisaPasswordError: Boolean,
    toastState: ToastUIState,
    scope: CoroutineScope
) {
    Button(onClick = {
        if (apkError || outputError || signatureError || signaturePasswordError || signatureAlisaPasswordError) {
            scope.launch {
                toastState.show(ToastModel("请检查Error项", ToastModel.Type.Error))
            }
            return@Button
        }
        signatureApk(viewModel, toastState, scope)
    }) {
        Text(
            "开始签名",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

private fun signatureApk(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    if (viewModel.apkSignatureState.apkPath.isBlank() || viewModel.apkSignatureState.outputPath.isBlank() || viewModel.apkSignatureState.keyStorePath.isBlank() || viewModel.apkSignatureState.keyStorePassword.isBlank() || viewModel.apkSignatureState.keyStoreAlisaList.isNullOrEmpty() || viewModel.apkSignatureState.keyStoreAlisaPassword.isBlank()) {
        scope.launch {
            toastState.show(ToastModel("请检查空项", ToastModel.Type.Error))
        }
        return
    }
    viewModel.apkSigner()
}

