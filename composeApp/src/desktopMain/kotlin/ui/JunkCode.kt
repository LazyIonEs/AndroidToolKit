package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darkrockstudios.libraries.mpfilepicker.DirectoryPicker
import file.showFolderSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.JunkCodeEnum
import toast.ToastModel
import toast.ToastUIState
import utils.LottieAnimation
import utils.isWindows
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
    when (val uiState = viewModel.junkCodeUIState) {
        UIState.WAIT -> Unit
        UIState.Loading -> {
            Box(
                modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
            ) {
                LottieAnimation(scope, "files/lottie_loading.json")
            }
        }

        is UIState.Success -> scope.launch {
            toastState.show(ToastModel(uiState.result as String, ToastModel.Type.Success))
        }

        is UIState.Error -> scope.launch {
            toastState.show(ToastModel(uiState.msg, ToastModel.Type.Error))
        }
    }
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
                OutputPath(viewModel, outputPathError)
            }
            item {
                Spacer(Modifier.size(8.dp))
                AarName(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                PackageName(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                PackageCount(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                ActivityCountPerPackage(viewModel)
            }
            item {
                Spacer(Modifier.size(8.dp))
                ResPrefix(viewModel)
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
fun OutputPath(viewModel: MainViewModel, outputPathError: Boolean) {
    var showDirPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.junkCodeInfoState.outputPath,
            onValueChange = { path ->
                viewModel.updateJunkCodeInfo(JunkCodeEnum.OUTPUT_PATH, path)
            },
            label = { Text("AAR输出路径", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = outputPathError
        )
        SmallFloatingActionButton(onClick = {
            if (isWindows) {
                showDirPicker = true
            } else {
                showFolderSelector { path ->
                    viewModel.updateJunkCodeInfo(JunkCodeEnum.OUTPUT_PATH, path)
                }
            }
        }) {
            Icon(Icons.Rounded.FolderOpen, "选择文件夹")
        }
    }
    if (isWindows) {
        DirectoryPicker(showDirPicker) { path ->
            showDirPicker = false
            if (path?.isNotBlank() == true) {
                viewModel.updateJunkCodeInfo(JunkCodeEnum.OUTPUT_PATH, path)
            }
        }
    }
}

@Composable
fun AarName(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.junkCodeInfoState.aarName,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(
                    "AAR 名称", style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true
        )
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
fun PackageCount(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+\$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.junkCodeInfoState.packageCount,
            onValueChange = { packageCount ->
                if (packageCount.isEmpty() || packageCount.matches(pattern)) {
                    viewModel.updateJunkCodeInfo(JunkCodeEnum.PACKAGE_COUNT, packageCount)
                }
            },
            label = { Text("包的数量", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.packageCount.isBlank()
        )
    }
}

@Composable
fun ActivityCountPerPackage(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val pattern = remember { Regex("^\\d+\$") }
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.junkCodeInfoState.activityCountPerPackage,
            onValueChange = { activityCountPerPackage ->
                if (activityCountPerPackage.isEmpty() || activityCountPerPackage.matches(pattern)) {
                    viewModel.updateJunkCodeInfo(
                        JunkCodeEnum.ACTIVITY_COUNT_PER_PACKAGE, activityCountPerPackage
                    )
                }
            },
            label = {
                Text(
                    "每个包里 activity 的数量", style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.activityCountPerPackage.isBlank()
        )
    }
}

@Composable
fun ResPrefix(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.junkCodeInfoState.resPrefix,
            onValueChange = { resPrefix ->
                viewModel.updateJunkCodeInfo(
                    JunkCodeEnum.RES_PREFIX, resPrefix
                )
            },
            label = {
                Text(
                    "资源前缀", style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.junkCodeInfoState.resPrefix.isBlank()
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
        if (viewModel.junkCodeInfoState.outputPath.isBlank() || viewModel.junkCodeInfoState.packageName.isBlank() || viewModel.junkCodeInfoState.suffix.isBlank() || viewModel.junkCodeInfoState.packageCount.isBlank() || viewModel.junkCodeInfoState.activityCountPerPackage.isNullOrEmpty() || viewModel.junkCodeInfoState.resPrefix.isBlank()) {
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
