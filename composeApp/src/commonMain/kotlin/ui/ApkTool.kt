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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import model.FileSelectorType
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.apk_output_path
import org.tool.kit.composeapp.generated.resources.apktool_app_name
import org.tool.kit.composeapp.generated.resources.apktool_min_sdk_version
import org.tool.kit.composeapp.generated.resources.apktool_package_name
import org.tool.kit.composeapp.generated.resources.apktool_target_sdk_version
import org.tool.kit.composeapp.generated.resources.apktool_version_code
import org.tool.kit.composeapp.generated.resources.apktool_version_name
import org.tool.kit.composeapp.generated.resources.check_empty
import org.tool.kit.composeapp.generated.resources.check_error
import org.tool.kit.composeapp.generated.resources.icon_file
import org.tool.kit.composeapp.generated.resources.start_generating
import vm.MainViewModel
import java.io.File

@Composable
fun ApkTool(viewModel: MainViewModel) {
    ApkToolBox(viewModel)
}

@Composable
private fun ApkToolBox(viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val outputPathError =
            viewModel.apkToolInfoState.outputPath.isNotBlank() && !File(viewModel.apkToolInfoState.outputPath).isDirectory
        val iconFileError =
            viewModel.apkToolInfoState.icon.isNotBlank() && !File(viewModel.apkToolInfoState.icon).isFile
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = viewModel.apkToolInfoState.outputPath,
                    label = stringResource(Res.string.apk_output_path),
                    isError = outputPathError,
                    onValueChange = { path ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(outputPath = path))
                    })
            }
            item {
                Spacer(Modifier.size(8.dp))
                Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 16.dp)) {
                    FileInput(
                        value = viewModel.apkToolInfoState.icon,
                        label = stringResource(Res.string.icon_file),
                        isError = iconFileError,
                        modifier = Modifier.padding(end = 8.dp, bottom = 3.dp),
                        trailingIcon = null,
                        FileSelectorType.IMAGE
                    ) { path ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(icon = path))
                    }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.apkToolInfoState.packageName,
                    label = stringResource(Res.string.apktool_package_name),
                    isError = viewModel.apkToolInfoState.packageName.isBlank(),
                    onValueChange = { packageName ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(packageName = packageName))
                    })
            }
            item {
                Spacer(Modifier.size(8.dp))
                SdkVersionInput(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                VersionInput(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.apkToolInfoState.appName,
                    label = stringResource(Res.string.apktool_app_name),
                    isError = viewModel.apkToolInfoState.appName.isBlank(),
                    onValueChange = { appName ->
                        viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(appName = appName))
                    })
            }
            item {
                Spacer(Modifier.size(12.dp))
                Generate(
                    viewModel, outputPathError, iconFileError
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SdkVersionInput(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = viewModel.apkToolInfoState.targetSdkVersion,
            onValueChange = { targetSdkVersion ->
                if (targetSdkVersion.isEmpty() || targetSdkVersion.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(targetSdkVersion = targetSdkVersion))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_target_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.targetSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = viewModel.apkToolInfoState.minSdkVersion,
            onValueChange = { minSdkVersion ->
                if (minSdkVersion.isEmpty() || minSdkVersion.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(minSdkVersion = minSdkVersion))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_min_sdk_version),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.minSdkVersion.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun VersionInput(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
            value = viewModel.apkToolInfoState.versionCode,
            onValueChange = { versionCode ->
                if (versionCode.isEmpty() || versionCode.matches(pattern)) {
                    viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(versionCode = versionCode))
                }
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_code), style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.versionCode.isBlank(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = viewModel.apkToolInfoState.versionName,
            onValueChange = { versionName ->
                viewModel.updateApkToolInfo(viewModel.apkToolInfoState.copy(versionName = versionName))
            },
            label = {
                Text(
                    text = stringResource(Res.string.apktool_version_name), style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.apkToolInfoState.versionName.isBlank(),
        )
    }
}

@Composable
private fun Generate(
    viewModel: MainViewModel, outputPathError: Boolean, iconFileError: Boolean
) {
    Button(onClick = {
        if (outputPathError || iconFileError) {
            viewModel.updateSnackbarVisuals(Res.string.check_error)
            return@Button
        }
        if (viewModel.apkToolInfoState.outputPath.isBlank() || viewModel.apkToolInfoState.packageName.isBlank() || viewModel.apkToolInfoState.targetSdkVersion.isBlank() || viewModel.apkToolInfoState.minSdkVersion.isBlank() || viewModel.apkToolInfoState.versionCode.isEmpty() || viewModel.apkToolInfoState.versionName.isBlank() || viewModel.apkToolInfoState.appName.isBlank()) {
            viewModel.updateSnackbarVisuals(Res.string.check_empty)
            return@Button
        }
        viewModel.generateApktool()
    }) {
        Text(
            text = stringResource(Res.string.start_generating),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}