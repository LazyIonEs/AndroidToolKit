package ui

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showFileSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.Verifier
import model.VerifierResult
import toast.ToastModel
import toast.ToastUIState
import utils.LottieAnimation
import utils.copy
import utils.isWindows
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
    SignatureLoading(viewModel, scope)
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
    AnimatedVisibility(
        visible = dragging,
        enter = fadeIn() + slideIn(
            tween(
                durationMillis = 400, easing = LinearOutSlowInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) },
        exit = slideOut(
            tween(
                durationMillis = 400, easing = FastOutLinearInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) } + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            )
        ) {
            LottieAnimation(scope, "files/upload.json")
        }
    }
    Box(modifier = Modifier.padding(6.dp)
        .onExternalDrag(onDragStart = { dragging = true }, onDragExit = { dragging = false }, onDrop = { state ->
            val dragData = state.dragData
            if (dragData is DragData.FilesList) {
                dragData.readFiles().first().let {
                    if (it.endsWith(".apk")) {
                        val path = File(URI.create(it)).path
                        viewModel.apkVerifier(path)
                    } else if (it.endsWith(".jks") || it.endsWith(".keystore")) {
                        val path = File(URI.create(it)).path
                        signaturePath.value = path
                    } else {
                    }
                }
            }
            dragging = false
        }), contentAlignment = Alignment.TopCenter
    ) {
        SignatureFloatingButton(viewModel, dragging, signaturePath)
    }
}

/**
 * Loading动画
 */
@Composable
private fun SignatureLoading(
    viewModel: MainViewModel, scope: CoroutineScope
) {
    AnimatedVisibility(
        visible = viewModel.verifierState == UIState.Loading,
        enter = fadeIn() + expandHorizontally(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
        ) {
            LottieAnimation(scope, "files/lottie_loading.json")
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
 * 选择文件按钮
 */
@Composable
private fun SignatureFloatingButton(
    viewModel: MainViewModel, dragging: Boolean, signaturePath: MutableState<String>
) {
    var showFilePickerApk by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)
        ) {
            AnimatedVisibility(
                visible = viewModel.verifierState is UIState.Success, modifier = Modifier.padding(end = 12.dp)
            ) {
                ExtendedFloatingActionButton(onClick = {
                    viewModel.changeSeparatorSign()
                }, icon = { Icon(Icons.Rounded.Sync, "切换间隔符号") }, text = {
                    Text("切换间隔符号")
                })
            }
            AnimatedVisibility(
                visible = viewModel.verifierState != UIState.Loading
            ) {
                ExtendedFloatingActionButton(onClick = {
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
                }, icon = { Icon(Icons.Rounded.DriveFolderUpload, "准备选择文件") }, text = {
                    Text(
                        if (dragging) {
                            "愣着干嘛，还不松手"
                        } else {
                            "点击选择或拖拽上传(APK/签名)文件"
                        }
                    )
                }, expanded = viewModel.verifierState !is UIState.Success)
            }
        }
    }
    if (isWindows) {
        FilePicker(
            show = showFilePickerApk, fileExtensions = listOf("apk")
        ) { platformFile ->
            showFilePickerApk = false
            if (platformFile?.path?.isNotBlank() == true && platformFile.path.endsWith(".apk")) {
                viewModel.apkVerifier(platformFile.path)
            } else if (platformFile?.path?.isNotBlank() == true && (platformFile.path.endsWith(".jks") || platformFile.path.endsWith(
                    ".keystore"
                ))
            ) {
                signaturePath.value = platformFile.path
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
            Text(text = "请输入签名密码")
        }, text = {
            var expanded by remember { mutableStateOf(false) }
            Column {
                OutlinedTextField(modifier = Modifier.padding(vertical = 4.dp),
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
                    label = { Text("签名密码", style = MaterialTheme.typography.labelLarge) },
                    isError = alisa.isBlank(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                ExposedDropdownMenuBox(modifier = Modifier.padding(vertical = 6.dp),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }) {
                    OutlinedTextField(modifier = Modifier.menuAnchor(),
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
                        toastState.show(ToastModel("签名密码错误", ToastModel.Type.Error))
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