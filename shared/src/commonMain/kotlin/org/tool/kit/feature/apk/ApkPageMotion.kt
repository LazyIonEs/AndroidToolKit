@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.tool.kit.feature.apk

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Small directional motion keeps the fixed APK header still and follows system animation scaling. */
@Composable
internal fun <T> ApkPageMotion(
    target: T, modifier: Modifier = Modifier, forward: (T, T) -> Boolean = { _, _ -> true },
    content: @Composable (T) -> Unit
) {
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = target, modifier = modifier.fillMaxSize().clipToBounds(),
        transitionSpec = {
            val direction = if (forward(initialState, targetState)) 1 else -1
            (slideInHorizontally(spatial) { direction * it / 12 } + fadeIn(effects)) togetherWith
                    (slideOutHorizontally(spatial) { -direction * it / 12 } + fadeOut(effects)) using SizeTransform(
                clip = true
            )
        }, contentAlignment = Alignment.TopStart, label = "APK navigation"
    ) { page ->
        Box(Modifier.fillMaxSize()) { content(page) }
    }
}

@Composable
internal fun ApkBackHeading(
    title: String,
    back: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    endText: String? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onBack, Modifier.testTag("apk-detail-back")) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text(back)
        }
        Box(Modifier.weight(1f)) {
            ApkInfoTooltip(title) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        endText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ApkPanel(
    modifier: Modifier = Modifier,
    padding: Int = 16,
    gap: Int = 12,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.padding(padding.dp),
            verticalArrangement = Arrangement.spacedBy(gap.dp),
            content = content
        )
    }
}

/** Fill the available height for short content; keep natural sizing and scrolling for long content. */
@Composable
internal fun ApkFillScrollColumn(
    modifier: Modifier = Modifier,
    padding: Int = 0,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(padding.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content
        )
    }
}
