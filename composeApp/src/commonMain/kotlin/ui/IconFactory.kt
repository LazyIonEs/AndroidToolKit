package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberPlainTooltipPositionProvider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import constant.ConfigConstant
import model.FileSelectorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.DarkThemeConfig
import model.IconFactoryData
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.tool.kit.composeapp.generated.resources.Res
import org.tool.kit.composeapp.generated.resources.ZCOOLKuaiLe_Regular
import utils.LottieAnimation
import utils.isImage
import utils.update
import vm.MainViewModel
import vm.UIState
import java.io.File
import kotlin.io.path.pathString
import kotlin.math.roundToInt

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
    LoadingAnimate(viewModel.iconFactoryUIState == UIState.Loading, viewModel, scope)
    IconFactorySheet(viewModel, showBottomSheet, scope)
}

/**
 * 图标生成预览
 */
@OptIn(ExperimentalResourceApi::class)
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
                        modifier = Modifier.animateItem(),
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
                        }).size(if (icon != null) 192.dp else 256.dp).animateItem()
                    ) {
                        icon?.let {
                            if (icon.exists()) {
                                val bitmap = icon.inputStream().readAllBytes().decodeToImageBitmap()
                                Image(
                                    bitmap = bitmap, contentDescription = "预览图标", modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                viewModel.updateIconFactoryInfo(
                                    viewModel.iconFactoryInfoState.copy(
                                        icon = null, result = null
                                    )
                                )
                            }
                        } ?: let {
                            val themeConfig by viewModel.themeConfig.collectAsState()
                            val useDarkTheme = when (themeConfig) {
                                DarkThemeConfig.LIGHT -> false
                                DarkThemeConfig.DARK -> true
                                DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
                            }
                            if (useDarkTheme) {
                                LottieAnimation(scope, "files/lottie_main_3_dark.json")
                            } else {
                                LottieAnimation(scope, "files/lottie_main_3_light.json")
                            }
                        }
                    }
                }
                if (icon != null) {
                    item {
                        Icon(
                            imageVector = Icons.Rounded.Start,
                            contentDescription = "向右",
                            modifier = Modifier.size(48.dp).animateItem()
                        )
                    }
                    item {
                        Box(modifier = Modifier.animateItem()) {
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

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun IconFactoryResultPlaceholder(resultFile: File?, title: String, size: Int) {
    Crossfade(targetState = resultFile) { file ->
        if (file != null && file.exists()) {
            val bitmap = file.inputStream().readAllBytes().decodeToImageBitmap()
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun IconFactorySheet(viewModel: MainViewModel, showBottomSheet: MutableState<Boolean>, scope: CoroutineScope) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging, scope)
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
                dragging = it
            }, onFinish = { result ->
                result.onSuccess { fileList ->
                    fileList.firstOrNull()?.let {
                        val path = it.toAbsolutePath().pathString
                        if (path.isImage) {
                            viewModel.updateIconFactoryInfo(
                                viewModel.iconFactoryInfoState.copy(
                                    icon = File(path),
                                    result = null
                                )
                            )
                        }
                    }
                }
            }))
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
                    }, expanded = viewModel.iconFactoryInfoState.icon == null, FileSelectorType.IMAGE
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
                ExtendedFloatingActionButton(onClick = { showBottomSheet.update { true } },
                    expanded = true,
                    icon = { Icon(Icons.Rounded.Tune, "更多设置") },
                    text = { Text("更多设置") })
            }
        }
        if (showBottomSheet.value) {
            ModalBottomSheet(modifier = Modifier.fillMaxHeight().align(Alignment.BottomEnd),
                sheetState = sheetState,
                onDismissRequest = { showBottomSheet.update { false } }) {
                IconFactorySetting(viewModel, sheetState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconFactorySetting(viewModel: MainViewModel, sheetState: SheetState) {
    val scope = rememberCoroutineScope()
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
            Box(modifier = Modifier.fillMaxWidth()) {
                StringInput(value = viewModel.iconFactoryInfoState.iconName,
                    label = "图标名称",
                    isError = viewModel.iconFactoryInfoState.iconName.isBlank(),
                    onValueChange = { iconName ->
                        viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconName = iconName))
                    })
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(top = 3.dp, end = 16.dp)) {
                    TooltipBox(
                        positionProvider = rememberPlainTooltipPositionProvider(), tooltip = {
                            PlainTooltip {
                                Text(
                                    if (sheetState.currentValue == SheetValue.Expanded) "收起" else "展开更多自定义项",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }, state = rememberTooltipState()
                    ) {
                        FilledTonalIconButton(onClick = {
                            scope.launch {
                                if (sheetState.currentValue == SheetValue.Expanded) {
                                    sheetState.partialExpand()
                                } else {
                                    sheetState.expand()
                                }
                            }
                        }) {
                            val rotate by animateFloatAsState(if (sheetState.currentValue == SheetValue.Expanded) 180f else 0f)
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "展开更多",
                                modifier = Modifier.rotate(rotate)
                            )
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("压缩自定义项", style = MaterialTheme.typography.titleSmall)
                Divider(thickness = 2.dp, startIndent = 18.dp)
            }
            Spacer(Modifier.size(12.dp))
            Compression(viewModel)
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun Compression(viewModel: MainViewModel) {
    val iconFactoryData by viewModel.iconFactoryData.collectAsState()
    val compressionOptions = listOf("无损压缩", "有损压缩")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        compressionOptions.forEachIndexed { index, label ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = compressionOptions.size), onClick = {
                    viewModel.saveIconFactoryData(iconFactoryData.copy(lossless = index == 0))
                }, selected = if (iconFactoryData.lossless) index == 0 else index == 1
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    AnimatedVisibility(!iconFactoryData.lossless) {
        CompressRangeSliders(viewModel, iconFactoryData)
    }

    Spacer(Modifier.size(8.dp))

    var compressionSpeed by remember { mutableFloatStateOf(iconFactoryData.percentage * 10f) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "压缩速度", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "快速",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = "疯狂",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Slider(value = compressionSpeed, onValueChange = {
            compressionSpeed = it
        }, onValueChangeFinished = {
            val percentage =
                "%.2f".format(compressionSpeed.toBigDecimal().divide(10f.toBigDecimal()).toFloat()).toFloat()
            val speed = 11 - (10 * percentage).roundToInt()
            val preset = (7 * percentage).roundToInt() - 1
            viewModel.saveIconFactoryData(
                iconFactoryData.copy(
                    speed = speed, preset = preset, percentage = percentage
                )
            )
        }, valueRange = 1f..10f)
    }
    Spacer(Modifier.size(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Algorithm(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            options = ConfigConstant.ICON_PNG_ALGORITHM,
            isPng = true,
            name = iconFactoryData.pngTypIdx.name
        ) { select ->
            viewModel.saveIconFactoryData(iconFactoryData.copy(pngTypIdx = select))
        }

        Algorithm(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            options = ConfigConstant.ICON_JPEG_ALGORITHM,
            isPng = false,
            name = iconFactoryData.jpegTypIdx.name
        ) { select ->
            viewModel.saveIconFactoryData(iconFactoryData.copy(jpegTypIdx = select))
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
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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

@ExperimentalMaterial3Api
@Composable
private fun CompressRangeSliders(viewModel: MainViewModel, iconFactoryData: IconFactoryData) {
    var rangeSliderPosition by remember { mutableStateOf(iconFactoryData.minimum.toFloat()..iconFactoryData.target.toFloat()) }
    val rangeStart = rangeSliderPosition.start.roundToInt()
    val rangeEnd = rangeSliderPosition.endInclusive.roundToInt()
    Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "PNG质量", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "最低限度：$rangeStart",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = "目标：$rangeEnd",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        RangeSlider(value = rangeSliderPosition, onValueChange = {
            rangeSliderPosition = if (it.endInclusive < 30) {
                it.start.rangeTo(30f)
            } else {
                it
            }
        }, valueRange = 0f..100f, onValueChangeFinished = {
            viewModel.saveIconFactoryData(
                iconFactoryData.copy(
                    minimum = rangeSliderPosition.start.roundToInt(),
                    target = rangeSliderPosition.endInclusive.roundToInt()
                )
            )
        })
        Spacer(Modifier.size(8.dp))
        var jpegQuality by remember { mutableFloatStateOf(iconFactoryData.quality) }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "JPEG质量",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = "目标：${jpegQuality.roundToInt()}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Slider(value = jpegQuality, onValueChange = {
            jpegQuality = it
        }, onValueChangeFinished = {
            viewModel.saveIconFactoryData(
                iconFactoryData.copy(
                    quality = jpegQuality.roundToInt().toFloat()
                )
            )
        }, valueRange = 0f..100f)
    }
}

@ExperimentalMaterial3Api
@Composable
private fun <T> Algorithm(modifier: Modifier, options: List<T>, isPng: Boolean, name: String, onClick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(modifier = modifier, expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            value = name,
            onValueChange = {},
            label = {
                Text(
                    text = if (isPng) "PNG 缩放算法" else "JPEG 缩放算法", style = MaterialTheme.typography.labelLarge
                )
            },
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
                    text = {
                        Text(
                            text = (selectionOption as Enum<*>).name, style = MaterialTheme.typography.labelLarge
                        )
                    },
                    onClick = {
                        onClick(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}