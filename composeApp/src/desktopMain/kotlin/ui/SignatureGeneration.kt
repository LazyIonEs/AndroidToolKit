package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.KeyStoreEnum
import toast.ToastModel
import toast.ToastUIState
import utils.isKey
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
    LoadingAnimate(viewModel.keyStoreInfoUIState == UIState.Loading, scope)
    toast(viewModel.keyStoreInfoUIState, toastState, scope)
}

/**
 * 签名生成
 */
@Composable
private fun GenerationBox(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val keyStorePathError =
            viewModel.keyStoreInfoState.keyStorePath.isNotBlank() && !File(viewModel.keyStoreInfoState.keyStorePath).isDirectory
        val keyStoreNameError =
            viewModel.keyStoreInfoState.keyStoreName.isNotBlank() && !(viewModel.keyStoreInfoState.keyStoreName.isKey)
        val keyStoreConfirmPasswordError =
            viewModel.keyStoreInfoState.keyStoreConfirmPassword.isNotBlank() && viewModel.keyStoreInfoState.keyStorePassword != viewModel.keyStoreInfoState.keyStoreConfirmPassword
        val keyStoreAlisaConfirmPasswordError =
            viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword.isNotBlank() && viewModel.keyStoreInfoState.keyStoreAlisaPassword != viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = viewModel.keyStoreInfoState.keyStorePath,
                    label = "密钥输出路径",
                    isError = keyStorePathError
                ) { path ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_PATH, path)
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.keyStoreName,
                    label = "密钥名称",
                    isError = keyStoreNameError
                ) { name ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_NAME, name)
                }
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
                StringInput(
                    value = viewModel.keyStoreInfoState.keyStoreAlisa,
                    label = "密钥别名",
                    isError = false
                ) { name ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.KEY_STORE_ALISA, name)
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStoreAlisaPassword(viewModel, keyStoreAlisaConfirmPasswordError)
            }
            item {
                Spacer(Modifier.size(4.dp))
                IntInput(
                    value = viewModel.keyStoreInfoState.validityPeriod,
                    label = "密码有效期（单位：年）",
                    isError = viewModel.keyStoreInfoState.validityPeriod.isBlank()
                ) { validityPeriod ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.VALIDITY_PERIOD, validityPeriod)
                }
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
                StringInput(
                    value = viewModel.keyStoreInfoState.authorName,
                    label = "作者名称",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.AUTHOR_NAME, authorName)
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.organizationalUnit,
                    label = "组织单位",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.ORGANIZATIONAL_UNIT, authorName)
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.organizational,
                    label = "组织",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.ORGANIZATIONAL, authorName)
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.city,
                    label = "城市",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.CITY, authorName)
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.province,
                    label = "省份",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.PROVINCE, authorName)
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.countryCode,
                    label = "国家编码",
                    isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(KeyStoreEnum.COUNTRY_CODE, authorName)
                }
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

/**
 * 密钥密码
 */
@Composable
private fun KeyStorePassword(
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

/**
 * 别名密码
 */
@Composable
private fun KeyStoreAlisaPassword(
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
    Button(onClick = {
        if (keyStorePathError || keyStoreNameError || keyStoreConfirmPasswordError || keyStoreAlisaConfirmPasswordError) {
            scope.launch {
                toastState.show(ToastModel("请检查Error项", ToastModel.Type.Error))
            }
            return@Button
        }
        createSignature(viewModel, toastState, scope)
    }) {
        Text(
            "创建签名", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

/**
 * 创建签名
 */
private fun createSignature(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    if (viewModel.keyStoreInfoState.keyStorePath.isBlank() || viewModel.keyStoreInfoState.keyStoreName.isBlank() || viewModel.keyStoreInfoState.keyStorePassword.isBlank() || viewModel.keyStoreInfoState.keyStoreConfirmPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisa.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword.isBlank() || viewModel.keyStoreInfoState.validityPeriod.isBlank() || viewModel.keyStoreInfoState.authorName.isBlank() || viewModel.keyStoreInfoState.organizationalUnit.isBlank() || viewModel.keyStoreInfoState.organizational.isBlank() || viewModel.keyStoreInfoState.city.isBlank() || viewModel.keyStoreInfoState.province.isBlank() || viewModel.keyStoreInfoState.countryCode.isBlank()) {
        scope.launch {
            toastState.show(ToastModel("请检查空项", ToastModel.Type.Error))
        }
        return
    }
    viewModel.createSignature()
}


