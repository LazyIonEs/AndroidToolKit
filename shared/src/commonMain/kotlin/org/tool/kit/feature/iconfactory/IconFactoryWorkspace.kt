package org.tool.kit.feature.iconfactory

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Start
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.*

private val iconDensities = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)

/** 800 × 600 桌面工作区；布局偏好独立于业务状态，设置编辑仍在原面板中。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IconFactoryWorkspace(
    state: IconFactoryUiState,
    inputImage: IconImageUi?,
    resultImages: List<IconImageUi?>?,
    onIntent: (IconFactoryIntent) -> Unit,
    pickIcon: () -> Unit,
) {
    var detail by rememberSaveable { mutableStateOf(false) }
    var selectedDensity by rememberSaveable { mutableIntStateOf(iconDensities.lastIndex) }
    Surface(Modifier.fillMaxSize().testTag("icon-workspace")) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.icon_factory_page_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(Res.string.icon_factory_summary), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconLayoutButtons(detail) { detail = it }
            }
            Spacer(Modifier.height(16.dp))
            val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
            val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
            AnimatedContent(
                targetState = detail,
                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                transitionSpec = {
                    val direction = if (targetState) 1 else -1
                    (slideInHorizontally(spatial) { direction * it / 24 } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -direction * it / 24 } + fadeOut(effects)) using SizeTransform(clip = true)
                },
                contentAlignment = Alignment.TopStart,
                label = "Icon preview layout",
            ) { displayedDetail ->
                // Retiring content remains visible only for the transition, not as a second action target.
                val contentModifier = Modifier.fillMaxSize()
                    .then(if (displayedDetail == detail) Modifier else Modifier.clearAndSetSemantics {})
                val activeIntent: (IconFactoryIntent) -> Unit = { if (displayedDetail == detail) onIntent(it) }
                val activePick: () -> Unit = { if (displayedDetail == detail) pickIcon() }
                if (displayedDetail) {
                    IconDetailLayout(state, inputImage, resultImages, selectedDensity,
                        { if (displayedDetail == detail) selectedDensity = it }, activeIntent, activePick,
                        contentModifier.testTag("icon-detail-content"))
                } else {
                    IconOverviewLayout(state, inputImage, resultImages, activeIntent, activePick, onInspect = {
                        if (displayedDetail == detail) {
                            selectedDensity = it
                            detail = true
                        }
                    }, modifier = contentModifier.testTag("icon-overview-content"))
                }
            }
        }
    }
}

/** 固定功能图标；选中状态由原生按钮形变和色阶表达，不追加勾号。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconLayoutButtons(detail: Boolean, onSelect: (Boolean) -> Unit) {
    val labels = listOf(Res.string.icon_factory_layout_overview, Res.string.icon_factory_layout_detail)
    Row(Modifier.width(280.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        labels.forEachIndexed { index, label ->
            val checked = detail == (index == 1)
            ToggleButton(
                checked = checked, onCheckedChange = { onSelect(index == 1) },
                modifier = Modifier.weight(1f).testTag(if (index == 0) "icon-layout-overview" else "icon-layout-detail"),
                colors = ToggleButtonDefaults.elevatedToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                    else ButtonGroupDefaults.connectedTrailingButtonShapes(),
            ) {
                Icon(if (index == 0) Icons.Rounded.ViewWeek else Icons.Rounded.CenterFocusStrong,
                    null, Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

@Composable
private fun IconOverviewLayout(
    state: IconFactoryUiState, inputImage: IconImageUi?, resultImages: List<IconImageUi?>?,
    onIntent: (IconFactoryIntent) -> Unit, pickIcon: () -> Unit, onInspect: (Int) -> Unit, modifier: Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))
        OutlinedCard(Modifier.fillMaxWidth().height(132.dp).testTag("icon-source")) {
            Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconSourceImage(inputImage, 80.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconCaption(stringResource(Res.string.icon_factory_source))
                    IconSourceName(state)
                    IconCaption(stringResource(if (state.form.inputPath != null) Res.string.icon_factory_replace_hint
                        else Res.string.icon_factory_source_hint))
                }
                IconPickButton(state, pickIcon)
            }
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().weight(1f).testTag("icon-results")) {
            IconPreviewHeading(state, resultImages)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                iconDensities.forEachIndexed { index, (density, pixels) ->
                    Card(onClick = { onInspect(index) }, modifier = Modifier.weight(1f).fillMaxHeight().testTag("icon-result-$density")) {
                        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                IconResultImage(resultImages?.getOrNull(index), state.result != null, density, 56.dp)
                            }
                            Text(density, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            IconCaption("$pixels × $pixels px")
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            IconCaption(stringResource(Res.string.icon_factory_inspect_hint))
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconOutputSummary(state, Modifier.weight(1f))
            IconSettingsButton(state, onIntent)
            IconGenerateButton(state, onIntent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconDetailLayout(
    state: IconFactoryUiState, inputImage: IconImageUi?, resultImages: List<IconImageUi?>?,
    selected: Int, onSelect: (Int) -> Unit, onIntent: (IconFactoryIntent) -> Unit, pickIcon: () -> Unit, modifier: Modifier,
) {
    val (density, pixels) = iconDensities[selected]
    val image = resultImages?.getOrNull(selected)
    Column(modifier.fillMaxWidth()) {
        PrimaryTabRow(selectedTabIndex = selected) {
            iconDensities.forEachIndexed { index, item ->
                Tab(selected = index == selected, onClick = { onSelect(index) }, text = { Text(item.first) },
                    modifier = Modifier.testTag("icon-density-${item.first}"))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(Modifier.weight(1f).fillMaxHeight().testTag("icon-results"), shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    IconPreviewHeading(state, resultImages)
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        if (image != null) IconResultImage(image, state.result != null, density, 192.dp)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconResultImage(null, state.result != null, density, 48.dp)
                            IconCaption(stringResource(when {
                                state.busy -> Res.string.icon_factory_generating
                                state.result != null -> Res.string.icon_factory_preview_unavailable
                                else -> Res.string.icon_factory_preview_pending
                            }))
                        }
                    }
                    Text("$pixels × $pixels px", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    IconCaption(stringResource(Res.string.icon_factory_preview_scaled))
                }
            }
            Column(Modifier.width(248.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxWidth().testTag("icon-source")) {
                    Text(stringResource(Res.string.icon_factory_source), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconSourceImage(inputImage, 48.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconSourceName(state)
                            IconCaption(stringResource(Res.string.icon_factory_formats))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    IconPickButton(state, pickIcon)
                    Spacer(Modifier.height(12.dp))
                    IconCaption(stringResource(if (state.form.inputPath != null) Res.string.icon_factory_replace_hint
                        else Res.string.icon_factory_size_hint))
                }
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(24.dp))
                IconOutputSummary(state, Modifier.fillMaxWidth())
                Spacer(Modifier.weight(1f))
                IconSettingsButton(state, onIntent, Modifier.align(Alignment.End))
                Spacer(Modifier.height(8.dp))
                IconGenerateButton(state, onIntent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun IconSourceName(state: IconFactoryUiState) {
    IconSummaryText(state.form.inputPath?.substringAfterLast('/')?.substringAfterLast('\\')
        ?: stringResource(Res.string.icon_factory_drop_image), MaterialTheme.typography.titleMedium, Modifier.fillMaxWidth())
}

@Composable
private fun IconSourceImage(image: IconImageUi?, size: Dp) {
    if (image != null) {
        AsyncImage(image.request, stringResource(Res.string.icon_factory_source), Modifier.size(size), contentScale = ContentScale.Fit,
            error = rememberVectorPainter(Icons.Rounded.BrokenImage))
    } else {
        Surface(Modifier.size(size), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun IconResultImage(image: IconImageUi?, attempted: Boolean, density: String, size: Dp) {
    if (image != null) {
        AsyncImage(image.request, stringResource(Res.string.icon_factory_density_preview, density), Modifier.size(size),
            contentScale = ContentScale.Fit, error = rememberVectorPainter(Icons.Rounded.BrokenImage))
    } else {
        Icon(if (attempted) Icons.Rounded.BrokenImage else Icons.Rounded.Image,
            stringResource(if (attempted) Res.string.icon_factory_preview_unavailable else Res.string.icon_factory_preview_waiting),
            Modifier.size(minOf(size, 48.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IconPreviewHeading(state: IconFactoryUiState, resultImages: List<IconImageUi?>?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(Res.string.icon_factory_preview), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        IconCaption(when {
            state.busy -> stringResource(Res.string.icon_factory_generating)
            state.result != null -> stringResource(Res.string.icon_factory_preview_count,
                resultImages?.take(iconDensities.size)?.count { it != null } ?: 0, iconDensities.size)
            else -> stringResource(Res.string.icon_factory_density_count, iconDensities.size)
        })
    }
}

@Composable
private fun IconCaption(text: String) = Text(text, style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun IconPickButton(state: IconFactoryUiState, pickIcon: () -> Unit) {
    FilledTonalButton(onClick = pickIcon, enabled = !state.busy, modifier = Modifier.testTag("icon-pick-image")) {
        Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(if (state.form.inputPath != null) Res.string.icon_factory_replace_image else Res.string.icon_factory_select_image))
    }
}

@Composable
private fun IconSettingsButton(state: IconFactoryUiState, onIntent: (IconFactoryIntent) -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = { onIntent(IconFactoryIntent.SheetOpened) }, enabled = !state.busy,
        modifier = modifier.testTag("icon-more-settings")) {
        Icon(Icons.Rounded.Tune, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.more_settings))
    }
}

@Composable
private fun IconGenerateButton(state: IconFactoryUiState, onIntent: (IconFactoryIntent) -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = { onIntent(IconFactoryIntent.Submit) }, enabled = state.form.inputPath != null && !state.busy,
        modifier = modifier.testTag("icon-generate")) {
        if (state.busy) CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize).testTag("icon-progress"), strokeWidth = 2.dp)
        else Icon(Icons.Rounded.Start, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(when {
            state.busy -> Res.string.icon_factory_generating
            state.result?.any { it.previewAvailable } == true -> Res.string.icon_factory_generate_again
            else -> Res.string.start_making
        }))
    }
}

@Composable
private fun IconOutputSummary(state: IconFactoryUiState, modifier: Modifier) {
    val form = state.form
    val output = if (form.outputPath.isBlank() || form.fileDir.isBlank()) {
        stringResource(Res.string.icon_factory_output_missing)
    } else {
        val separator = if ('\\' in form.outputPath) "\\" else "/"
        form.outputPath.trimEnd('/', '\\') + separator + form.fileDir.trimStart('/', '\\')
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.icon_factory_output_location), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconSummaryText(output, MaterialTheme.typography.bodyMedium, Modifier.fillMaxWidth())
            val compression = stringResource(if (state.settings.lossless) Res.string.lossless_compression else Res.string.lossy_compression)
            IconSummaryText("${form.iconDir} · ${form.iconName} · $compression", MaterialTheme.typography.bodySmall,
                Modifier.fillMaxWidth(), secondary = true)
        }
    }
}

/** 长文件名及路径保留单行排版，同时提供完整内容的原生提示。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconSummaryText(text: String, style: TextStyle, modifier: Modifier, centered: Boolean = false, secondary: Boolean = false) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState(), modifier = modifier,
    ) {
        Text(text, Modifier.fillMaxWidth(), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}
