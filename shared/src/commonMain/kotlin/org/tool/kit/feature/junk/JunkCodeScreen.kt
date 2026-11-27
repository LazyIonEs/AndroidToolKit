package org.tool.kit.feature.junk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.defaultScrollbarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.FolderInput
import org.tool.kit.model.JunkMode
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.aar_batch_configuration
import org.tool.kit.shared.generated.resources.aar_count
import org.tool.kit.shared.generated.resources.aar_count_unit
import org.tool.kit.shared.generated.resources.aar_name
import org.tool.kit.shared.generated.resources.aar_output_folder_name
import org.tool.kit.shared.generated.resources.aar_output_path
import org.tool.kit.shared.generated.resources.aar_package_and_resources
import org.tool.kit.shared.generated.resources.aar_size_per_file
import org.tool.kit.shared.generated.resources.aar_total_size_range
import org.tool.kit.shared.generated.resources.junk_package_name
import org.tool.kit.shared.generated.resources.junk_random_resource_prefix
import org.tool.kit.shared.generated.resources.junk_random_suffix
import org.tool.kit.shared.generated.resources.resource_prefix
import org.tool.kit.shared.generated.resources.start_generating
import org.tool.kit.shared.generated.resources.suffix

private val contentStartPadding = 24.dp
private val contentEndPadding = 68.dp
private val scrollbarGutter = 12.dp

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/1 20:07
 * @Description : 展示单个与批量 AAR 表单和大小估计，通过事件切换模式或生成
 * @Version     : 1.0
 */
@Composable
fun JunkCodeScreen(state: JunkCodeUiState, onIntent: (JunkCodeIntent) -> Unit, pickOutput: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxSize()
            .padding(top = 20.dp, bottom = 20.dp, end = 14.dp)
    ) {
        BoxWithConstraints(
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = 1120.dp).fillMaxSize()
        ) {
            val contentWidth = maxWidth - contentStartPadding - contentEndPadding
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.size(16.dp))
                FolderInput(
                    value = state.outputPath,
                    label = stringResource(Res.string.aar_output_path),
                    isError = state.outputValidation.isError,
                    onPickerRequest = pickOutput,
                    onValueChange = { onIntent(JunkCodeIntent.OutputPathChanged(it)) },
                )
                Spacer(Modifier.size(8.dp))
                JunkMode(state.mode, onIntent)
                Spacer(Modifier.size(4.dp))
                AnimatedModeContent(
                    state,
                    onIntent,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    stackPanels = contentWidth < 600.dp,
                    stackFields = contentWidth < 320.dp,
                    compact = contentWidth < 420.dp,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AnimatedModeContent(
    state: JunkCodeUiState,
    onIntent: (JunkCodeIntent) -> Unit,
    modifier: Modifier,
    stackPanels: Boolean,
    stackFields: Boolean,
    compact: Boolean,
) {
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val activeMode by rememberUpdatedState(state.mode)
    AnimatedContent(
        targetState = state,
        contentKey = { it.mode },
        modifier = modifier.padding(start = contentStartPadding, end = contentEndPadding - scrollbarGutter)
            .clipToBounds(),
        transitionSpec = {
            val direction = if (targetState.mode == JunkMode.MULTI) 1 else -1
            ((slideInHorizontally(spatial) { direction * it / 24 } + fadeIn(fade)) togetherWith
                (slideOutHorizontally(spatial) { -direction * it / 24 } + fadeOut(fade))) using null
        },
        label = "AAR mode content",
    ) { displayed ->
        val active = displayed.mode == activeMode
        // 大小事件按当前模式写入，退出中的表单只用于过渡。
        val onActiveIntent: (JunkCodeIntent) -> Unit = { intent ->
            if (displayed.mode == activeMode) onIntent(intent)
        }
        val content = if (active) Modifier else Modifier.clearAndSetSemantics {}
        Column(content.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val scrollState = rememberScrollState()
                LaunchedEffect(active) {
                    if (active) scrollState.scrollTo(0)
                }
                FormPanels(
                    displayed, onActiveIntent, stackPanels, stackFields,
                    Modifier.fillMaxWidth().testTag("aar-form-scroll").verticalScroll(scrollState)
                        .padding(end = scrollbarGutter, top = 8.dp, bottom = 8.dp),
                )
                if (active && (scrollState.canScrollForward || scrollState.canScrollBackward)) {
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        style = defaultScrollbarStyle().copy(
                            unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        ),
                    )
                }
            }
            GenerateFooter(displayed, compact) { onActiveIntent(JunkCodeIntent.Submit) }
        }
    }
}

@Composable
private fun FormPanels(
    state: JunkCodeUiState,
    onIntent: (JunkCodeIntent) -> Unit,
    stackPanels: Boolean,
    stackFields: Boolean,
    modifier: Modifier,
) {
    val panelHeight = if (stackPanels) Modifier else Modifier.heightIn(min = 200.dp)
    val size = when (state.mode) {
        JunkMode.SINGLE -> state.single.size
        JunkMode.MULTI -> state.multi.size
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AarFormPair(
            stacked = stackPanels,
            spacing = if (stackPanels) 12.dp else 8.dp,
            first = { fieldModifier ->
                val panel = fieldModifier.then(panelHeight).testTag("aar-config-panel")
                when (state.mode) {
                    JunkMode.SINGLE -> FormPanel(stringResource(Res.string.aar_package_and_resources), panel) {
                        SingleFields(state.single, onIntent, stackFields)
                    }
                    JunkMode.MULTI -> FormPanel(stringResource(Res.string.aar_batch_configuration), panel) {
                        MultiFields(state.multi, onIntent)
                    }
                }
            },
            second = { fieldModifier ->
                val panel = fieldModifier.then(panelHeight).testTag("aar-size-panel")
                FormPanel(stringResource(Res.string.aar_size_per_file), panel) {
                    AarSizeFields(size, state.mode, onIntent, stackFields = !stackPanels || stackFields)
                }
            },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            AarSizeSlider(size, state.mode, Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)) {
                onIntent(JunkCodeIntent.SizeRangeChanged(it))
            }
        }
    }
}

@Composable
internal fun AarFormPair(
    stacked: Boolean,
    spacing: Dp = 12.dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (stacked) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun FormPanel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SingleFields(form: SingleJunkForm, onIntent: (JunkCodeIntent) -> Unit, stackFields: Boolean) {
    FormTextField(form.packageName, stringResource(Res.string.junk_package_name), Modifier.fillMaxWidth()) {
        onIntent(JunkCodeIntent.PackageNameChanged(it.trim()))
    }
    AarFormPair(
        stacked = stackFields,
        first = { modifier ->
            FormTextField(
                form.suffix,
                stringResource(Res.string.suffix),
                modifier,
                trailingIcon = {
                    RandomizeField(stringResource(Res.string.junk_random_suffix)) { onIntent(JunkCodeIntent.RandomSuffix) }
                },
            ) {
                onIntent(JunkCodeIntent.SuffixChanged(it.trim()))
            }
        },
        second = { modifier ->
            FormTextField(
                form.resPrefix,
                stringResource(Res.string.resource_prefix),
                modifier,
                trailingIcon = {
                    RandomizeField(stringResource(Res.string.junk_random_resource_prefix)) { onIntent(JunkCodeIntent.RandomPrefix) }
                },
            ) {
                onIntent(JunkCodeIntent.ResPrefixChanged(it.trim()))
            }
        },
    )
}

@Composable
private fun MultiFields(form: MultiJunkForm, onIntent: (JunkCodeIntent) -> Unit) {
    FormTextField(form.outputDir, stringResource(Res.string.aar_output_folder_name), Modifier.fillMaxWidth()) {
        onIntent(JunkCodeIntent.OutputDirChanged(it))
    }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = form.aarCount,
        onValueChange = { input ->
            if (input.all { it in '0'..'9' }) onIntent(JunkCodeIntent.AarCountChanged(input))
        },
        label = { Text(stringResource(Res.string.aar_count), style = MaterialTheme.typography.labelLarge) },
        suffix = { Text(stringResource(Res.string.aar_count_unit)) },
        singleLine = true,
        isError = (form.aarCount.toIntOrNull() ?: 0) < 1,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun FormTextField(
    value: String,
    label: String,
    modifier: Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        trailingIcon = trailingIcon,
        singleLine = true,
        isError = value.isBlank(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RandomizeField(label: String, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.Shuffle, contentDescription = label)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JunkMode(currentJunkMode: JunkMode, onIntent: (JunkCodeIntent) -> Unit) {
    val junkModeList = JunkMode.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = contentStartPadding, end = contentEndPadding),
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
private fun GenerateFooter(state: JunkCodeUiState, compact: Boolean, onSubmit: () -> Unit) {
    HorizontalDivider(Modifier.padding(end = scrollbarGutter))
    val footer = Modifier.fillMaxWidth().padding(
        end = scrollbarGutter,
        top = if (compact) 8.dp else 12.dp,
        bottom = if (compact) 12.dp else 14.dp,
    )
    if (compact) {
        Column(footer, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutputSummary(state, Modifier.fillMaxWidth())
            GenerateButton(onSubmit, Modifier.fillMaxWidth())
        }
    } else {
        Row(footer, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutputSummary(state, Modifier.weight(1f))
            GenerateButton(onSubmit)
        }
    }
}

@Composable
private fun GenerateButton(onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onSubmit, modifier = modifier) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(Res.string.start_generating))
    }
}

@Composable
private fun OutputSummary(state: JunkCodeUiState, modifier: Modifier) {
    val batch = state.mode == JunkMode.MULTI
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(if (batch) Icons.Rounded.Layers else Icons.Rounded.FolderZip, contentDescription = null)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (batch) Res.string.aar_total_size_range else Res.string.aar_name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (batch) {
                Text(
                    state.estimatedSize.ifEmpty { "—" },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SelectionContainer {
                    Text(
                        state.single.aarName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
