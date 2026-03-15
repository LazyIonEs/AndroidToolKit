package org.tool.kit.feature.junk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.IntInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.model.JunkMode
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.aar_name
import org.tool.kit.shared.generated.resources.aar_output_path
import org.tool.kit.shared.generated.resources.check_empty
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.estimated_size
import org.tool.kit.shared.generated.resources.junk_package_name
import org.tool.kit.shared.generated.resources.number_of_activities
import org.tool.kit.shared.generated.resources.number_of_packages
import org.tool.kit.shared.generated.resources.resource_prefix
import org.tool.kit.shared.generated.resources.start_generating
import org.tool.kit.shared.generated.resources.suffix
import org.tool.kit.utils.generateSecureToken
import org.tool.kit.vm.MainViewModel
import java.io.File

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/1 20:07
 * @Description : 垃圾代码生成页面
 * @Version     : 1.0
 */
@Composable
fun JunkCode(viewModel: MainViewModel) {
    JunkCodeBox(viewModel)
}

@Composable
private fun JunkCodeBox(viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val outputPathError =
            viewModel.junkCodeInfoState.outputPath.isNotBlank() && !File(viewModel.junkCodeInfoState.outputPath).isDirectory
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = viewModel.junkCodeInfoState.outputPath,
                    label = stringResource(Res.string.aar_output_path),
                    isError = outputPathError,
                    onValueChange = { path ->
                        viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(outputPath = path))
                    })
            }
            item {
                Spacer(Modifier.size(6.dp))
                JunkMode(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                val currentJunkMode by viewModel.junkMode.collectAsState()
                AnimatedContent(currentJunkMode) { junkMode ->
                    when (junkMode) {
                        JunkMode.SINGLE -> SingleUi(viewModel)
                        JunkMode.MULTI -> MultiUi(viewModel)
                    }
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Generate(
                    viewModel, outputPathError
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SingleUi(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StringInput(
            value = viewModel.junkCodeInfoState.aarName,
            label = stringResource(Res.string.aar_name),
            isError = false,
            realOnly = true,
            onValueChange = { })
        Spacer(Modifier.size(8.dp))
        PackageName(viewModel)
        Spacer(Modifier.size(8.dp))
        StringInput(
            value = viewModel.junkCodeInfoState.resPrefix,
            label = stringResource(Res.string.resource_prefix),
            isError = viewModel.junkCodeInfoState.resPrefix.isBlank(),
            trailingIcon = {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.clickable {
                        viewModel.updateJunkCodeInfo(
                            viewModel.junkCodeInfoState.copy(
                                resPrefix = generateSecureToken(2, 6) + "_"
                            )
                        )
                    })
            },
            onValueChange = { resPrefix ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(resPrefix = resPrefix))
            })
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = viewModel.junkCodeInfoState.packageCount,
            value2 = viewModel.junkCodeInfoState.activityCountPerPackage,
            label1 = stringResource(Res.string.number_of_packages),
            label2 = stringResource(Res.string.number_of_activities),
            isError1 = viewModel.junkCodeInfoState.packageCount.isBlank(),
            isError2 = viewModel.junkCodeInfoState.activityCountPerPackage.isBlank(),
            onValue1Change = { packageCount ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(packageCount = packageCount))
            },
            onValue2Change = { activityCountPerPackage ->
                viewModel.updateJunkCodeInfo(
                    viewModel.junkCodeInfoState.copy(
                        activityCountPerPackage = activityCountPerPackage
                    )
                )
            }
        )
    }
}

@Composable
private fun MultiUi(viewModel: MainViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StringInput(
            value = viewModel.junkCodeInfoState.outputDir,
            label = "多AAR输出文件夹名称",
            isError = viewModel.junkCodeInfoState.outputDir.isBlank(),
            onValueChange = { outputDir ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(outputDir = outputDir))
            })
        Spacer(Modifier.size(8.dp))
        IntInput(
            value = viewModel.junkCodeInfoState.aarCount,
            label = "需要生成的AAR包数量",
            isError = viewModel.junkCodeInfoState.aarCount.isBlank(),
            onValueChange = { aarCount ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(aarCount = aarCount))
            }
        )
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = viewModel.junkCodeInfoState.leastPackageCount,
            value2 = viewModel.junkCodeInfoState.maximumPackageCount,
            label1 = "包数量（最小）",
            label2 = "包数量（最大）",
            isError1 = viewModel.junkCodeInfoState.leastPackageCount.isBlank(),
            isError2 = viewModel.junkCodeInfoState.maximumPackageCount.isBlank(),
            onValue1Change = { leastPackageCount ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(leastPackageCount = leastPackageCount))
            },
            onValue2Change = { maximumPackageCount ->
                viewModel.updateJunkCodeInfo(
                    viewModel.junkCodeInfoState.copy(
                        maximumPackageCount = maximumPackageCount
                    )
                )
            }
        )
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = viewModel.junkCodeInfoState.leastActivityCountPerPackage,
            value2 = viewModel.junkCodeInfoState.maximumActivityCountPerPackage,
            label1 = "每个包里 activity 的数量（最小）",
            label2 = "每个包里 activity 的数量（最大）",
            isError1 = viewModel.junkCodeInfoState.leastActivityCountPerPackage.isBlank(),
            isError2 = viewModel.junkCodeInfoState.maximumActivityCountPerPackage.isBlank(),
            onValue1Change = { leastActivityCountPerPackage ->
                viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(leastActivityCountPerPackage = leastActivityCountPerPackage))
            },
            onValue2Change = { maximumActivityCountPerPackage ->
                viewModel.updateJunkCodeInfo(
                    viewModel.junkCodeInfoState.copy(
                        maximumActivityCountPerPackage = maximumActivityCountPerPackage
                    )
                )
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JunkMode(viewModel: MainViewModel) {
    val junkModeList = JunkMode.entries
    val currentJunkMode by viewModel.junkMode.collectAsState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 68.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        junkModeList.forEachIndexed { index, mode ->
            ToggleButton(
                checked = mode == currentJunkMode,
                onCheckedChange = {
                    if (mode != currentJunkMode) {
                        viewModel.saveJunkMode(mode)
                    }
                },
                colors = ToggleButtonDefaults.elevatedToggleButtonColors(),
                modifier = Modifier.weight(1f),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        junkModeList.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                AnimatedVisibility(mode == currentJunkMode) {
                    Row {
                        Icon(
                            imageVector = Icons.Rounded.Done,
                            contentDescription = "Done icon",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(text = mode.title)
            }
        }
    }
}

@Composable
private fun PackageName(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(3f),
            value = viewModel.junkCodeInfoState.packageName,
            onValueChange = { packageName ->
                val junkCodeInfo = viewModel.junkCodeInfoState.copy()
                junkCodeInfo.packageName = packageName
                viewModel.updateJunkCodeInfo(junkCodeInfo)
            },
            label = {
                Text(
                    text = stringResource(Res.string.junk_package_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.packageName.isBlank()
        )
        Text(
            ".",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(2f),
            value = viewModel.junkCodeInfoState.suffix,
            onValueChange = { suffix ->
                val junkCodeInfo = viewModel.junkCodeInfoState.copy()
                junkCodeInfo.suffix = suffix
                viewModel.updateJunkCodeInfo(junkCodeInfo)
            },
            trailingIcon = {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.clickable {
                        val junkCodeInfo = viewModel.junkCodeInfoState.copy()
                        junkCodeInfo.suffix = generateSecureToken(3, 8)
                        viewModel.updateJunkCodeInfo(junkCodeInfo)
                    })
            },
            label = {
                Text(
                    text = stringResource(Res.string.suffix),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.suffix.isBlank()
        )
    }
}

@Composable
private fun MultiIntTextField(
    value1: String, value2: String,
    label1: String, label2: String,
    isError1: Boolean, isError2: Boolean,
    onValue1Change: (String) -> Unit, onValue2Change: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = value1,
            onValueChange = { value1 ->
                if (value1.isEmpty() || value1.matches(pattern)) {
                    onValue1Change.invoke(value1)
                }
            },
            label = {
                Text(
                    label1,
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = isError1,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = value2,
            onValueChange = { value2 ->
                if (value2.isEmpty() || value2.matches(pattern)) {
                    onValue2Change.invoke(value2)
                }
            },
            label = {
                Text(
                    label2,
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = isError2,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

@Composable
private fun Generate(
    viewModel: MainViewModel, outputPathError: Boolean
) {
    val currentJunkMode by viewModel.junkMode.collectAsState()
    val estimateSize = viewModel.junkCodeInfoState.estimateAarSize(currentJunkMode)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(end = 72.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = stringResource(Res.string.estimated_size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 10.sp
            )
            Text(
                text = estimateSize,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        VerticalDivider(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
        )
        Button(onClick = {
            if (outputPathError) {
                viewModel.updateSnackbarVisuals(Res.string.check_error)
                return@Button
            }
            if (viewModel.junkCodeInfoState.outputPath.isBlank() || viewModel.junkCodeInfoState.packageName.isBlank() || viewModel.junkCodeInfoState.suffix.isBlank() || viewModel.junkCodeInfoState.packageCount.isBlank() || viewModel.junkCodeInfoState.activityCountPerPackage.isEmpty() || viewModel.junkCodeInfoState.resPrefix.isBlank()) {
                viewModel.updateSnackbarVisuals(Res.string.check_empty)
                return@Button
            }
            if (viewModel.junkCodeInfoState.outputDir.isBlank() || viewModel.junkCodeInfoState.aarCount.isBlank() || viewModel.junkCodeInfoState.leastPackageCount.isBlank() || viewModel.junkCodeInfoState.maximumPackageCount.isBlank() || viewModel.junkCodeInfoState.leastActivityCountPerPackage.isEmpty() || viewModel.junkCodeInfoState.maximumActivityCountPerPackage.isBlank()) {
                viewModel.updateSnackbarVisuals(Res.string.check_empty)
                return@Button
            }
            viewModel.generateJunkCode()
        }) {
            Text(
                text = stringResource(Res.string.start_generating),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}