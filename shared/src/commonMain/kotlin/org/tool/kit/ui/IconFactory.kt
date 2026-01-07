package org.tool.kit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Start
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.constant.ConfigConstant
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.FileSelectorType
import org.tool.kit.model.IconFactoryData
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.ZCOOLKuaiLe_Regular
import org.tool.kit.shared.generated.resources.android_directory
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.close
import org.tool.kit.shared.generated.resources.compress_custom
import org.tool.kit.shared.generated.resources.compression_speed
import org.tool.kit.shared.generated.resources.crazy
import org.tool.kit.shared.generated.resources.expand
import org.tool.kit.shared.generated.resources.external_directory_name
import org.tool.kit.shared.generated.resources.fast
import org.tool.kit.shared.generated.resources.icon_factory_describe
import org.tool.kit.shared.generated.resources.icon_factory_title
import org.tool.kit.shared.generated.resources.icon_name
import org.tool.kit.shared.generated.resources.icon_output_path
import org.tool.kit.shared.generated.resources.jpeg_quality
import org.tool.kit.shared.generated.resources.jpeg_scaling_algorithm
import org.tool.kit.shared.generated.resources.let_go
import org.tool.kit.shared.generated.resources.lossless_compression
import org.tool.kit.shared.generated.resources.lossy_compression
import org.tool.kit.shared.generated.resources.minimum
import org.tool.kit.shared.generated.resources.more_settings
import org.tool.kit.shared.generated.resources.png_quality
import org.tool.kit.shared.generated.resources.png_scaling_algorithm
import org.tool.kit.shared.generated.resources.start_making
import org.tool.kit.shared.generated.resources.target
import org.tool.kit.shared.generated.resources.upload_image
import org.tool.kit.utils.LottieAnimation
import org.tool.kit.utils.getImageRequest
import org.tool.kit.utils.isImage
import org.tool.kit.utils.update
import org.tool.kit.vm.MainViewModel
import org.tool.kit.vm.UIState
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
    val showBottomSheet = remember { mutableStateOf(false) }
    IconFactoryPreview(viewModel, showBottomSheet)
    IconFactorySheet(viewModel, showBottomSheet)
}

/**
 * 图标生成预览
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
private fun IconFactoryPreview(
    viewModel: MainViewModel, showBottomSheet: MutableState<Boolean>
) {
    val icon = viewModel.iconFactoryInfoState.icon
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(
                visible = icon == null,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(.6f).padding(start = 16.dp)
                ) {
                    val fontFamily = FontFamily(Font(Res.font.ZCOOLKuaiLe_Regular))
                    Text(
                        text = stringResource(Res.string.icon_factory_title),
                        style = MaterialTheme.typography.displayMedium,
                        fontFamily = fontFamily,
                    )
                    Spacer(Modifier.size(24.dp))
                    Text(
                        text = stringResource(Res.string.icon_factory_describe),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }

            Crossfade(targetState = icon, modifier = Modifier.weight(1.5f), content = { icon ->
                icon?.let {
                    Box(modifier = Modifier.size(256.dp), contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = getImageRequest(icon),
                            contentDescription = null,
                            modifier = Modifier.size(192.dp)
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
                        LottieAnimation(
                            "files/lottie_main_3_dark.json",
                            modifier = Modifier.requiredSize(256.dp)
                        )
                    } else {
                        LottieAnimation(
                            "files/lottie_main_3_light.json",
                            modifier = Modifier.requiredSize(256.dp)
                        )
                    }
                }
            })

            AnimatedVisibility(
                visible = icon != null,
                modifier = Modifier.weight(.5f),
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Start,
                    contentDescription = "Start",
                    modifier = Modifier.size(48.dp)
                )
            }

            AnimatedVisibility(
                visible = icon != null,
                modifier = Modifier.weight(3f),
                enter = fadeIn() + slideInHorizontally(),
                exit = fadeOut() + slideOutHorizontally()
            ) {
                IconFactoryResult(viewModel)
            }
        }
        AnimatedVisibility(icon != null) {
            Column {
                Spacer(Modifier.size(12.dp))
                Button({
                    viewModel.iconFactoryInfoState.apply {
                        if (outputPath.isBlank() || fileDir.isBlank() || iconName.isBlank()) {
                            if (!showBottomSheet.value) showBottomSheet.update { true }
                            viewModel.updateSnackbarVisuals(Res.string.check_error)
                            return@Button
                        }
                    }
                    viewModel.iconGeneration(icon?.absolutePath ?: return@Button)
                }) {
                    Text(text = stringResource(Res.string.start_making))
                }
            }
        }
    }
}

@Composable
private fun IconFactoryResult(viewModel: MainViewModel) {
    Column(
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(0),
                title = "mdpi",
                size = 48
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(1),
                title = "hdpi",
                size = 72
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(2),
                title = "xhdpi",
                size = 96
            )
        }
        Spacer(Modifier.size(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(3),
                title = "xxhdpi",
                size = 144
            )

            IconFactoryResultPlaceholder(
                resultFile = viewModel.iconFactoryInfoState.result?.getOrNull(4),
                title = "xxxhdpi",
                size = 192
            )
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun IconFactoryResultPlaceholder(resultFile: File?, title: String, size: Int) {
    Crossfade(targetState = resultFile) { file ->
        if (file != null && file.exists()) {
            AsyncImage(
                model = getImageRequest(file),
                contentDescription = null,
                modifier = Modifier.size(size.dp)
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
private fun IconFactorySheet(viewModel: MainViewModel, showBottomSheet: MutableState<Boolean>) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var dragging by remember { mutableStateOf(false) }
    UploadAnimate(dragging)
    Box(
        modifier = Modifier.fillMaxSize().dragAndDropTarget(
            shouldStartDragAndDrop = accept@{ true },
            target = dragAndDropTarget(dragging = { dragging = it }, onFinish = { result ->
                result.onSuccess { fileList ->
                    fileList.firstOrNull()?.let {
                        val path = it.toAbsolutePath().pathString
                        if (path.isImage) {
                            viewModel.updateIconFactoryInfo(
                                viewModel.iconFactoryInfoState.copy(
                                    icon = File(path), result = null
                                )
                            )
                        }
                    }
                }
            })
        )
    ) {
        Column(modifier = Modifier.align(Alignment.BottomEnd)) {
            AnimatedVisibility(
                visible = viewModel.iconFactoryUIState != UIState.Loading,
                modifier = Modifier.align(Alignment.End)
            ) {
                FileButton(
                    value = if (dragging) {
                        stringResource(Res.string.let_go)
                    } else {
                        stringResource(Res.string.upload_image)
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
                    icon = { Icon(Icons.Rounded.Tune, "Tune") },
                    text = { Text(stringResource(Res.string.more_settings)) })
            }
        }
        if (showBottomSheet.value) {
            ModalBottomSheet(
                modifier = Modifier.fillMaxHeight().align(Alignment.BottomEnd),
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
        modifier = Modifier.fillMaxSize().padding(end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.size(8.dp))
            FolderInput(
                value = viewModel.iconFactoryInfoState.outputPath,
                label = stringResource(Res.string.icon_output_path),
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
                StringInput(
                    value = viewModel.iconFactoryInfoState.iconName,
                    label = stringResource(Res.string.icon_name),
                    isError = viewModel.iconFactoryInfoState.iconName.isBlank(),
                    onValueChange = { iconName ->
                        viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconName = iconName))
                    })
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(top = 3.dp, end = 16.dp)
                ) {
                    TooltipBox(
                        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(
                                    if (sheetState.currentValue == SheetValue.Expanded)
                                        stringResource(Res.string.close)
                                    else
                                        stringResource(Res.string.expand),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        state = rememberTooltipState()
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
                                contentDescription = "KeyboardArrowUp",
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
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(Res.string.compress_custom),
                    style = MaterialTheme.typography.titleSmall
                )
                HorizontalDivider(modifier = Modifier.padding(start = 8.dp), thickness = 2.dp)
            }
            Spacer(Modifier.size(12.dp))
            Compression(viewModel)
            Spacer(Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalMaterial3Api
@Composable
fun Compression(viewModel: MainViewModel) {
    val iconFactoryData by viewModel.iconFactoryData.collectAsState()
    val compressionOptions =
        listOf(
            stringResource(Res.string.lossless_compression),
            stringResource(Res.string.lossy_compression)
        )
    Row(
        modifier = Modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        compressionOptions.forEachIndexed { index, label ->
            ToggleButton(
                checked = if (iconFactoryData.lossless) index == 0 else index == 1,
                onCheckedChange = {
                    viewModel.saveIconFactoryData(iconFactoryData.copy(lossless = index == 0))
                },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    compressionOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                AnimatedVisibility(if (iconFactoryData.lossless) index == 0 else index == 1) {
                    Row {
                        Icon(
                            imageVector = Icons.Rounded.Done,
                            contentDescription = "Done icon",
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(text = label)
            }
        }
    }
    AnimatedVisibility(
        visible = !iconFactoryData.lossless,
        enter = fadeIn() + expandVertically(),
        exit = shrinkVertically() + fadeOut()
    ) {
        CompressRangeSliders(viewModel, iconFactoryData)
    }

    Spacer(Modifier.size(8.dp))

    var compressionSpeed by remember { mutableFloatStateOf(iconFactoryData.percentage * 10f) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.compression_speed),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(Res.string.fast),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = stringResource(Res.string.crazy),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        Slider(value = compressionSpeed, onValueChange = {
            compressionSpeed = it
        }, onValueChangeFinished = {
            val percentage =
                "%.2f".format(compressionSpeed.toBigDecimal().divide(10f.toBigDecimal()).toFloat())
                    .toFloat()
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
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
            label = {
                Text(
                    stringResource(Res.string.external_directory_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = viewModel.iconFactoryInfoState.fileDir.isBlank(),
        )
        var expanded by remember { mutableStateOf(false) }
        val options = ConfigConstant.ANDROID_ICON_DIR_LIST
        ExposedDropdownMenuBox(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            expanded = expanded,
            onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                value = viewModel.iconFactoryInfoState.iconDir,
                onValueChange = { iconDir ->
                    viewModel.updateIconFactoryInfo(viewModel.iconFactoryInfoState.copy(iconDir = iconDir))
                },
                label = {
                    Text(
                        text = stringResource(Res.string.android_directory),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
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
                        text = {
                            Text(
                                text = selectionOption,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        onClick = {
                            viewModel.updateIconFactoryInfo(
                                viewModel.iconFactoryInfoState.copy(
                                    iconDir = selectionOption
                                )
                            )
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
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(Res.string.png_quality),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(Res.string.minimum, rangeStart),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            Text(
                text = stringResource(Res.string.target, rangeEnd),
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
                text = stringResource(Res.string.jpeg_quality),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = stringResource(Res.string.target, jpegQuality.roundToInt()),
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
private fun <T> Algorithm(
    modifier: Modifier,
    options: List<T>,
    isPng: Boolean,
    name: String,
    onClick: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            value = name,
            onValueChange = {},
            label = {
                Text(
                    text = if (isPng)
                        stringResource(Res.string.png_scaling_algorithm)
                    else
                        stringResource(Res.string.jpeg_scaling_algorithm),
                    style = MaterialTheme.typography.labelLarge
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
                            text = (selectionOption as Enum<*>).name,
                            style = MaterialTheme.typography.labelLarge
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