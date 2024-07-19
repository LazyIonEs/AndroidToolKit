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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import model.DarkThemeConfig
import model.DestStoreSize
import model.DestStoreType
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
    Box(modifier = Modifier.padding(top = 20.dp, bottom = 20.dp, end = 14.dp)) {
        LazyColumn {
            item {
                Conventional(viewModel)
            }
            item {
                Spacer(Modifier.size(16.dp))
                ApkSignatureSetUp(viewModel)
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
private fun ApkSignatureSetUp(
    viewModel: MainViewModel
) {
    val userData by viewModel.userData.collectAsState()
    var signerSuffix by mutableStateOf(userData.defaultSignerSuffix)
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
            StringInput(value = signerSuffix,
                label = "签名后缀",
                isError = userData.defaultSignerSuffix.isBlank(),
                onValueChange = { suffix ->
                    signerSuffix = suffix
                    viewModel.saveUserData(userData.copy(defaultSignerSuffix = suffix))
                })
            Spacer(Modifier.size(3.dp))
            Text(
                "签名后缀： Apk签名后输出名称（比如：输入Apk名称为apk_unsign.apk，则输入Apk名称为apk_unsign${userData.defaultSignerSuffix}.apk）",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.labelSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("输出文件重复是否删除重复文件", style = MaterialTheme.typography.bodyLarge)
                    if (!userData.duplicateFileRemoval) {
                        Text(
                            "注意：输出文件重复后无法成功签名，会提示输出文件已存在",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(checked = userData.duplicateFileRemoval,
                    onCheckedChange = { viewModel.saveUserData(userData.copy(duplicateFileRemoval = it)) })
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用文件对齐", style = MaterialTheme.typography.bodyLarge)
                    if (!userData.alignFileSize) {
                        Text(
                            "注意：目标 R+（版本 30 及更高版本）要求已安装 APK 内的文件未压缩存储并在 4 字节边界上对齐",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Switch(checked = userData.alignFileSize,
                    onCheckedChange = { viewModel.saveUserData(userData.copy(alignFileSize = it)) })
            }
        }
    }
}

/**
 * 签名生成设置页
 */
@Composable
private fun KeyStore(viewModel: MainViewModel) {
    val userData by viewModel.userData.collectAsState()
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(2.5f)) {
                    Text("目标密钥类型", style = MaterialTheme.typography.bodyLarge)
                    if (userData.destStoreType == DestStoreType.JKS) {
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
                        DestStoreType.JKS.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Switch(
                        checked = userData.destStoreType == DestStoreType.PKCS12,
                        onCheckedChange = { viewModel.saveUserData(userData.copy(destStoreType = if (it) DestStoreType.PKCS12 else DestStoreType.JKS)) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        DestStoreType.PKCS12.name,
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
                    if (userData.destStoreSize == DestStoreSize.ONE_THOUSAND_TWENTY_FOUR) {
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
                        DestStoreSize.ONE_THOUSAND_TWENTY_FOUR.size.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Switch(
                        checked = userData.destStoreSize == DestStoreSize.TWO_THOUSAND_FORTY_EIGHT,
                        onCheckedChange = { viewModel.saveUserData(userData.copy(destStoreSize = if (it) DestStoreSize.TWO_THOUSAND_FORTY_EIGHT else DestStoreSize.ONE_THOUSAND_TWENTY_FOUR)) },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        DestStoreSize.TWO_THOUSAND_FORTY_EIGHT.size.toString(),
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
    viewModel: MainViewModel
) {
    val userData by viewModel.userData.collectAsState()
    var outputPath by mutableStateOf(userData.defaultOutputPath)
    val outPutError = userData.defaultOutputPath.isNotBlank() && !File(userData.defaultOutputPath).isDirectory
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
            FolderInput(value = outputPath, label = "默认输出路径", isError = outPutError, onValueChange = { path ->
                outputPath = path
                viewModel.saveUserData(userData.copy(defaultOutputPath = path))
            })
            Spacer(Modifier.size(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "外观", modifier = Modifier.padding(start = 24.dp), style = MaterialTheme.typography.bodyLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 62.dp)
                ) {
                    val modeList = listOf(DarkThemeConfig.FOLLOW_SYSTEM, DarkThemeConfig.LIGHT, DarkThemeConfig.DARK)
                    modeList.forEach { theme ->
                        ElevatedFilterChip(modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            selected = userData.darkThemeConfig == theme,
                            onClick = { viewModel.saveUserData(userData.copy(darkThemeConfig = theme)) },
                            label = {
                                Text(
                                    theme.value,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                                )
                            },
                            leadingIcon = if (userData.darkThemeConfig == theme) {
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