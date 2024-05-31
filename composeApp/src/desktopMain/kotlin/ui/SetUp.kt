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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import model.Exterior
import model.StoreSize
import model.StoreType
import org.tool.kit.BuildConfig
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
fun SetUp(viewModel: MainViewModel) {
    val signerSuffixError = viewModel.signerSuffix.isBlank()
    val outPutError = viewModel.outputPath.isNotBlank() && !File(viewModel.outputPath).isDirectory
    Box(modifier = Modifier.padding(top = 20.dp, bottom = 20.dp, end = 14.dp)) {
        LazyColumn {
            item {
                Conventional(viewModel, outPutError)
            }
            item {
                Spacer(Modifier.size(16.dp))
                ApkSignature(viewModel, signerSuffixError)
            }
            item {
                Spacer(Modifier.size(16.dp))
                KeyStore(viewModel)
            }
            item {
                Spacer(Modifier.size(16.dp))
                About()
            }
        }
    }
}

/**
 * APK签名设置页
 */
@Composable
private fun ApkSignature(
    viewModel: MainViewModel, signerSuffixError: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "APK签名",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(20.dp))
            StringInput(value = viewModel.signerSuffix,
                label = "签名后缀",
                isError = signerSuffixError,
                onValueChange = { suffix -> viewModel.updateSignerSuffix(suffix) })
            Spacer(Modifier.size(3.dp))
            Text(
                "签名后缀： Apk签名后输出名称（比如：输入Apk名称为apk_unsign.apk，则输入Apk名称为apk_unsign${viewModel.signerSuffix}.apk）",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("输出文件重复是否删除重复文件", style = MaterialTheme.typography.bodyLarge)
                    if (!viewModel.flagDelete) {
                        Text(
                            "注意：输出文件重复后无法成功签名，会提示输出文件已存在",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(checked = viewModel.flagDelete, onCheckedChange = { viewModel.updateFlagDelete(it) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用文件对齐", style = MaterialTheme.typography.bodyLarge)
                    if (!viewModel.isAlignFileSize) {
                        Text(
                            "注意：目标 R+（版本 30 及更高版本）要求已安装 APK 内的文件未压缩存储并在 4 字节边界上对齐",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(checked = viewModel.isAlignFileSize, onCheckedChange = { viewModel.updateIsAlignFileSize(it) })
            }
        }
    }
}

/**
 * 签名生成设置页
 */
@Composable
private fun KeyStore(viewModel: MainViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "签名生成",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(6.dp))
            // 注释的代码会在后面版本中删除
//            FileInput(
//                value = viewModel.keytool, label = "Keytool可执行文件", isError = keytoolError, FileSelectorType.EXECUTE
//            ) { path ->
//                viewModel.updateKeytoolPath(path)
//            }
//            Spacer(Modifier.size(6.dp))
//            if (isWindows) {
//                Text(
//                    "Keytool可执行文件： 一般位于/JDK 安装目录/bin/keytool",
//                    modifier = Modifier.padding(horizontal = 24.dp),
//                    style = MaterialTheme.typography.labelSmall
//                )
//            } else {
//                Text(
//                    "Keytool可执行文件： 一般位于/JDK 安装目录/Contents/Home/bin/keytool",
//                    modifier = Modifier.padding(horizontal = 24.dp),
//                    style = MaterialTheme.typography.labelSmall
//                )
//            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(2.5f)) {
                    Text("目标密钥类型", style = MaterialTheme.typography.bodyLarge)
                    if (viewModel.destStoreType == StoreType.JKS.value) {
                        Text(
                            text = "注意：JKS 密钥库使用专用格式。建议使用行业标准格式 PKCS12。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        StoreType.JKS.value,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Switch(
                        checked = viewModel.destStoreType == StoreType.PKCS12.value,
                        onCheckedChange = { viewModel.updateDestStoreType(if (it) StoreType.PKCS12 else StoreType.JKS) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        StoreType.PKCS12.value,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(2.5f)) {
                    Text("目标密钥大小", style = MaterialTheme.typography.bodyLarge)
                    if (viewModel.destStoreSize.toInt() == StoreSize.SIZE_1024.value) {
                        Text(
                            text = "注意：生成的证书 使用的 1024 位 RSA 密钥 被视为存在安全风险。此密钥大小将在未来的更新中被禁用。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        StoreSize.SIZE_1024.value.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Switch(
                        checked = viewModel.destStoreSize.toInt() == StoreSize.SIZE_2048.value,
                        onCheckedChange = { viewModel.updateDestStoreSize(if (it) StoreSize.SIZE_2048 else StoreSize.SIZE_1024) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        StoreSize.SIZE_2048.value.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun Conventional(
    viewModel: MainViewModel, outPutError: Boolean
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "常规",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.size(12.dp))
            FolderInput(value = viewModel.outputPath,
                label = "默认输出路径",
                isError = outPutError,
                onValueChange = { path -> viewModel.updateOutputPath(path) })
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "外观", modifier = Modifier.padding(start = 24.dp), style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)
                ) {
                    val modeList = listOf(Exterior.AutoMode, Exterior.LightMode, Exterior.DarkMode)
                    modeList.forEach { exterior ->
                        ElevatedFilterChip(modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            selected = viewModel.darkMode == exterior.mode,
                            onClick = { viewModel.updateDarkMode(exterior.mode) },
                            label = {
                                Text(
                                    exterior.title,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
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
                            })
                    }
                }
            }
        }
    }
}

@Composable
private fun About() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)) {
            Spacer(Modifier.size(4.dp))
            Text(
                "关于",
                modifier = Modifier.padding(horizontal = 16.dp),
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