package signature

import LottieAnimation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueryBuilder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showFileSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Verifier
import model.VerifierResult
import toast.ToastModel
import toast.ToastUIState
import utils.copy
import utils.isWindows
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/5 19:47
 * @Description : 描述
 * @Version     : 1.0
 */
@Composable
fun SignatureInformation(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    val signaturePath = remember { mutableStateOf("") }
    when (val uiState = viewModel.verifierState) {
        UIState.WAIT, is UIState.Error -> {
            if (uiState is UIState.Error) {
                scope.launch {
                    toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
                }
            }
            SignatureMain(modifier, scope)
        }

        UIState.Loading -> SignatureLoading(modifier, scope)
        is UIState.Success -> SignatureList(modifier, uiState, toastState, scope)
    }
    SignatureBox(modifier, viewModel, signaturePath)
    SignatureDialog(modifier, viewModel, signaturePath, toastState, scope)
}

/**
 * 主页动画
 */
@Composable
private fun SignatureMain(modifier: Modifier = Modifier, scope: CoroutineScope) {
    Box(
        modifier = modifier.padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(scope, "files/lottie_main_2.json", modifier)
    }
}

/**
 * 签名主页，包含拖拽文件逻辑
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignatureBox(modifier: Modifier = Modifier, viewModel: MainViewModel, signaturePath: MutableState<String>) {
    var isDragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.padding(6.dp).onExternalDrag(
            onDragStart = { isDragging = true },
            onDragExit = { isDragging = false },
            onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    dragData.readFiles().first().let {
                        if (it.endsWith(".apk")) {
                            val path = File(URI.create(it)).path
                            viewModel.apkVerifier(path)
                        } else if (it.endsWith(".jks") || it.endsWith(".keystore")) {
                            val path = File(URI.create(it)).path
                            signaturePath.value = path
                        } else { }
                    }
                }
                isDragging = false
            }),
        contentAlignment = Alignment.TopCenter
    ) {
        SignatureFloatingButton(modifier, viewModel, isDragging, signaturePath)
    }
}

/**
 * Loading动画
 */
@Composable
private fun SignatureLoading(modifier: Modifier = Modifier, scope: CoroutineScope) {
    Box(
        modifier = modifier.padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(scope, "files/lottie_loading.json", modifier)
    }
}

/**
 * 签名信息列表
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SignatureList(modifier: Modifier = Modifier, uiState: UIState, toastState: ToastUIState, scope: CoroutineScope) {
    if (uiState is UIState.Success) {
        LazyColumn(
            modifier = modifier.fillMaxHeight().fillMaxWidth().padding(top = 6.dp, bottom = 6.dp, end = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items((uiState.result as VerifierResult).data) { verifier ->
                Column(modifier.animateItemPlacement().padding(vertical = 8.dp)) {
                    Card(
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                        modifier = modifier.padding(vertical = 4.dp).fillMaxWidth()
                    ) {
                        if (uiState.result.isApk) {
                            Text("Valid APK signature V${verifier.version} found", modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("Valid Signature V${verifier.version} found", modifier = modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    SignatureListTop(modifier, verifier, scope, toastState)
                    SignatureListCenter(modifier, verifier, scope, toastState)
                    SignatureListBottom(modifier, verifier, scope, toastState)
                }
            }
        }
    }
}

/**
 * 选择文件按钮
 */
@Composable
private fun SignatureFloatingButton(modifier: Modifier = Modifier, viewModel: MainViewModel, isDragging: Boolean, signaturePath: MutableState<String>) {
    var showFilePickerApk by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Box(
            modifier = modifier.align(Alignment.BottomEnd).padding(end = 8.dp)
        ) {
            AnimatedVisibility(
                visible = viewModel.verifierState != UIState.Loading
            ) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (isWindows) {
                            showFilePickerApk = true
                        } else {
                            showFileSelector(isAll = true) { path ->
                                if (path.endsWith(".apk")) {
                                    viewModel.apkVerifier(path)
                                } else if (path.endsWith(".jks") || path.endsWith(".keystore")) {
                                    signaturePath.value = path
                                }
                            }
                        }
                    },
                    icon = { Icon(Icons.Rounded.DriveFolderUpload, "准备选择文件") },
                    text = {
                        Text(if (isDragging) {
                            "愣着干嘛，还不松手"
                        } else {
                            "点击选择或拖拽上传(APK/签名)文件"
                        })
                    }
                )
            }
        }
    }
    if (isWindows) {
        FilePicker(
            show = showFilePickerApk,
            fileExtensions = listOf("apk")
        ) { platformFile ->
            showFilePickerApk = false
            if (platformFile?.path?.isNotBlank() == true && platformFile.path.endsWith(".apk")) {
                viewModel.apkVerifier(platformFile.path)
            } else if (platformFile?.path?.isNotBlank() == true && (platformFile.path.endsWith(".jks") || platformFile.path.endsWith(".keystore"))) {
                signaturePath.value = platformFile.path
            }
        }
    }
}

/**
 * 签名验证弹窗
 */
@Composable
private fun SignatureDialog(modifier: Modifier, viewModel: MainViewModel, signaturePath: MutableState<String>, toastState: ToastUIState, scope: CoroutineScope) {
    if (signaturePath.value.isNotBlank()) {
        val password = remember { mutableStateOf("") }
        val alisa = remember { mutableStateOf("") }
        AlertDialog(
            icon = {
                Icon(Icons.Rounded.Password, contentDescription = "Password")
            },
            title = {
                Text(text = "请输入签名密码")
            },
            text = {
                Column {
                    OutlinedTextField(
                        modifier = modifier.padding(vertical = 4.dp),
                        value = password.value,
                        onValueChange = { value ->
                            password.value = value
                            alisa.value = viewModel.verifyAlisa(signaturePath.value, value)
                        },
                        label = { Text("签名密码", style = MaterialTheme.typography.labelLarge) },
                        isError = alisa.value.isBlank(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    OutlinedTextField(
                        modifier = modifier.padding(vertical = 6.dp),
                        value = alisa.value,
                        readOnly = true,
                        onValueChange = { _ -> },
                        label = { Text("签名别名", style = MaterialTheme.typography.labelLarge) },
                        singleLine = true
                    )
                }
            },
            onDismissRequest = {
                signaturePath.value = ""
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (alisa.value.isNotBlank()) {
                            viewModel.signerVerifier(signaturePath.value, password.value, alisa.value)
                            signaturePath.value = ""
                        } else {
                            scope.launch {
                                toastState.show(ToastModel("签名密码错误", ToastModel.Type.Error))
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        signaturePath.value = ""
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 签名信息 - { Subject，Valid from，Valid until}
 */
@Composable
private fun SignatureListTop(modifier: Modifier = Modifier, verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = modifier.padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.subject, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Rounded.Subject, contentDescription = "Subject", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Subject", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.subject, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.validFrom, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.QueryBuilder, contentDescription = "Valid from", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Valid from", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.validFrom, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.validUntil, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Restore, contentDescription = "Valid until", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Valid until", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.validUntil, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


/**
 * 签名信息 - { Public Key Type，Modulus，Signature Type}
 */
@Composable
private fun SignatureListCenter(modifier: Modifier = Modifier, verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = modifier.padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.publicKeyType, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.Public, contentDescription = "Public Key Type", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Public Key Type", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.publicKeyType, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.modulus, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.LibraryBooks, contentDescription = "Modulus", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Modulus", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.modulus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.signatureType, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.Lock, contentDescription = "Signature Type", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("Signature Type", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.signatureType, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * 签名信息 - { MD5，SHA-1，SHA-256}
 */
@Composable
private fun SignatureListBottom(modifier: Modifier = Modifier, verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
    ) {
        Column(
            modifier = modifier.padding(vertical = 20.dp, horizontal = 16.dp)
        ) {
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.md5, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Rounded.SentimentSatisfied, contentDescription = "MD5", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("MD5", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.md5, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(
                modifier = modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        copy(verifier.sha1, toastState)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.SentimentDissatisfied, contentDescription = "SHA-1", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("SHA-1", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha1, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.sha256, toastState)
                }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.SentimentVeryDissatisfied, contentDescription = "SHA-256", modifier = modifier.padding(end = 14.dp))
                    Column {
                        Text("SHA-256", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha256, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}