package org.tool.kit.feature.iconfactory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.constant.ConfigConstant
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.feature.ui.StringInput
import org.tool.kit.feature.ui.UploadAnimate
import org.tool.kit.model.IconFactoryData
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.android_directory
import org.tool.kit.shared.generated.resources.check_error
import org.tool.kit.shared.generated.resources.close
import org.tool.kit.shared.generated.resources.compress_custom
import org.tool.kit.shared.generated.resources.compression_speed
import org.tool.kit.shared.generated.resources.crazy
import org.tool.kit.shared.generated.resources.expand
import org.tool.kit.shared.generated.resources.external_directory_name
import org.tool.kit.shared.generated.resources.fast
import org.tool.kit.shared.generated.resources.icon_name
import org.tool.kit.shared.generated.resources.icon_output_path
import org.tool.kit.shared.generated.resources.jpeg_quality
import org.tool.kit.shared.generated.resources.jpeg_scaling_algorithm
import org.tool.kit.shared.generated.resources.lossless_compression
import org.tool.kit.shared.generated.resources.lossy_compression
import org.tool.kit.shared.generated.resources.minimum
import org.tool.kit.shared.generated.resources.png_quality
import org.tool.kit.shared.generated.resources.png_scaling_algorithm
import org.tool.kit.shared.generated.resources.target
import kotlin.math.roundToInt

/** 页面拖放和设置面板宿主；设置面板内部布局保持独立。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IconFactoryScreen(
    state: IconFactoryUiState,
    inputImage: IconImageUi?,
    resultImages: List<IconImageUi?>?,
    onIntent: (IconFactoryIntent) -> Unit,
    pickOutput: () -> Unit,
    pickIcon: () -> Unit,
    dragging: Boolean,
    target: androidx.compose.ui.draganddrop.DragAndDropTarget,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    Box(Modifier.fillMaxSize().dragAndDropTarget(
        shouldStartDragAndDrop = { !state.busy && !state.sheetOpen }, target = target,
    )) {
        IconFactoryWorkspace(state, inputImage, resultImages, onIntent, pickIcon)
        UploadAnimate(dragging && !state.busy && !state.sheetOpen)
        if (state.sheetOpen) {
            ModalBottomSheet(
                modifier = Modifier.fillMaxHeight().align(Alignment.BottomEnd),
                sheetState = sheetState,
                onDismissRequest = { onIntent(IconFactoryIntent.SheetClosed) },
            ) {
                IconFactorySetting(state.form, state.settings, state.draft, onIntent, pickOutput, sheetState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconFactorySetting(form: IconFactoryForm, settings: IconFactoryData, draft: IconSettingsDraft,
    onIntent: (IconFactoryIntent) -> Unit, pickOutput: () -> Unit, sheetState: SheetState) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(end = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(Modifier.size(8.dp))
            FolderInput(
                value = form.outputPath,
                label = stringResource(Res.string.icon_output_path),
                isError = false, onPickerRequest = pickOutput,
                onValueChange = { path ->
                    onIntent(IconFactoryIntent.OutputPathChanged(path))
                })
        }
        item {
            Spacer(Modifier.size(8.dp))
            IconsFactoryInput(form, onIntent)
        }
        item {
            Spacer(Modifier.size(8.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                StringInput(
                    value = form.iconName,
                    label = stringResource(Res.string.icon_name),
                    isError = form.iconName.isBlank(),
                    onValueChange = { iconName ->
                        onIntent(IconFactoryIntent.IconNameChanged(iconName))
                    })
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .padding(top = 3.dp, end = 16.dp)
                ) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 8.dp),
                    thickness = 2.dp
                )
            }
            Spacer(Modifier.size(12.dp))
            Compression(settings, draft, onIntent)
            Spacer(Modifier.size(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@ExperimentalMaterial3Api
@Composable
/** 压缩选项编辑器：滑动仅修改草稿，手势结束后发送提交事件。 */
fun Compression(iconFactoryData: IconFactoryData, draft: IconSettingsDraft, onIntent: (IconFactoryIntent) -> Unit) {
    DisposableEffect(Unit) {
        onIntent(IconFactoryIntent.CompressionEditorEntered)
        onDispose { }
    }
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
                    onIntent(IconFactoryIntent.LosslessChanged(index == 0))
                },
                colors = ToggleButtonDefaults.tonalToggleButtonColors(),
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
        CompressRangeSliders(draft, onIntent)
    }

    Spacer(Modifier.size(8.dp))

    val compressionSpeed = draft.compressionSpeed

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
            onIntent(IconFactoryIntent.CompressionSpeedChanged(it))
        }, onValueChangeFinished = {
            onIntent(IconFactoryIntent.CompressionSpeedCommitted)
        }, valueRange = 1f..10f)
    }
    Spacer(Modifier.size(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Algorithm(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(1f),
            options = ConfigConstant.ICON_PNG_ALGORITHM,
            isPng = true,
            name = iconFactoryData.pngTypIdx.name
        ) { select ->
            onIntent(IconFactoryIntent.PngAlgorithmChanged(select))
        }

        Algorithm(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(1f),
            options = ConfigConstant.ICON_JPEG_ALGORITHM,
            isPng = false,
            name = iconFactoryData.jpegTypIdx.name
        ) { select ->
            onIntent(IconFactoryIntent.JpegAlgorithmChanged(select))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconsFactoryInput(form: IconFactoryForm, onIntent: (IconFactoryIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(1f),
            value = form.fileDir,
            onValueChange = { fileDir ->
                onIntent(IconFactoryIntent.FileDirChanged(fileDir))
            },
            label = {
                Text(
                    stringResource(Res.string.external_directory_name),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            singleLine = true,
            isError = form.fileDir.isBlank(),
        )
        var expanded by remember { mutableStateOf(false) }
        val options = ConfigConstant.ANDROID_ICON_DIR_LIST
        ExposedDropdownMenuBox(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp)
                .weight(1f),
            expanded = expanded,
            onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                value = form.iconDir,
                onValueChange = { iconDir ->
                    onIntent(IconFactoryIntent.IconDirChanged(iconDir))
                },
                label = {
                    Text(
                        text = stringResource(Res.string.android_directory),
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                isError = form.iconDir.isBlank(),
                singleLine = true,
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
                            onIntent(IconFactoryIntent.IconDirChanged(selectionOption))
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
/** 展示 PNG 质量范围和 JPEG 质量草稿，在用户结束拖动时提交对应字段。 */
private fun CompressRangeSliders(draft: IconSettingsDraft, onIntent: (IconFactoryIntent) -> Unit) {
    DisposableEffect(Unit) {
        onIntent(IconFactoryIntent.LossyEditorEntered)
        onDispose { }
    }
    val rangeSliderPosition = draft.minimum..draft.target
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
            onIntent(IconFactoryIntent.PngRangeChanged(it.start, it.endInclusive))
        }, valueRange = 0f..100f, onValueChangeFinished = {
            onIntent(IconFactoryIntent.PngRangeCommitted)
        })
        Spacer(Modifier.size(8.dp))
        val jpegQuality = draft.jpegQuality
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
            onIntent(IconFactoryIntent.JpegQualityChanged(it))
        }, onValueChangeFinished = {
            onIntent(IconFactoryIntent.JpegQualityCommitted)
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
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
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
