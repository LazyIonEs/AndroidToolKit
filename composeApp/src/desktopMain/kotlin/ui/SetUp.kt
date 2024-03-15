package ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import file.showExecuteSelector
import file.showFolderSelector
import model.Exterior
import model.StoreType
import org.apk.tools.BuildConfig
import utils.isWindows
import vm.MainViewModel
import java.awt.Desktop
import java.io.File

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/21 11:24
 * @Description : 设置页面
 * @Version     : 1.0
 */
@Composable
fun SetUp(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val aaptFile = File(viewModel.aapt)
    val keytoolFile = File(viewModel.keytool)
    val isAaptError =
        viewModel.aapt.isNotBlank() && !aaptFile.isFile && (!aaptFile.canExecute() || aaptFile.isDirectory)
    val isKeytoolError =
        viewModel.keytool.isNotBlank() && !keytoolFile.isFile && (!keytoolFile.canExecute() || keytoolFile.isDirectory)
    val isSignerSuffixError = viewModel.signerSuffix.isBlank()
    val isOutPutError = viewModel.outputPath.isNotBlank() && !File(viewModel.outputPath).isDirectory
    Box(modifier = modifier.padding(top = 20.dp, bottom = 20.dp, end = 14.dp)) {
        LazyColumn {
            item { ApkInformation(modifier, viewModel, isAaptError) }
            item {
                Spacer(Modifier.size(16.dp))
                ApkSignature(modifier, viewModel, isSignerSuffixError)
            }
            item {
                Spacer(Modifier.size(16.dp))
                KeyStore(modifier, viewModel, isKeytoolError)
            }
            item {
                Spacer(Modifier.size(16.dp))
                Conventional(modifier, viewModel, isOutPutError)
            }
            item {
                Spacer(Modifier.size(16.dp))
                About(modifier)
            }
        }
    }
}

@Composable
private fun ApkInformation(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    isAaptError: Boolean
) {
    Card(modifier.fillMaxWidth()) {
        Column(modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "APK信息",
                modifier = modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            var showFilePickerApk by remember { mutableStateOf(false) }
            Row(
                modifier = modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
                    value = viewModel.aapt,
                    onValueChange = { path ->
                        viewModel.updateAaptPath(path)
                    },
                    label = { Text("AAPT可执行文件", style = MaterialTheme.typography.labelLarge) },
                    singleLine = true,
                    isError = isAaptError
                )
                SmallFloatingActionButton(
                    onClick = {
                        if (isWindows) {
                            showFilePickerApk = true
                        } else {
                            showExecuteSelector { path ->
                                viewModel.updateAaptPath(path)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.FolderOpen, "选择文件")
                }
                Spacer(Modifier.size(4.dp))
                SmallFloatingActionButton(
                    onClick = { viewModel.useInternalAaptPath() }
                ) {
                    Icon(Icons.Rounded.Restore, "重置")
                }
            }
            if (isWindows) {
                Text(
                    "AAPT可执行文件： 一般位于/Android Studio SDK 安装目录/sdk/build-tools/版本号/aapt",
                    modifier = modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    "AAPT可执行文件： 一般位于/Users/用户名/Library/Android/sdk/build-tools/版本号/aapt",
                    modifier = modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (isWindows) {
                FilePicker(
                    show = showFilePickerApk,
                    fileExtensions = listOf("exe")
                ) { platformFile ->
                    showFilePickerApk = false
                    if (platformFile?.path?.isNotBlank() == true && File(platformFile.path).canExecute()) {
                        viewModel.updateAaptPath(platformFile.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkSignature(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    isSignerSuffixError: Boolean
) {
    Card(modifier.fillMaxWidth()) {
        Column(modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "APK签名",
                modifier = modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedTextField(
                modifier = modifier.fillMaxWidth()
                    .padding(start = 24.dp, end = 72.dp, top = 20.dp, bottom = 6.dp),
                value = viewModel.signerSuffix,
                onValueChange = { signerSuffix ->
                    viewModel.updateSignerSuffix(signerSuffix)
                },
                label = { Text("签名后缀", style = MaterialTheme.typography.labelLarge) },
                isError = isSignerSuffixError,
                singleLine = true
            )
            Text(
                "签名后缀： Apk签名后输出名称（比如：输入Apk名称为apk_unsign.apk，则输入Apk名称为apk_unsign${viewModel.signerSuffix}.apk）",
                modifier = modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = modifier.weight(1f)) {
                    Text("输出文件重复是否删除重复文件", style = MaterialTheme.typography.bodyLarge)
                    if (!viewModel.flagDelete) {
                        Text(
                            "注意：输出文件重复后无法成功签名，会提示输出文件已存在",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(
                    checked = viewModel.flagDelete,
                    onCheckedChange = { viewModel.updateFlagDelete(it) }
                )
            }
            Row(
                modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = modifier.weight(1f)) {
                    Text("启用文件对齐", style = MaterialTheme.typography.bodyLarge)
                    if (!viewModel.isAlignFileSize) {
                        Text(
                            "注意：当未启用文件对齐，签名之后对APK做出了进一步更改，签名便会失效",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(
                    checked = viewModel.isAlignFileSize,
                    onCheckedChange = { viewModel.updateIsAlignFileSize(it) }
                )
            }
        }
    }
}

@Composable
fun KeyStore(modifier: Modifier, viewModel: MainViewModel, isKeytoolError: Boolean) {
    Card(modifier.fillMaxWidth()) {
        Column(modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "签名生成",
                modifier = modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            var showFilePickerApk by remember { mutableStateOf(false) }
            Row(
                modifier = modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
                    value = viewModel.keytool,
                    onValueChange = { path ->
                        viewModel.updateKeytoolPath(path)
                    },
                    label = {
                        Text(
                            "Keytool可执行文件",
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    singleLine = true,
                    isError = isKeytoolError
                )
                SmallFloatingActionButton(
                    onClick = {
                        if (isWindows) {
                            showFilePickerApk = true
                        } else {
                            showExecuteSelector { path ->
                                viewModel.updateKeytoolPath(path)
                            }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.FolderOpen, "选择文件")
                }
            }
            if (isWindows) {
                Text(
                    "Keytool可执行文件： 一般位于/JDK 安装目录/bin/keytool",
                    modifier = modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            } else {
                Text(
                    "Keytool可执行文件： 一般位于/JDK 安装目录/Contents/Home/bin/keytool",
                    modifier = modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(
                modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = modifier.weight(1f)) {
                    Text("目标密钥类型", style = MaterialTheme.typography.bodyLarge)
                }
                Text(StoreType.JKS.value, style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = viewModel.destStoreType == StoreType.PKCS12.value,
                    onCheckedChange = { viewModel.updateDestStoreType(if (it) StoreType.PKCS12 else StoreType.JKS) },
                    modifier = modifier.padding(horizontal = 12.dp)
                )
                Text(StoreType.PKCS12.value, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = if (viewModel.destStoreType == StoreType.JKS.value) {
                    "注意：JKS 密钥库使用专用格式。使用的 1024 位 RSA 密钥 被视为存在安全风险。此密钥大小将在未来的更新中被禁用。建议使用行业标准格式 PKCS12。"
                } else {
                    "注意：PKCS12 密钥库使用专用格式。该类型不支持其他存储和密钥口令，会忽略用户指定的别名密码（-keypass）。别名密码将与密钥密码保持一致。"
                },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = modifier.padding(start = 24.dp, end = 16.dp)
            )
            if (isWindows) {
                FilePicker(
                    show = showFilePickerApk,
                    fileExtensions = listOf("exe")
                ) { platformFile ->
                    showFilePickerApk = false
                    if (platformFile?.path?.isNotBlank() == true && File(platformFile.path).canExecute()) {
                        viewModel.updateKeytoolPath(platformFile.path)
                    }
                }
            }
        }
    }
}

@Composable
private fun Conventional(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    isOutPutError: Boolean
) {
    Card(modifier.fillMaxWidth()) {
        Column(modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "常规",
                modifier = modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            var showDirPicker by remember { mutableStateOf(false) }
            Row(
                modifier = modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
                    value = viewModel.outputPath,
                    onValueChange = { path ->
                        viewModel.updateOutputPath(path)
                    },
                    label = { Text("默认输出路径", style = MaterialTheme.typography.labelLarge) },
                    singleLine = true,
                    isError = isOutPutError
                )
                SmallFloatingActionButton(
                    onClick = {
                        if (isWindows) {
                            showDirPicker = true
                        } else {
                            showFolderSelector { path ->
                                viewModel.updateOutputPath(path)
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
                        viewModel.updateOutputPath(path)
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "外观",
                    modifier = modifier.padding(start = 24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)) {
                    val modeList = listOf(Exterior.AutoMode, Exterior.LightMode, Exterior.DarkMode)
                    modeList.forEach { exterior ->
                        ElevatedFilterChip(
                            modifier = modifier.weight(1f).padding(horizontal = 8.dp),
                            selected = viewModel.darkMode == exterior.mode,
                            onClick = { viewModel.updateDarkMode(exterior.mode) },
                            label = {
                                Text(
                                    exterior.title,
                                    textAlign = TextAlign.End,
                                    modifier = modifier.fillMaxWidth().padding(8.dp)
                                )
                            },
                            leadingIcon = if (viewModel.darkMode == exterior.mode) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Done,
                                        contentDescription = "Done icon",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun About(modifier: Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "关于",
                modifier = modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("应用名称", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_NAME, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("应用版本", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_VERSION, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("应用描述", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_DESCRIPTION, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("应用版权", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_COPYRIGHT, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("应用作者", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_VENDOR, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.size(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("开源协议", style = MaterialTheme.typography.bodyLarge)
                Text(BuildConfig.APP_LICENSE, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { Desktop.getDesktop().browse(BuildConfig.APP_GITHUB_URI) },
                ) {
                    Text("GitHub")
                }
                Button(
                    onClick = { Desktop.getDesktop().browse(BuildConfig.AUTHOR_GITHUB_URI) },
                ) {
                    Text("Author")
                }
                Button(
                    onClick = { Desktop.getDesktop().browse(BuildConfig.APP_LICENSE_URI) },
                ) {
                    Text("License")
                }
            }
        }
    }
}