package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueryBuilder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Verifier
import model.VerifierResult
import toast.ToastModel
import toast.ToastUIState
import utils.LottieAnimation
import utils.copy
import utils.isApk
import utils.isKey
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/5 19:47
 * @Description : 签名信息
 * @Version     : 1.0
 */
@Composable
fun SignatureInformation(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    val signaturePath = remember { mutableStateOf("") }
    val uiState = viewModel.verifierState
    if (uiState == UIState.WAIT || uiState is UIState.Error) {
        if (uiState is UIState.Error) {
            scope.launch {
                toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
            }
        }
        SignatureMain(scope)
    }
    SignatureList(viewModel, toastState, scope)
    SignatureBox(viewModel, signaturePath, scope)
    LoadingAnimate(viewModel.verifierState == UIState.Loading, scope)
    SignatureDialog(viewModel, signaturePath, toastState, scope)
}

/**
 * 主页动画
 */
@Composable
private fun SignatureMain(scope: CoroutineScope) {
    Box(
        modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
    ) {
        LottieAnimation(scope, "files/lottie_main_2.json")
    }
}

/**
 * 签名主页，包含拖拽文件逻辑
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignatureBox(
    viewModel: MainViewModel, signaturePath: MutableState<String>, scope: CoroutineScope
) {
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize().padding(6.dp)
            .onExternalDrag(onDragStart = { dragging = true }, onDragExit = { dragging = false }, onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    dragData.readFiles().first().let {
                        if (it.isApk) {
                            val path = File(URI.create(it)).path
                            viewModel.apkVerifier(path)
                        } else if (it.isKey) {
                            val path = File(URI.create(it)).path
                            signaturePath.value = path
                        } else {

                        }
                    }
                }
                dragging = false
            }), contentAlignment = Alignment.TopCenter
    ) {
        Row(modifier = Modifier.align(Alignment.BottomEnd)) {
            AnimatedVisibility(
                visible = viewModel.verifierState is UIState.Success, modifier = Modifier.padding(end = 12.dp)
            ) {
                var rotateValue by remember { mutableStateOf(0f) }
                val rotate: Float by animateFloatAsState(
                    targetValue = rotateValue,
                    animationSpec = tween(durationMillis = 400, easing = LinearEasing)
                )
                ExtendedFloatingActionButton(
                    onClick = {
                        rotateValue -= 360
                        viewModel.changeSeparatorSign()
                    },
                    icon = { Icon(Icons.Rounded.Sync, "切换间隔符号", modifier = Modifier.rotate(rotate)) },
                    text = { Text("切换间隔字符") })
            }
            AnimatedVisibility(
                visible = viewModel.verifierState != UIState.Loading
            ) {
                FileButton(
                    value = if (dragging) {
                        "愣着干嘛，还不松手"
                    } else {
                        "点击选择或拖拽上传(APK/签名)文件"
                    },
                    expanded = viewModel.verifierState !is UIState.Success,
                    FileSelectorType.KEY,
                    FileSelectorType.APK
                ) { path ->
                    if (path.isApk) {
                        viewModel.apkVerifier(path)
                    } else if (path.isKey) {
                        signaturePath.value = path
                    }
                }
            }
        }
    }
}

/**
 * 签名信息列表
 */
@Composable
private fun SignatureList(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    val uiState = viewModel.verifierState
    AnimatedVisibility(
        visible = uiState is UIState.Success, enter = fadeIn(), exit = fadeOut()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 6.dp, bottom = 6.dp, end = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState is UIState.Success) {
                items((uiState.result as VerifierResult).data) { verifier ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Card(
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
                        ) {
                            if (uiState.result.isApk) {
                                Text(
                                    "Valid APK signature V${verifier.version} found",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                Text(
                                    "Valid KeyStore Signature V${verifier.version} found",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        SignatureListTop(verifier, scope, toastState)
                        SignatureListCenter(verifier, scope, toastState)
                        SignatureListBottom(verifier, scope, toastState)
                    }
                }
                item { Spacer(Modifier.size(24.dp)) }
            }
        }
    }
}

/**
 * 签名验证弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureDialog(
    viewModel: MainViewModel, signaturePath: MutableState<String>, toastState: ToastUIState, scope: CoroutineScope
) {
    if (signaturePath.value.isNotBlank()) {
        val password = remember { mutableStateOf("") }
        var options by remember { mutableStateOf<ArrayList<String>?>(null) }
        var alisa by remember { mutableStateOf(options?.getOrNull(0) ?: "") }
        AlertDialog(icon = {
            Icon(Icons.Rounded.Password, contentDescription = "Password")
        }, title = {
            Text(text = "请输入密钥密码")
        }, text = {
            var expanded by remember { mutableStateOf(false) }
            Column {
                OutlinedTextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    value = password.value,
                    onValueChange = { value ->
                        password.value = value
                        options = viewModel.verifyAlisa(signaturePath.value, value)
                        alisa = if (!options.isNullOrEmpty()) {
                            options?.getOrNull(0) ?: ""
                        } else {
                            ""
                        }
                    },
                    label = { Text("密钥密码", style = MaterialTheme.typography.labelLarge) },
                    isError = alisa.isBlank(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                ExposedDropdownMenuBox(modifier = Modifier.padding(vertical = 6.dp),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor(),
                        value = alisa,
                        readOnly = true,
                        onValueChange = { },
                        label = { Text("密钥别名", style = MaterialTheme.typography.labelLarge) },
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
                                text = { Text(selectionOption) },
                                onClick = {
                                    alisa = selectionOption
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
        }, onDismissRequest = {
            signaturePath.value = ""
        }, confirmButton = {
            TextButton(onClick = {
                if (alisa.isNotBlank()) {
                    viewModel.signerVerifier(
                        signaturePath.value, password.value, alisa
                    )
                    signaturePath.value = ""
                } else {
                    scope.launch {
                        toastState.show(ToastModel("密钥密码错误", ToastModel.Type.Error))
                    }
                }
            }) {
                Text("确认")
            }
        }, dismissButton = {
            TextButton(onClick = {
                signaturePath.value = ""
            }) {
                Text("取消")
            }
        })
    }
}

/**
 * 签名信息 - { Subject，Valid from，Valid until}
 */
@Composable
private fun SignatureListTop(
    verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.subject, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Subject,
                        contentDescription = "Subject",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Subject", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.subject, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.validFrom, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueryBuilder,
                        contentDescription = "Valid from",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Valid from", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.validFrom, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.validUntil, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Valid until",
                        modifier = Modifier.padding(end = 14.dp)
                    )
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
private fun SignatureListCenter(
    verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.publicKeyType, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = "Public Key Type",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Public Key Type", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.publicKeyType, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.modulus, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                        contentDescription = "Modulus",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Modulus", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.modulus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.signatureType, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Signature Type",
                        modifier = Modifier.padding(end = 14.dp)
                    )
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
private fun SignatureListBottom(
    verifier: Verifier, scope: CoroutineScope, toastState: ToastUIState
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.md5, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SentimentSatisfied,
                        contentDescription = "MD5",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("MD5", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.md5, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.sha1, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentDissatisfied,
                        contentDescription = "SHA-1",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("SHA-1", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha1, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    copy(verifier.sha256, toastState)
                }
            }) {
                Row(
                    modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentVeryDissatisfied,
                        contentDescription = "SHA-256",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("SHA-256", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha256, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}