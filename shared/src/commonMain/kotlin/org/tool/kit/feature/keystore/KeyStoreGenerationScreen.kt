package org.tool.kit.feature.keystore

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.IntInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.certificate
import org.tool.kit.shared.generated.resources.city_or_locality
import org.tool.kit.shared.generated.resources.confirm_password
import org.tool.kit.shared.generated.resources.country_code
import org.tool.kit.shared.generated.resources.create_key_store
import org.tool.kit.shared.generated.resources.first_and_last_name
import org.tool.kit.shared.generated.resources.key
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_alias_password
import org.tool.kit.shared.generated.resources.key_file_name
import org.tool.kit.shared.generated.resources.key_output_path
import org.tool.kit.shared.generated.resources.key_password
import org.tool.kit.shared.generated.resources.organization
import org.tool.kit.shared.generated.resources.organizational_unit
import org.tool.kit.shared.generated.resources.state_or_province
import org.tool.kit.shared.generated.resources.validity_period_unit_year

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/1 15:28
 * @Description : 展示密钥库生成表单和密码确认错误，通过回调提交字段与生成操作
 * @Version     : 1.0
 */
@Composable
fun KeyStoreGenerationScreen(
    state: KeyStoreGenerationUiState,
    onIntent: (KeyStoreGenerationIntent) -> Unit,
    onPickOutput: () -> Unit,
) {
    GenerationBox(state.form, state.validation, onIntent, onPickOutput)
}

@Composable
private fun GenerationBox(
    form: KeyStoreForm,
    validation: KeyStoreValidation,
    onIntent: (KeyStoreGenerationIntent) -> Unit,
    onPickOutput: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = form.keyStorePath,
                    label = stringResource(Res.string.key_output_path),
                    isError = validation.outputPathError,
                    onPickerRequest = onPickOutput
                ) { path ->
                    onIntent(KeyStoreGenerationIntent.OutputPathChanged(path))
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                StringInput(
                    value = form.keyStoreName,
                    label = stringResource(Res.string.key_file_name),
                    isError = validation.fileNameError
                ) { name ->
                    onIntent(KeyStoreGenerationIntent.FileNameChanged(name))
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStorePassword(form.keyStorePassword, form.keyStoreConfirmPassword, validation.storeConfirmationError,
                    { onIntent(KeyStoreGenerationIntent.StorePasswordChanged(it)) },
                    { onIntent(KeyStoreGenerationIntent.StoreConfirmationChanged(it)) })
            }
            item {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.key),
                        style = MaterialTheme.typography.titleSmall
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 8.dp), thickness = 2.dp)
                }
                Spacer(Modifier.size(12.dp))
                StringInput(
                    value = form.keyStoreAlisa,
                    label = stringResource(Res.string.key_alias),
                    isError = false
                ) { name ->
                    onIntent(KeyStoreGenerationIntent.AliasChanged(name))
                }
            }
            item {
                Spacer(Modifier.size(4.dp))
                KeyStoreAlisaPassword(form.keyStoreAlisaPassword, form.keyStoreAlisaConfirmPassword, validation.aliasConfirmationError,
                    { onIntent(KeyStoreGenerationIntent.AliasPasswordChanged(it)) },
                    { onIntent(KeyStoreGenerationIntent.AliasConfirmationChanged(it)) })
            }
            item {
                Spacer(Modifier.size(4.dp))
                IntInput(
                    value = form.validityPeriod,
                    label = stringResource(Res.string.validity_period_unit_year),
                    isError = form.validityPeriod.isBlank()
                ) { validityPeriod ->
                    onIntent(KeyStoreGenerationIntent.ValidityChanged(validityPeriod))
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.certificate),
                        style = MaterialTheme.typography.titleSmall
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 8.dp), thickness = 2.dp)
                }
                Spacer(Modifier.size(12.dp))
                StringInput(
                    value = form.authorName,
                    label = stringResource(Res.string.first_and_last_name),
                    isError = false
                ) { authorName ->
                    onIntent(KeyStoreGenerationIntent.AuthorNameChanged(authorName))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = form.organizationalUnit,
                    label = stringResource(Res.string.organizational_unit),
                    isError = false
                ) { organizationalUnit ->
                    onIntent(KeyStoreGenerationIntent.OrganizationalUnitChanged(organizationalUnit))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = form.organizational,
                    label = stringResource(Res.string.organization),
                    isError = false
                ) { organizational ->
                    onIntent(KeyStoreGenerationIntent.OrganizationChanged(organizational))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = form.city,
                    label = stringResource(Res.string.city_or_locality),
                    isError = false
                ) { city ->
                    onIntent(KeyStoreGenerationIntent.CityChanged(city))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = form.province,
                    label = stringResource(Res.string.state_or_province),
                    isError = false
                ) { province ->
                    onIntent(KeyStoreGenerationIntent.ProvinceChanged(province))
                }
            }
            item {
                Spacer(Modifier.size(2.dp))
                StringInput(
                    value = form.countryCode,
                    label = stringResource(Res.string.country_code),
                    isError = false
                ) { countryCode ->
                    onIntent(KeyStoreGenerationIntent.CountryCodeChanged(countryCode))
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                CreateSignature { onIntent(KeyStoreGenerationIntent.Submit) }
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
    password: String, confirmation: String, confirmationError: Boolean,
    onPasswordChange: (String) -> Unit, onConfirmationChange: (String) -> Unit,
) {
    ConfirmPasswordTextField(
        title = stringResource(Res.string.key_password),
        password = password,
        confirmPassword = confirmation,
        confirmPasswordError = confirmationError,
        onPasswordChange = onPasswordChange,
        onConfirmPasswordChange = onConfirmationChange)
}

/**
 * 别名密码
 */
@Composable
private fun KeyStoreAlisaPassword(
    password: String, confirmation: String, confirmationError: Boolean,
    onPasswordChange: (String) -> Unit, onConfirmationChange: (String) -> Unit,
) {
    ConfirmPasswordTextField(
        title = stringResource(Res.string.key_alias_password),
        password = password,
        confirmPassword = confirmation,
        confirmPasswordError = confirmationError,
        onPasswordChange = onPasswordChange,
        onConfirmPasswordChange = onConfirmationChange)
}

/**
 * 创建签名按钮
 */
@Composable
private fun CreateSignature(onSubmit: () -> Unit) {
    Button(onClick = onSubmit) {
        Text(
            text = stringResource(Res.string.create_key_store),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
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
            label = { Text(text = title, style = MaterialTheme.typography.labelLarge) },
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
            label = {
                Text(
                    text = stringResource(Res.string.confirm_password),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            isError = confirmPasswordError,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
    }
}
