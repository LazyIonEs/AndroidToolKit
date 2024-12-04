package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Divider
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
    viewModel: MainViewModel
) {
    val scope = rememberCoroutineScope()
    GenerationBox(viewModel)
    LoadingAnimate(viewModel.keyStoreInfoUIState == UIState.Loading, viewModel, scope)
}

/**
 * 签名生成
 */
@Composable
private fun GenerationBox(
    viewModel: MainViewModel
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
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStorePath = path))
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.keyStoreName, label = "密钥名称", isError = keyStoreNameError
                ) { name ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStoreName = name))
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
                    value = viewModel.keyStoreInfoState.keyStoreAlisa, label = "密钥别名", isError = false
                ) { name ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStoreAlisa = name))
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
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(validityPeriod = validityPeriod))
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
                    value = viewModel.keyStoreInfoState.authorName, label = "作者名称", isError = false
                ) { authorName ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(authorName = authorName))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.organizationalUnit, label = "组织单位", isError = false
                ) { organizationalUnit ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(organizationalUnit = organizationalUnit))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.organizational, label = "组织", isError = false
                ) { organizational ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(organizational = organizational))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.city, label = "城市", isError = false
                ) { city ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(city = city))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.province, label = "省份", isError = false
                ) { province ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(province = province))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = viewModel.keyStoreInfoState.countryCode, label = "国家编码", isError = false
                ) { countryCode ->
                    viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(countryCode = countryCode))
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                CreateSignature(
                    viewModel,
                    keyStorePathError,
                    keyStoreNameError,
                    keyStoreConfirmPasswordError,
                    keyStoreAlisaConfirmPasswordError
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
    ConfirmPasswordTextField(
        title = "密钥密码",
        password = viewModel.keyStoreInfoState.keyStorePassword,
        confirmPassword = viewModel.keyStoreInfoState.keyStoreConfirmPassword,
        confirmPasswordError = keyStoreConfirmPasswordError,
        onPasswordChange = { keyStorePassword ->
            viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStorePassword = keyStorePassword))
        },
        onConfirmPasswordChange = { keyStoreConfirmPassword ->
            viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStoreConfirmPassword = keyStoreConfirmPassword))
        }
    )
}

/**
 * 别名密码
 */
@Composable
private fun KeyStoreAlisaPassword(
    viewModel: MainViewModel, keyStoreAlisaConfirmPasswordError: Boolean
) {
    ConfirmPasswordTextField(
        title = "别名密码",
        password = viewModel.keyStoreInfoState.keyStoreAlisaPassword,
        confirmPassword = viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword,
        confirmPasswordError = keyStoreAlisaConfirmPasswordError,
        onPasswordChange = { keyStoreAlisaPassword ->
            viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStoreAlisaPassword = keyStoreAlisaPassword))
        },
        onConfirmPasswordChange = { keyStoreAlisaConfirmPassword ->
            viewModel.updateSignatureGenerate(viewModel.keyStoreInfoState.copy(keyStoreAlisaConfirmPassword = keyStoreAlisaConfirmPassword))
        }
    )
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
    keyStoreAlisaConfirmPasswordError: Boolean
) {
    Button(onClick = {
        if (keyStorePathError || keyStoreNameError || keyStoreConfirmPasswordError || keyStoreAlisaConfirmPasswordError) {
            viewModel.updateSnackbarVisuals("请检查Error项")
            return@Button
        }
        createSignature(viewModel)
    }) {
        Text(
            "创建签名", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}

/**
 * 创建签名
 */
private fun createSignature(viewModel: MainViewModel) {
    if (viewModel.keyStoreInfoState.keyStorePath.isBlank() || viewModel.keyStoreInfoState.keyStoreName.isBlank() || viewModel.keyStoreInfoState.keyStorePassword.isBlank() || viewModel.keyStoreInfoState.keyStoreConfirmPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisa.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaPassword.isBlank() || viewModel.keyStoreInfoState.keyStoreAlisaConfirmPassword.isBlank() || viewModel.keyStoreInfoState.validityPeriod.isBlank() || viewModel.keyStoreInfoState.authorName.isBlank() || viewModel.keyStoreInfoState.organizationalUnit.isBlank() || viewModel.keyStoreInfoState.organizational.isBlank() || viewModel.keyStoreInfoState.city.isBlank() || viewModel.keyStoreInfoState.province.isBlank() || viewModel.keyStoreInfoState.countryCode.isBlank()) {
        viewModel.updateSnackbarVisuals("请检查空项")
        return
    }
    viewModel.createSignature()
}

@Composable
private fun ConfirmPasswordTextField(
    title: String,
    password: String,
    confirmPassword: String,
    confirmPasswordError: Boolean,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = password,
            onValueChange = { keyStorePassword ->
                onPasswordChange(keyStorePassword)
            },
            label = { Text(title, style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = confirmPassword,
            onValueChange = { keyStoreConfirmPassword ->
                onConfirmPasswordChange(keyStoreConfirmPassword)
            },
            label = { Text("确认密码", style = MaterialTheme.typography.labelLarge) },
            isError = confirmPasswordError,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}

