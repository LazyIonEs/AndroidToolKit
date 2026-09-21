@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package org.tool.kit.feature.apk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.feature.ui.LoadingAnimate
import org.tool.kit.feature.ui.UploadAnimate
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_info_loading
import org.tool.kit.shared.generated.resources.apk_info_pick
import org.tool.kit.shared.generated.resources.apk_info_replace

/** 复用公共加载和拖入动画，操作与数据读取仍交由 Route/ViewModel。 */
@Composable
fun ApkInformationScreen(
    state: ApkInformationUiState,
    useDarkTheme: Boolean,
    onIntent: (ApkInformationIntent) -> Unit,
    onPickFile: () -> Unit,
    dragging: Boolean,
    dropTarget: DragAndDropTarget,
) {
    val showDrop = dragging && !state.busy
    Box(
        Modifier.fillMaxSize().testTag("apk-information-page")
            .dragAndDropTarget(shouldStartDragAndDrop = { !state.busy }, target = dropTarget)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                AnimatedContent(
                    state,
                    modifier = Modifier.fillMaxSize(),
                    contentKey = { it.phase },
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    label = "APK read state"
                ) { displayed ->
                    Box(Modifier.fillMaxSize()) {
                        when (displayed.phase) {
                            ApkInformationPhase.Idle -> ApkInformationEmptyState { if (state.phase == ApkInformationPhase.Idle) onPickFile() }
                            ApkInformationPhase.Loading -> Column(
                                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.apk_info_loading),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                displayed.inputFile?.let { ApkInputFile(it) }
                            }

                            ApkInformationPhase.Result -> displayed.result?.let { result ->
                                ApkInformationResult(
                                    result, displayed.inputFile, useDarkTheme,
                                    active = state.phase == ApkInformationPhase.Result && state.inputFile == displayed.inputFile,
                                    onPickFile = { if (state.phase == ApkInformationPhase.Result) onPickFile() }) {
                                    if (state.phase == ApkInformationPhase.Result && state.inputFile == displayed.inputFile) {
                                        onIntent(ApkInformationIntent.CopyText(it))
                                    }
                                }
                            }
                        }
                    }
                }
                // Reserve the bottom status area so the shared full-size artwork never covers its text.
                Box(
                    Modifier.fillMaxSize().padding(bottom = 104.dp)
                        .then(if (state.busy) Modifier.testTag("apk-loading-animation") else Modifier)
                ) {
                    LoadingAnimate(state.busy, useDarkTheme)
                }
            }
        }
        // Cover the identity header and the complete workspace; block click-through during a drop.
        UploadAnimate(
            showDrop,
            modifier = Modifier.matchParentSize()
                .then(if (showDrop) Modifier.testTag("apk-drop-animation") else Modifier),
            shape = RectangleShape
        )
    }
}

@Composable
internal fun ApkSelectButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    replace: Boolean = false
) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(if (replace) Res.string.apk_info_replace else Res.string.apk_info_pick))
    }
    if (replace) FilledTonalButton(onClick, modifier.testTag("apk-pick-file"), content = content)
    else Button(onClick, modifier.testTag("apk-pick-file"), content = content)
}
