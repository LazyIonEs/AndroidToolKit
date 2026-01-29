package org.tool.kit.feature.junk

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.IntInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.aar_name
import org.tool.kit.shared.generated.resources.aar_output_path
import org.tool.kit.shared.generated.resources.check_empty
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.junk_package_name
import org.tool.kit.shared.generated.resources.number_of_activities
import org.tool.kit.shared.generated.resources.number_of_packages
import org.tool.kit.shared.generated.resources.resource_prefix
import org.tool.kit.shared.generated.resources.start_generating
import org.tool.kit.shared.generated.resources.suffix
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
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.junkCodeInfoState.aarName,
                    label = stringResource(Res.string.aar_name),
                    isError = false,
                    realOnly = true,
                    onValueChange = { })
            }
            item {
                Spacer(Modifier.size(8.dp))
                PackageName(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                IntInput(
                    value = viewModel.junkCodeInfoState.packageCount,
                    label = stringResource(Res.string.number_of_packages),
                    isError = viewModel.junkCodeInfoState.packageCount.isBlank(),
                    onValueChange = { packageCount ->
                        viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(packageCount = packageCount))
                    })
            }
            item {
                Spacer(Modifier.size(8.dp))
                IntInput(
                    value = viewModel.junkCodeInfoState.activityCountPerPackage,
                    label = stringResource(Res.string.number_of_activities),
                    isError = viewModel.junkCodeInfoState.activityCountPerPackage.isBlank(),
                    onValueChange = { activityCountPerPackage ->
                        viewModel.updateJunkCodeInfo(
                            viewModel.junkCodeInfoState.copy(
                                activityCountPerPackage = activityCountPerPackage
                            )
                        )
                    })
            }
            item {
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.junkCodeInfoState.resPrefix,
                    label = stringResource(Res.string.resource_prefix),
                    isError = viewModel.junkCodeInfoState.resPrefix.isBlank(),
                    onValueChange = { resPrefix ->
                        viewModel.updateJunkCodeInfo(viewModel.junkCodeInfoState.copy(resPrefix = resPrefix))
                    })
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
private fun Generate(
    viewModel: MainViewModel, outputPathError: Boolean
) {
    Button(onClick = {
        if (outputPathError) {
            viewModel.updateSnackbarVisuals(Res.string.check_error)
            return@Button
        }
        if (viewModel.junkCodeInfoState.outputPath.isBlank() || viewModel.junkCodeInfoState.packageName.isBlank() || viewModel.junkCodeInfoState.suffix.isBlank() || viewModel.junkCodeInfoState.packageCount.isBlank() || viewModel.junkCodeInfoState.activityCountPerPackage.isEmpty() || viewModel.junkCodeInfoState.resPrefix.isBlank()) {
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