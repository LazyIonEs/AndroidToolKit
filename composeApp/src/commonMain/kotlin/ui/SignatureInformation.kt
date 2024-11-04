package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueryBuilder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import model.DarkThemeConfig
import model.Verifier
import model.VerifierResult
import utils.LottieAnimation
import utils.copy
import utils.isApk
import utils.isKey
import vm.MainViewModel
import vm.UIState
import kotlin.io.path.pathString

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/5 19:47
 * @Description : 签名信息
 * @Version     : 1.0
 */
@Composable
fun SignatureInformation(
    viewModel: MainViewModel
) {
    val scope = rememberCoroutineScope()
    val signaturePath = remember { mutableStateOf("") }
    if (viewModel.verifierState == UIState.WAIT) {
        SignatureLottie(viewModel, scope)
    }
    SignatureList(viewModel)
    SignatureBox(viewModel, signaturePath, scope)
    LoadingAnimate(viewModel.verifierState == UIState.Loading, viewModel, scope)
    SignatureDialog(viewModel, signaturePath)
}

/**
 * 主页动画
 */
@Composable
private fun SignatureLottie(viewModel: MainViewModel, scope: CoroutineScope) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    Box(
        modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
    ) {
        if (useDarkTheme) {
            LottieAnimation(scope, "files/lottie_main_1_dark.json")
        } else {
            LottieAnimation(scope, "files/lottie_main_1_light.json")
        }
    }
}

/**
 * 签名主页，包含拖拽文件逻辑
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SignatureBox(
    viewModel: MainViewModel, signaturePath: MutableState<String>, scope: CoroutineScope
) {
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(modifier = Modifier.fillMaxSize()
        .dragAndDropTarget(shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
            dragging = it
        }, onFinish = { result ->
            result.onSuccess { fileList ->
                fileList.firstOrNull()?.let {
                    val path = it.toAbsolutePath().pathString
                    if (path.isApk) {
                        viewModel.apkVerifier(path)
                    } else if (path.isKey) {
                        signaturePath.value = path
                    }
                }
            }
        })
        ), contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.align(Alignment.BottomEnd)) {
            AnimatedVisibility(
                visible = viewModel.verifierState != UIState.Loading, modifier = Modifier.align(Alignment.End)
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
            AnimatedVisibility(
                visible = viewModel.verifierState is UIState.Success,
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp, end = 16.dp)
            ) {
                TooltipBox(
                    positionProvider = rememberPlainTooltipPositionProvider(), tooltip = {
                        PlainTooltip {
                            Text(
                                "复制MD5、SHA1和SHA-256", style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }, state = rememberTooltipState()
                ) {
                    ExtendedFloatingActionButton(onClick = {
                        ((viewModel.verifierState as UIState.Success).result as? VerifierResult)?.let { result ->
                            if (result.isSuccess) {
                                result.data.getOrNull(0)?.let { verifier ->
                                    val stringBuilder = StringBuilder()
                                    stringBuilder.append("MD5: ${verifier.md5}")
                                    stringBuilder.append(System.lineSeparator())
                                    stringBuilder.append("SHA1: ${verifier.sha1}")
                                    stringBuilder.append(System.lineSeparator())
                                    stringBuilder.append("SHA-256: ${verifier.sha256}")
                                    copy(stringBuilder.toString(), viewModel)
                                }
                            }
                        }
                    },
                        expanded = true,
                        icon = { Icon(Icons.Rounded.ContentCopy, "一键复制") },
                        text = { Text("一键复制") })
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
    viewModel: MainViewModel
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
                        SignatureListTop(verifier, viewModel)
                        SignatureListCenter(verifier, viewModel)
                        SignatureListBottom(verifier, viewModel)
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
    viewModel: MainViewModel, signaturePath: MutableState<String>
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
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
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
                                text = { Text(text = selectionOption, style = MaterialTheme.typography.labelLarge) },
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
                    viewModel.updateSnackbarVisuals("密钥密码错误")
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
    verifier: Verifier, viewModel: MainViewModel
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.subject, viewModel)
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
                copy(verifier.validFrom, viewModel)
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
                copy(verifier.validUntil, viewModel)
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
    verifier: Verifier, viewModel: MainViewModel
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.publicKeyType, viewModel)
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
                copy(verifier.modulus, viewModel)
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
                copy(verifier.signatureType, viewModel)
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
    verifier: Verifier, viewModel: MainViewModel
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.md5, viewModel)
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
                copy(verifier.sha1, viewModel)
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
                copy(verifier.sha256, viewModel)
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