package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import file.showFolderSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.KeyStoreEnum
import toast.ToastModel
import toast.ToastUIState
import utils.LottieAnimation
import utils.isWindows
import vm.MainViewModel
import vm.UIState
import java.io.File

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/1 15:28
 * @Description : 签名生成
 * @Version     : 1.0
 */
@Composable
fun SignatureGeneration(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    GenerationBox(viewModel, toastState, scope)
    when (val uiState = viewModel.keyStoreInfoUIState) {
        UIState.WAIT -> Unit
        UIState.Loading -> {
            Box(
                modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
            ) {
                LottieAnimation(scope, "files/lottie_loading.json")
            }
        }

        is UIState.Success -> scope.launch {
            toastState.show(ToastModel(uiState.result as String, ToastModel.Type.Success))
        }

        is UIState.Error -> scope.launch {
            toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
        }
    }
}

/**
 * 签名生成
 */
@Composable
fun GenerationBox(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val keyStorePathError =
            viewModel.keyStoreInfoState.keyStorePath.isNotBlank() && !File(viewModel.keyStoreInfoState.keyStorePath).isDirectory
        val keyStoreNameError =
            viewModel.keyStoreInfoState.keyStoreName.isNotBlank() && !(viewModel.keyStoreInfoState.keyStoreName.endsWith(
                ".jks"
            ) || viewModel.keyStoreInfoState.keyStoreName.endsWith(".keystore"))
        val keyStoreConfirmPasswordError =
            viewModel.keyStoreInfoState.keyStoreConfirmPassword.isNotBlank() && viewModel.keyStoreInfoState.keyStorePassword != viewModel.keyStoreInfoState.keyStoreConfirmPassword
        val keyStoreAlisaConfirmPasswordError =
            viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword.isNotBlank() && viewModel.keyStoreInfoState.keyStoreAlisaPassword != viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                KeyStorePath(viewModel, keyStorePathError)
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStoreName(viewModel, keyStoreNameError)
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStorePassword(viewModel, keyStoreConfirmPasswordError)
            }
            item {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Key", style = MaterialTheme.typography.titleSmall)
                    Divider(thickness = 2.dp, startIndent = 18.dp)
                }
                Spacer(Modifier.size(12.dp))
                KeyStoreAlisa(viewModel)
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStoreAlisaPassword(viewModel, keyStoreAlisaConfirmPasswordError)
            }
            item {
                Spacer(Modifier.size(4.dp))
                ValidityPeriod(viewModel)
            }
            item {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Certificate", style = MaterialTheme.typography.titleSmall)
                    Divider(thickness = 2.dp, startIndent = 18.dp)
                }
                Spacer(Modifier.size(12.dp))
                AuthorName(viewModel)
            }
            item {
                Spacer(Modifier.size(2.dp))
                OrganizationalUnit(viewModel)
            }
            item {
                Spacer(Modifier.size(2.dp))
                Organizational(viewModel)
            }
            item {
                Spacer(Modifier.size(2.dp))
                City(viewModel)
            }
            item {
                Spacer(Modifier.size(2.dp))
                Province(viewModel)
            }
            item {
                Spacer(Modifier.size(2.dp))
                CountryCode(viewModel)
            }
            item {
                Spacer(Modifier.size(12.dp))
                CreateSignature(
                    viewModel,
                    keyStorePathError,
                    keyStoreNameError,
                    keyStoreConfirmPasswordError,
                    keyStoreAlisaConfirmPasswordError,
                    toastState,
                    scope
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun KeyStorePath(viewModel: MainViewModel, keyStorePathError: Boolean) {
    var showDirPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStorePath,
            onValueChange = { path ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_PATH, path)
            },
            label = { Text("密钥输出路径", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = keyStorePathError
        )
        SmallFloatingActionButton(onClick = {
            if (isWindows) {
                showDirPicker = true
            } else {
                showFolderSelector { path ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_PATH, path)
                }
            }
        }) {
            Icon(Icons.Rounded.FolderOpen, "选择文件夹")
        }
    }
    if (isWindows) {
        DirectoryPicker(showDirPicker) { path ->
            showDirPicker = false
            if (path?.isNotBlank() == true) {
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_PATH, path)
            }
        }
    }
}

@Composable
fun KeyStoreName(viewModel: MainViewModel, keyStoreNameError: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStoreName,
            onValueChange = { name ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_NAME, name)
            },
            label = { Text("密钥名称", style = MaterialTheme.typography.labelLarge) },
            isError = keyStoreNameError,
            singleLine = true
        )
    }
}

@Composable
fun KeyStorePassword(
    viewModel: MainViewModel, keyStoreConfirmPasswordError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStorePassword,
            onValueChange = { password ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_PASSWORD, password)
            },
            label = { Text("密钥密码", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStoreConfirmPassword,
            onValueChange = { password ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_CONFIRM_PASSWORD, password)
            },
            label = { Text("确认密码", style = MaterialTheme.typography.labelLarge) },
            isError = keyStoreConfirmPasswordError,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

@Composable
fun KeyStoreAlisa(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStoreAlisa,
            onValueChange = { name ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_ALISA, name)
            },
            label = { Text("密钥别名", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun KeyStoreAlisaPassword(
    viewModel: MainViewModel, keyStoreAlisaConfirmPasswordError: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStoreAlisaPassword,
            onValueChange = { password ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_ALISA_PASSWORD, password)
            },
            label = { Text("别名密码", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword,
            onValueChange = { password ->
                viewModel.updateSignatureGenerate(
                    KeyStoreEnum.KEY_STORE_ALISA_CONFIRM_PASSWORD, password
                )
            },
            label = { Text("确认密码", style = MaterialTheme.typography.labelLarge) },
            isError = keyStoreAlisaConfirmPasswordError,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

@Composable
fun ValidityPeriod(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+\$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.validityPeriod,
            onValueChange = { validityPeriod ->
                if (validityPeriod.isEmpty() || validityPeriod.matches(pattern)) {
                    viewModel.updateSignatureGenerate(KeyStoreEnum.VALIDITY_PERIOD, validityPeriod)
                }
            },
            label = { Text("密码有效期（单位：年）", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
fun AuthorName(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.authorName,
            onValueChange = { authorName ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.AUTHOR_NAME, authorName)
            },
            label = { Text("作者名称", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun OrganizationalUnit(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.organizationalUnit,
            onValueChange = { organizationalUnit ->
                viewModel.updateSignatureGenerate(
                    KeyStoreEnum.ORGANIZATIONAL_UNIT, organizationalUnit
                )
            },
            label = { Text("组织单位", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun Organizational(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.organizational,
            onValueChange = { organizational ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.ORGANIZATIONAL, organizational)
            },
            label = { Text("组织", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun City(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.city,
            onValueChange = { city ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.CITY, city)
            },
            label = { Text("城市", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun Province(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.province,
            onValueChange = { province ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.PROVINCE, province)
            },
            label = { Text("省份", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

@Composable
fun CountryCode(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 56.dp, bottom = 3.dp).weight(1f),
            value = viewModel.keyStoreInfoState.countryCode,
            onValueChange = { countryCode ->
                viewModel.updateSignatureGenerate(KeyStoreEnum.COUNTRY_CODE, countryCode)
            },
            label = { Text("国家编码", style = MaterialTheme.typography.labelLarge) },
            singleLine = true
        )
    }
}

/**
 * 创建签名按钮
 */
@Composable
private fun CreateSignature(
    viewModel: MainViewModel,
    keyStorePathError: Boolean,
    keyStoreNameError: Boolean,
    keyStoreConfirmPasswordError: Boolean,
    keyStoreAlisaConfirmPasswordError: Boolean,
    toastState: ToastUIState,
    scope: CoroutineScope
) {
    ElevatedButton(onClick = {
        if (keyStorePathError || keyStoreNameError || keyStoreConfirmPasswordError || keyStoreAlisaConfirmPasswordError) {
            scope.launch {
                toastState.show(ToastModel("请检查Error项", ToastModel.Type.Error))
            }
            return@ElevatedButton
        }
        createSignature(viewModel, toastState, scope)
    }) {
        Text(
            "创建签名",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

/**
 * 创建签名
 */
fun createSignature(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    if (viewModel.keyStoreInfoState.keyStorePath.isBlank() || viewModel.keyStoreInfoState.keyStoreName.isBlank() || viewModel.keyStoreInfoState.keyStorePassword.isBlank() || viewModel.keyStoreInfoState.keyStoreConfirmPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisa.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword.isBlank() || viewModel.keyStoreInfoState.validityPeriod.isBlank() || viewModel.keyStoreInfoState.authorName.isBlank() || viewModel.keyStoreInfoState.organizationalUnit.isBlank() || viewModel.keyStoreInfoState.organizational.isBlank() || viewModel.keyStoreInfoState.city.isBlank() || viewModel.keyStoreInfoState.province.isBlank() || viewModel.keyStoreInfoState.countryCode.isBlank()) {
        scope.launch {
            toastState.show(ToastModel("请检查空项", ToastModel.Type.Error))
        }
        return
    }
    viewModel.createSignature()
}


