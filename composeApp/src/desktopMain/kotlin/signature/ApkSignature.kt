package signature

import LottieAnimation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showFileSelector
import file.showFolderSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.SignatureEnum
import model.SignaturePolicy
import toast.ToastModel
import toast.ToastUIState
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/6 10:43
 * @Description : 描述
 * @Version     : 1.0
 */
@Composable
fun ApkSignature(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    SignatureBox(modifier, viewModel, toastState, scope)
    when (val uiState = viewModel.apkSignatureUIState) {
        UIState.WAIT -> Unit
        UIState.Loading -> {
            Box(
                modifier = modifier.padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(scope, "files/lottie_loading.json", modifier)
            }
        }

        is UIState.Success -> scope.launch {
            toastState.currentData?.dismiss()
            toastState.show(ToastModel(uiState.result as String, ToastModel.Type.Success))
        }

        is UIState.Error -> scope.launch {
            toastState.currentData?.dismiss()
            toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
        }
    }
}

/**
 * 签名主页
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SignatureBox(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    var isDragging by remember { mutableStateOf(false) }
    val isWindows = System.getProperty("os.name").startsWith("Win")
    val isApkError = viewModel.apkSignatureState.apkPath.isNotBlank() && !File(viewModel.apkSignatureState.apkPath).isFile
    val isOutputError = viewModel.apkSignatureState.outPutPath.isNotBlank() && !File(viewModel.apkSignatureState.outPutPath).isDirectory
    val isSignatureError = viewModel.apkSignatureState.signaturePath.isNotBlank() && !File(viewModel.apkSignatureState.signaturePath).isFile
    val isSignaturePasswordError = viewModel.apkSignatureState.signaturePassword.isNotBlank() && viewModel.apkSignatureState.signatureAlisa.isBlank()
    val isSignatureAlisaPasswordError = viewModel.apkSignatureState.signatureAlisa.isNotBlank() && viewModel.apkSignatureState.signatureAlisaPassword.isNotBlank() && !viewModel.verifyAlisaPassword()
    Box(modifier = modifier.padding(top = 20.dp, bottom = 20.dp, end = 14.dp).onExternalDrag(
        onDragStart = { isDragging = true },
        onDragExit = { isDragging = false },
        onDrop = { state ->
            val dragData = state.dragData
            if (dragData is DragData.FilesList) {
                var mApkPath = ""
                var mSignaturePath = ""
                dragData.readFiles().forEach {
                    if (it.endsWith(".apk") && mApkPath.isBlank()) {
                        mApkPath = File(URI.create(it)).path
                    } else if ((it.endsWith(".jks") || it.endsWith(".keystore")) && mSignaturePath.isBlank()) {
                        mSignaturePath = File(URI.create(it)).path
                    }
                }
                if (mApkPath.isNotBlank()) {
                    viewModel.updateApkSignature(SignatureEnum.APK_PATH, mApkPath)
                }
                if (mSignaturePath.isNotBlank()) {
                    viewModel.updateApkSignature(SignatureEnum.SIGNATURE_PATH, mSignaturePath)
                }
            }
            isDragging = false
        })
    ) {
        Card(
            modifier = modifier.fillMaxHeight().fillMaxWidth()
        ) {
            LazyColumn(
                modifier = modifier.fillMaxHeight().fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Spacer(Modifier.size(16.dp))
                    SignatureApk(modifier, viewModel, isApkError, isWindows)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignatureOutput(modifier, viewModel, isOutputError, isWindows)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignaturePolicy(modifier, viewModel, toastState, scope)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignaturePath(modifier, viewModel, isSignatureError, isWindows)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignaturePassword(modifier, viewModel, isSignaturePasswordError)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignatureAlisa(modifier, viewModel)
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    SignatureAlisaPassword(modifier, viewModel, isSignatureAlisaPasswordError)
                }
                item {
                    Spacer(Modifier.size(12.dp))
                    Signature(modifier, viewModel, isApkError, isOutputError, isSignatureError, isSignaturePasswordError, isSignatureAlisaPasswordError, toastState, scope)
                    Spacer(Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * APK文件
 */
@Composable
private fun SignatureApk(modifier: Modifier = Modifier, viewModel: MainViewModel, isApkError: Boolean, isWindows: Boolean) {
    var showFilePickerApk by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.apkPath,
            onValueChange = { path ->
                viewModel.updateApkSignature(SignatureEnum.APK_PATH, path)
            },
            label = { Text("APK文件", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = isApkError
        )
        SmallFloatingActionButton(
            onClick = {
                if (isWindows) {
                    showFilePickerApk = true
                } else {
                    showFileSelector { path ->
                        viewModel.updateApkSignature(SignatureEnum.APK_PATH, path)
                    }
                }
            }
        ) {
            Icon(Icons.Rounded.FolderOpen, "选择文件")
        }
    }
    if (isWindows) {
        FilePicker(show = showFilePickerApk, fileExtensions = listOf("apk")) { platformFile ->
            showFilePickerApk = false
            if (platformFile?.path?.isNotBlank() == true && platformFile.path.endsWith("apk")) {
                viewModel.updateApkSignature(SignatureEnum.APK_PATH, platformFile.path)
            }
        }
    }
}

/**
 * 签名输出路径
 */
@Composable
private fun SignatureOutput(modifier: Modifier = Modifier, viewModel: MainViewModel, isOutputError: Boolean, isWindows: Boolean) {
    var showDirPicker by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.outPutPath,
            onValueChange = { path ->
                viewModel.updateApkSignature(SignatureEnum.OUT_PUT_PATH, path)
            },
            label = { Text("输出路径", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = isOutputError
        )
        SmallFloatingActionButton(
            onClick = {
                if (isWindows) {
                    showDirPicker = true
                } else {
                    showFolderSelector { path ->
                        viewModel.updateApkSignature(SignatureEnum.OUT_PUT_PATH, path)
                    }
                }
            }
        ) {
            Icon(Icons.Rounded.FolderOpen, "选择文件夹")
        }
    }
    if (isWindows) {
        DirectoryPicker(showDirPicker) { path ->
            showDirPicker = false
            if (path?.isNotBlank() == true) {
                viewModel.updateApkSignature(SignatureEnum.OUT_PUT_PATH, path)
            }
        }
    }
}

/**
 * 签名策略
 */
@Composable
private fun SignaturePolicy(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    val policyList = listOf(SignaturePolicy.V1, SignaturePolicy.V2, SignaturePolicy.V2Only, SignaturePolicy.V3)
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("签名策略：", style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(start = 24.dp))
            Text(viewModel.apkSignatureState.signaturePolicy.value, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(2.dp))
        Row(
            modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)
        ) {
            policyList.forEach { signaturePolicy ->
                ElevatedFilterChip(
                    modifier = modifier.weight(1f).padding(horizontal = 8.dp),
                    selected = viewModel.apkSignatureState.signaturePolicy == signaturePolicy,
                    onClick = {
                        toastState.currentData?.dismiss()
                        if (signaturePolicy == SignaturePolicy.V2Only) {
                            scope.launch {
                                toastState.show(ToastModel("使用 V2 Only 签名的APK包仅支持Android 7及更高版本的系统安装和使用，请注意", ToastModel.Type.Warning), 8000L)
                            }
                        }
                        viewModel.updateApkSignature(SignatureEnum.SIGNATURE_POLICY, signaturePolicy)
                    },
                    label = { Text(signaturePolicy.title, textAlign = TextAlign.End, modifier = modifier.fillMaxWidth().padding(8.dp)) },
                    leadingIcon = if (viewModel.apkSignatureState.signaturePolicy == signaturePolicy) {
                        { Icon(imageVector = Icons.Rounded.Done, contentDescription = "Done icon", modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/**
 * 签名文件
 */
@Composable
private fun SignaturePath(modifier: Modifier = Modifier, viewModel: MainViewModel, isSignatureError: Boolean, isWindows: Boolean) {
    var showFilePickerSignature by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.signaturePath,
            onValueChange = { path ->
                viewModel.updateApkSignature(SignatureEnum.SIGNATURE_PATH, path)
            },
            label = { Text("签名文件", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = isSignatureError
        )
        SmallFloatingActionButton(
            onClick = {
                if (isWindows) {
                    showFilePickerSignature = true
                } else {
                    showFileSelector(false) { path ->
                        viewModel.updateApkSignature(SignatureEnum.SIGNATURE_PATH, path)
                    }
                }
            }
        ) {
            Icon(Icons.Rounded.FolderOpen, "选择文件")
        }
    }
    if (isWindows) {
        FilePicker(show = showFilePickerSignature, fileExtensions = listOf("jks", "keystore")) { platformFile ->
            showFilePickerSignature = false
            if (platformFile?.path?.isNotBlank() == true && (platformFile.path.endsWith("jks") || platformFile.path.endsWith("keystore"))) {
                viewModel.updateApkSignature(SignatureEnum.SIGNATURE_PATH, platformFile.path)
            }
        }
    }
}

/**
 * 签名密码
 */
@Composable
private fun SignaturePassword(modifier: Modifier = Modifier, viewModel: MainViewModel, isSignaturePasswordError: Boolean) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.signaturePassword,
            onValueChange = { path ->
                viewModel.updateApkSignature(SignatureEnum.SIGNATURE_PASSWORD, path)
                if (viewModel.apkSignatureState.signaturePath.isNotBlank() && File(viewModel.apkSignatureState.signaturePath).isFile) {
                    viewModel.verifyAlisa()
                }
            },
            label = { Text("签名密码", style = MaterialTheme.typography.labelLarge) },
            isError = isSignaturePasswordError,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

/**
 * 签名别名
 */
@Composable
private fun SignatureAlisa(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.signatureAlisa,
            readOnly = true,
            onValueChange = { _ -> },
            label = { Text("签名别名", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

/**
 * 签名别名密码
 */
@Composable
private fun SignatureAlisaPassword(modifier: Modifier = Modifier, viewModel: MainViewModel, isSignatureAlisaPasswordError: Boolean) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.apkSignatureState.signatureAlisaPassword,
            onValueChange = { path ->
                viewModel.updateApkSignature(SignatureEnum.SIGNATURE_ALISA_PASSWORD, path)
            },
            label = { Text("签名别名密码", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = isSignatureAlisaPasswordError,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

/**
 * 开始签名按钮
 */
@Composable
private fun Signature(modifier: Modifier = Modifier, viewModel: MainViewModel, isApkError: Boolean, isOutputError: Boolean, isSignatureError: Boolean, isSignaturePasswordError: Boolean, isSignatureAlisaPasswordError: Boolean, toastState: ToastUIState, scope: CoroutineScope) {
    ElevatedButton(
        onClick = {
            if (isApkError || isOutputError || isSignatureError || isSignaturePasswordError || isSignatureAlisaPasswordError) {
                scope.launch {
                    toastState.show(ToastModel("请检查Error项", ToastModel.Type.Error))
                }
                return@ElevatedButton
            }
            signatureApk(viewModel, toastState, scope)
        }
    ) {
        Text("开始签名", style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(horizontal = 48.dp))
    }
}

private fun signatureApk(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    if (viewModel.apkSignatureState.apkPath.isBlank() ||
        viewModel.apkSignatureState.outPutPath.isBlank() ||
        viewModel.apkSignatureState.signaturePath.isBlank() ||
        viewModel.apkSignatureState.signaturePassword.isBlank() ||
        viewModel.apkSignatureState.signatureAlisa.isBlank() ||
        viewModel.apkSignatureState.signatureAlisaPassword.isBlank()
    ) {
        scope.launch {
            toastState.show(ToastModel("请检查空项", ToastModel.Type.Error))
        }
        return
    }
    viewModel.apkSigner()
}