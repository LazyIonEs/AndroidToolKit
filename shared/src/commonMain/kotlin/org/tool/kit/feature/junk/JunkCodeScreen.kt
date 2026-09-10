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
import org.tool.kit.shared.generated.resources.estimated_size
import org.tool.kit.shared.generated.resources.junk_package_name
import org.tool.kit.shared.generated.resources.number_of_activities
import org.tool.kit.shared.generated.resources.number_of_packages
import org.tool.kit.shared.generated.resources.resource_prefix
import org.tool.kit.shared.generated.resources.start_generating
import org.tool.kit.shared.generated.resources.suffix

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/1 20:07
 * @Description : 垃圾代码生成页面
 * @Version     : 1.0
 */
@Composable
fun JunkCodeScreen(state: JunkCodeUiState, onIntent: (JunkCodeIntent) -> Unit, pickOutput: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = state.outputPath,
                    label = stringResource(Res.string.aar_output_path),
                    isError = state.outputValidation.isError,
                    onPickerRequest = pickOutput,
                    onValueChange = { path ->
                        onIntent(JunkCodeIntent.OutputPathChanged(path))
                    })
            }
            item {
                Spacer(Modifier.size(6.dp))
                JunkMode(state.mode, onIntent)
            }
            item {
                Spacer(Modifier.size(8.dp))
                AnimatedContent(state.mode) { junkMode ->
                    when (junkMode) {
                        JunkMode.SINGLE -> SingleUi(state.single, onIntent)
                        JunkMode.MULTI -> MultiUi(state.multi, onIntent)
                    }
                }
            }
            item {
                Spacer(Modifier.size(12.dp))
                Generate(state.estimatedSize) { onIntent(JunkCodeIntent.Submit) }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SingleUi(form: SingleJunkForm, onIntent: (JunkCodeIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StringInput(
            value = form.aarName,
            label = stringResource(Res.string.aar_name),
            isError = false,
            realOnly = true,
            onValueChange = { })
        Spacer(Modifier.size(8.dp))
        PackageName(form, onIntent)
        Spacer(Modifier.size(8.dp))
        StringInput(
            value = form.resPrefix,
            label = stringResource(Res.string.resource_prefix),
            isError = form.resPrefix.isBlank(),
            trailingIcon = {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.clickable {
                        onIntent(JunkCodeIntent.RandomPrefix)
                    })
            },
            onValueChange = { resPrefix ->
                onIntent(JunkCodeIntent.ResPrefixChanged(resPrefix))
            })
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = form.packageCount,
            value2 = form.activityCountPerPackage,
            label1 = stringResource(Res.string.number_of_packages),
            label2 = stringResource(Res.string.number_of_activities),
            isError1 = form.packageCount.isBlank(),
            isError2 = form.activityCountPerPackage.isBlank(),
            onValue1Change = { packageCount ->
                onIntent(JunkCodeIntent.PackageCountChanged(packageCount))
            },
            onValue2Change = { activityCountPerPackage ->
                onIntent(JunkCodeIntent.ActivityCountChanged(activityCountPerPackage))
            }
        )
    }
}

@Composable
private fun MultiUi(form: MultiJunkForm, onIntent: (JunkCodeIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StringInput(
            value = form.outputDir,
            label = "多AAR输出文件夹名称",
            isError = form.outputDir.isBlank(),
            onValueChange = { outputDir ->
                onIntent(JunkCodeIntent.OutputDirChanged(outputDir))
            })
        Spacer(Modifier.size(8.dp))
        IntInput(
            value = form.aarCount,
            label = "需要生成的AAR包数量",
            isError = form.aarCount.isBlank(),
            onValueChange = { aarCount ->
                onIntent(JunkCodeIntent.AarCountChanged(aarCount))
            }
        )
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = form.leastPackageCount,
            value2 = form.maximumPackageCount,
            label1 = "包数量（最小）",
            label2 = "包数量（最大）",
            isError1 = form.leastPackageCount.isBlank(),
            isError2 = form.maximumPackageCount.isBlank(),
            onValue1Change = { leastPackageCount ->
                onIntent(JunkCodeIntent.LeastPackagesChanged(leastPackageCount))
            },
            onValue2Change = { maximumPackageCount ->
                onIntent(JunkCodeIntent.MaximumPackagesChanged(maximumPackageCount))
            }
        )
        Spacer(Modifier.size(8.dp))
        MultiIntTextField(
            value1 = form.leastActivityCountPerPackage,
            value2 = form.maximumActivityCountPerPackage,
            label1 = "每个包里 activity 的数量（最小）",
            label2 = "每个包里 activity 的数量（最大）",
            isError1 = form.leastActivityCountPerPackage.isBlank(),
            isError2 = form.maximumActivityCountPerPackage.isBlank(),
            onValue1Change = { leastActivityCountPerPackage ->
                onIntent(JunkCodeIntent.LeastActivitiesChanged(leastActivityCountPerPackage))
            },
            onValue2Change = { maximumActivityCountPerPackage ->
                onIntent(JunkCodeIntent.MaximumActivitiesChanged(maximumActivityCountPerPackage))
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JunkMode(currentJunkMode: JunkMode, onIntent: (JunkCodeIntent) -> Unit) {
    val junkModeList = JunkMode.entries
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
                        onIntent(JunkCodeIntent.ModeChanged(mode))
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
private fun PackageName(form: SingleJunkForm, onIntent: (JunkCodeIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(3f),
            value = form.packageName,
            onValueChange = { packageName ->
                onIntent(JunkCodeIntent.PackageNameChanged(packageName))
            },
            label = {
                Text(
                    text = stringResource(Res.string.junk_package_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.packageName.isBlank()
        )
        Text(
            ".",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(2f),
            value = form.suffix,
            onValueChange = { suffix ->
                onIntent(JunkCodeIntent.SuffixChanged(suffix))
            },
            trailingIcon = {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    modifier = Modifier.clickable {
                        onIntent(JunkCodeIntent.RandomSuffix)
                    })
            },
            label = {
                Text(
                    text = stringResource(Res.string.suffix),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.suffix.isBlank()
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
private fun Generate(estimateSize: String, onSubmit: () -> Unit) {
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
        Button(onClick = onSubmit) {
            Text(
                text = stringResource(Res.string.start_generating),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
        }
    }
}
