package ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.JunkCodeEnum
import toast.ToastModel
import toast.ToastUIState
import vm.MainViewModel
import vm.UIState
import java.io.File

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/1 20:07
 * @Description : 垃圾代码生成页面
 * @Version     : 1.0
 */
@Composable
fun JunkCode(
    viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope
) {
    JunkCodeBox(viewModel, toastState, scope)
    LoadingAnimate(viewModel.junkCodeUIState == UIState.Loading, scope)
    toast(viewModel.junkCodeUIState, toastState, scope)
}

@Composable
fun JunkCodeBox(viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    Card(
        modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        val outputPathError =
            viewModel.junkCodeInfoState.outputPath.isNotBlank() && !File(viewModel.junkCodeInfoState.outputPath).isDirectory
        LazyColumn(
            modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = viewModel.junkCodeInfoState.outputPath,
                    label = "AAR输出路径",
                    isError = outputPathError,
                    onValueChange = { path ->
                        viewModel.updateJunkCodeInfo(JunkCodeEnum.OUTPUT_PATH, path)
                    }
                )
            }
            item {
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.junkCodeInfoState.aarName,
                    label = "AAR 名称",
                    isError = false,
                    realOnly = true,
                    onValueChange = { }
                )
            }
            item {
                Spacer(Modifier.size(8.dp))
                PackageName(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                IntInput(
                    value = viewModel.junkCodeInfoState.packageCount,
                    label = "包的数量",
                    isError = viewModel.junkCodeInfoState.packageCount.isBlank(),
                    onValueChange = { packageCount ->
                        viewModel.updateJunkCodeInfo(JunkCodeEnum.PACKAGE_COUNT, packageCount)
                    }
                )
            }
            item {
                Spacer(Modifier.size(8.dp))
                IntInput(
                    value = viewModel.junkCodeInfoState.activityCountPerPackage,
                    label = "每个包里 activity 的数量",
                    isError = viewModel.junkCodeInfoState.activityCountPerPackage.isBlank(),
                    onValueChange = { activityCountPerPackage ->
                        viewModel.updateJunkCodeInfo(JunkCodeEnum.ACTIVITY_COUNT_PER_PACKAGE, activityCountPerPackage)
                    }
                )
            }
            item {
                Spacer(Modifier.size(8.dp))
                StringInput(
                    value = viewModel.junkCodeInfoState.resPrefix,
                    label = "资源前缀",
                    isError = viewModel.junkCodeInfoState.resPrefix.isBlank(),
                    onValueChange = { resPrefix ->
                        viewModel.updateJunkCodeInfo(JunkCodeEnum.RES_PREFIX, resPrefix)
                    }
                )
            }
            item {
                Spacer(Modifier.size(12.dp))
                Generate(
                    viewModel, outputPathError, toastState, scope
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun PackageName(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(3f),
            value = viewModel.junkCodeInfoState.packageName,
            onValueChange = { packageName ->
                viewModel.updateJunkCodeInfo(JunkCodeEnum.PACKAGE_NAME, packageName)
            },
            label = { Text("包名", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.packageName.isBlank()
        )
        Text(
            ".",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.Bottom).padding(bottom = 3.dp)
        )
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(2f),
            value = viewModel.junkCodeInfoState.suffix,
            onValueChange = { suffix ->
                viewModel.updateJunkCodeInfo(JunkCodeEnum.SUFFIX, suffix)
            },
            label = { Text("后缀", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.suffix.isBlank()
        )
    }
}

@Composable
fun Generate(
    viewModel: MainViewModel, outputPathError: Boolean, toastState: ToastUIState, scope: CoroutineScope
) {
    ElevatedButton(onClick = {
        if (outputPathError) {
            scope.launch {
                toastState.show(ToastModel("请检查Error项", ToastModel.Type.Error))
            }
            return@ElevatedButton
        }
        if (viewModel.junkCodeInfoState.outputPath.isBlank() || viewModel.junkCodeInfoState.packageName.isBlank() || viewModel.junkCodeInfoState.suffix.isBlank() || viewModel.junkCodeInfoState.packageCount.isBlank() || viewModel.junkCodeInfoState.activityCountPerPackage.isEmpty() || viewModel.junkCodeInfoState.resPrefix.isBlank()) {
            scope.launch {
                toastState.show(ToastModel("请检查空项", ToastModel.Type.Error))
            }
            return@ElevatedButton
        }
        viewModel.generateJunkCode()
    }) {
        Text(
            "开始生成", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 48.dp)
        )
    }
}
