package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Start
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.DragData
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import constant.ConfigConstant
import file.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.Font
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.ZCOOLKuaiLe_Regular
import utils.LottieAnimation
import utils.isImage
import utils.update
import vm.MainViewModel
import vm.UIState
import java.io.File
import java.net.URI

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/8 16:13
 * @Description : 图标工厂
 * @Version     : 1.0
 */
@Composable
fun IconFactory(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val showBottomSheet = remember { mutableStateOf(false) }
    IconFactoryPreview(viewModel, showBottomSheet, scope)
    LoadingAnimate(viewModel.iconFactoryUIState == UIState.Loading, scope)
    IconFactorySheet(viewModel, showBottomSheet, scope)
}

/**
 * 图标生成预览
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconFactoryPreview(
    viewModel: MainViewModel, showBottomSheet: MutableState<Boolean>, scope: CoroutineScope
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val icon = viewModel.iconFactoryInfoState.icon
        var animationEnds by remember { mutableStateOf(icon != null) }
        Column(
            modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                item {
                    Row(
                        modifier = Modifier.animateItemPlacement(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = icon == null,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Column {
                                val fontRegular = FontFamily(Font(Res.font.ZCOOLKuaiLe_Regular))
                                Text(
                                    text = "一键生成应用图标",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontFamily = fontRegular
                                )
                                Spacer(Modifier.size(24.dp))
                                Text(
                                    "支持png、jpg、jpeg文件\n上传 1024 x 1024 像素的图片以获得最佳效果",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.size(48.dp))
                            }
                        }
                    }
                }
                item {
                    Box(
                        modifier = Modifier.animateContentSize(finishedListener = { _, _ ->
                            animationEnds = true
                        }).size(if (icon != null) 192.dp else 256.dp).animateItemPlacement()
                    ) {
                        icon?.let {
                            if (icon.exists()) {
                                val bitmap = loadImageBitmap(icon.inputStream())
                                Crossfade(targetState = bitmap) {
                                    Image(
                                        bitmap = it, contentDescription = "预览图标", modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(icon = null, result = null))
                            }
                        } ?: LottieAnimation(scope, "files/lottie_main_3.json")
                    }
                }
                if (icon != null) {
                    item {
                        Icon(
                            imageVector = Icons.Rounded.Start,
                            contentDescription = "向右",
                            modifier = Modifier.size(48.dp).animateItemPlacement()
                        )
                    }
                    item {
                        Box(modifier = Modifier.animateItemPlacement()) {
                            IconFactoryResult(viewModel)
                        }
                    }
                }
            }
            AnimatedVisibility(animationEnds) {
                Spacer(Modifier.size(24.dp))
                Button({
                    viewModel.iconFactoryInfoState.apply {
                        if (outputPath.isBlank() || fileDir.isBlank() || iconName.isBlank()) {
                            if (!showBottomSheet.value) showBottomSheet.update { true }
                            viewModel.updateSnackbarVisuals("请检查空项")
                            return@Button
                        }
                    }
                    viewModel.iconGeneration(icon?.absolutePath ?: return@Button)
                }) {
                    Text("开始制作")
                }
            }
        }
    }
}

@Composable
private fun IconFactoryResult(viewModel: MainViewModel) {
    Column(
        modifier = Modifier.width(384.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(0), title = "mdpi", size = 48
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(1), title = "hdpi", size = 72
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(2), title = "xhdpi", size = 96
            )
        }
        Spacer(Modifier.size(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(3), title = "xxhdpi", size = 144
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(4), title = "xxxhdpi", size = 192
            )
        }
    }
}

@Composable
private fun IconFactoryResultPlaceholder(resultFile: File?, title: String, size: Int) {
    Crossfade(targetState = resultFile) { file ->
        if (file != null && file.exists()) {
            val bitmap = loadImageBitmap(file.inputStream())
            Image(
                bitmap = bitmap, contentDescription = "预览图标", modifier = Modifier.size(size.dp)
            )
        } else {
            Card(modifier = Modifier.size(size.dp)) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = title, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun IconFactorySheet(viewModel: MainViewModel, showBottomSheet: MutableState<Boolean>, scope: CoroutineScope) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize()
            .onExternalDrag(onDragStart = { dragging = true }, onDragExit = { dragging = false }, onDrop = { state ->
                val dragData = state.dragData
                if (dragData is DragData.FilesList) {
                    dragData.readFiles().firstOrNull()?.let {
                        if (it.isImage) {
                            val file = File(URI.create(it))
                            viewModel.updateIconFactoryInfo(
                                viewModel.iconFactoryInfoState.copy(
                                    icon = file, result = null
                                )
                            )
                        }
                    }
                }
                dragging = false
            })
    ) {
        Column(modifier = Modifier.align(Alignment.BottomEnd)) {
            AnimatedVisibility(
                visible = viewModel.iconFactoryUIState != UIState.Loading, modifier = Modifier.align(Alignment.End)
            ) {
                FileButton(
                    value = if (dragging) {
                        "愣着干嘛，还不松手"
                    } else {
                        "点击选择或拖拽上传图片"
                    },
                    expanded = viewModel.iconFactoryInfoState.icon == null,
                    FileSelectorType.IMAGE
                ) { path ->
                    if (path.isImage) {
                        val file = File(path)
                        viewModel.updateIconFactoryInfo(
                            viewModel.iconFactoryInfoState.copy(
                                icon = file, result = null
                            )
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = viewModel.iconFactoryInfoState.icon != null,
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp).align(Alignment.End)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showBottomSheet.update { true } },
                    expanded = true,
                    icon = { Icon(Icons.Rounded.Tune, "更多设置") },
                    text = { Text("更多设置") })
            }
        }
        if (showBottomSheet.value) {
            ModalBottomSheet(modifier = Modifier.fillMaxHeight().align(Alignment.BottomEnd),
                sheetState = sheetState,
                onDismissRequest = { showBottomSheet.update { false } }) {
                IconFactorySetting(viewModel)
            }
        }
    }
}

@Composable
private fun IconFactorySetting(viewModel: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = 20.dp, end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.size(8.dp))
            FolderInput(value = viewModel.iconFactoryInfoState.outputPath,
                label = "图标输出路径",
                isError = false,
                onValueChange = { path ->
                    viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(outputPath = path))
                })
        }
        item {
            Spacer(Modifier.size(8.dp))
            IconsFactoryInput(viewModel)
        }
        item {
            Spacer(Modifier.size(8.dp))
            StringInput(value = viewModel.iconFactoryInfoState.iconName,
                label = "图标名称",
                isError = viewModel.iconFactoryInfoState.iconName.isBlank(),
                onValueChange = { iconName ->
                    viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconName = iconName))
                })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconsFactoryInput(viewModel: MainViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = viewModel.iconFactoryInfoState.fileDir,
            onValueChange = { fileDir ->
                viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(fileDir = fileDir))
            },
            label = { Text("外部目录名称", style = MaterialTheme.typography.labelLarge) },
            singleLine = true,
            isError = viewModel.iconFactoryInfoState.fileDir.isBlank(),
        )
        var expanded by remember { mutableStateOf(false) }
        val options = ConfigConstant.ANDROID_ICON_DIR_LIST
        ExposedDropdownMenuBox(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            expanded = expanded,
            onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                value = viewModel.iconFactoryInfoState.iconDir,
                onValueChange = { iconDir ->
                    viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconDir = iconDir))
                },
                label = { Text("Android目录", style = MaterialTheme.typography.labelLarge) },
                isError = viewModel.iconFactoryInfoState.iconDir.isBlank(),
                singleLine = true,
                readOnly = true,
                trailingIcon = { TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(text = selectionOption, style = MaterialTheme.typography.labelLarge) },
                        onClick = {
                            viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconDir = selectionOption))
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
    }
}