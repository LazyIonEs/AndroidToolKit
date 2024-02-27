package toast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Title(title: String, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp, color: Color = MaterialTheme.colorScheme.primary, fontWeight: FontWeight = FontWeight.Bold, maxLine: Int = 1, textAlign: TextAlign = TextAlign.Start, isLoading: Boolean = false) {
    Text(text = title, modifier = modifier.basicMarquee(), fontWeight = fontWeight, fontSize = fontSize, color = color, maxLines = maxLine, overflow = TextOverflow.Ellipsis, textAlign = textAlign)
}

public interface ToastData {
    public val message: String
    public val icon: ImageVector?
    public val animationDuration: StateFlow<Int?>
    public val type: ToastModel.Type?
    public suspend fun run(accessibilityManager: AccessibilityManager?)
    public fun pause()
    public fun resume()
    public fun dismiss()
    public fun dismissed()
}

data class ToastModel(val message: String, val type: Type) {
    enum class Type {
        Normal, Success, Info, Warning, Error,
    }
}

private data class ColorData(
    val backgroundColor: Color,
    val textColor: Color,
    val iconColor: Color,
    val icon: ImageVector? = null,
)

@Composable public fun Toast(
    toastData: ToastData,
) {

    val animateDuration by toastData.animationDuration.collectAsState()

    val colorData = when (toastData.type) {
        ToastModel.Type.Normal -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.background,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon = Icons.Rounded.Notifications,
        )

        ToastModel.Type.Success -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.primaryContainer,
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Rounded.Check,
        )

        ToastModel.Type.Info -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.secondary,
            textColor = MaterialTheme.colorScheme.onSecondary,
            iconColor = MaterialTheme.colorScheme.onSecondary,
            icon = Icons.Rounded.Info,
        )

        ToastModel.Type.Warning -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.onError,
            iconColor = MaterialTheme.colorScheme.onError,
            icon = Icons.Rounded.Warning,
        )

        ToastModel.Type.Error -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.error,
            textColor = MaterialTheme.colorScheme.onError,
            iconColor = MaterialTheme.colorScheme.onError,
            icon = Icons.Rounded.Error,
        )

        else -> ColorData(
            backgroundColor = MaterialTheme.colorScheme.background,
            textColor = MaterialTheme.colorScheme.primary,
            iconColor = MaterialTheme.colorScheme.primary,
            icon = Icons.Rounded.Notifications,
        )
    }
    val icon = toastData.icon ?: colorData.icon
    key(toastData) {
        Toast(
            message = toastData.message,
            icon = icon,
            backgroundColor = colorData.backgroundColor,
            iconColor = colorData.iconColor,
            textColor = colorData.textColor,
            animateDuration = animateDuration,
            onPause = toastData::pause,
            onResume = toastData::resume,
            onDismissed = toastData::dismissed,
        )
    }
}

@Composable private fun Toast(
    message: String,
    icon: ImageVector?,
    backgroundColor: Color,
    iconColor: Color,
    textColor: Color,
    animateDuration: Int? = 0,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onDismissed: () -> Unit = {},
) {
    val roundedValue = 12.dp
    CardDefaults.shape
    Surface(
        modifier = Modifier.padding(16.dp).widthIn(max = 520.dp).fillMaxWidth().toastGesturesDetector(onPause, onResume, onDismissed),
        color = backgroundColor,
        shape = RoundedCornerShape(roundedValue),
        tonalElevation = 2.dp,
    ) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(animateDuration) { // Do not run animation when animations are turned off.

            if (coroutineContext.durationScale == 0f) return@LaunchedEffect

            if (animateDuration == null) {
                progress.stop()
            } else {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = animateDuration,
                        easing = EaseOut,
                    ),
                )
            }
        }

        val color = LocalContentColor.current
        Row(
            Modifier.drawBehind {
                val fraction = progress.value * size.width
                drawRoundRect(
                    color = color,
                    size = Size(width = fraction, height = size.height),
                    cornerRadius = CornerRadius(roundedValue.toPx()),
                    alpha = 0.1f,
                )
            }.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(24.dp), tint = iconColor)
            }
            Title(message, color = textColor)
        }
    }
}

private fun Modifier.toastGesturesDetector(
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDismissed: () -> Unit,
): Modifier = composed {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    pointerInput(Unit) {
        val decay = splineBasedDecay<Float>(this)
        coroutineScope {
            while (true) {
                awaitPointerEventScope { // Detect a touch down event.
                    val down = awaitFirstDown()
                    onPause()
                    val pointerId = down.id

                    val velocityTracker = VelocityTracker() // Stop any ongoing animation.
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetY.stop()
                        alpha.stop()
                    }

                    verticalDrag(pointerId) { change ->
                        onPause() // Update the animation value with touch events.
                        val changeY = (offsetY.value + change.positionChange().y).coerceAtMost(0f)
                        launch {
                            offsetY.snapTo(changeY)
                        }
                        if (changeY == 0f) {
                            velocityTracker.resetTracking()
                        } else {
                            velocityTracker.addPosition(
                                change.uptimeMillis,
                                change.position,
                            )
                        }
                    }

                    onResume() // No longer receiving touch events. Prepare the animation.
                    val velocity = velocityTracker.calculateVelocity().y
                    val targetOffsetY = decay.calculateTargetValue(
                        offsetY.value,
                        velocity,
                    ) // The animation stops when it reaches the bounds.
                    offsetY.updateBounds(
                        lowerBound = -size.height.toFloat() * 3,
                        upperBound = size.height.toFloat(),
                    )
                    launch {
                        if (velocity >= 0 || targetOffsetY.absoluteValue <= size.height) { // Not enough velocity; Slide back.
                            offsetY.animateTo(
                                targetValue = 0f,
                                initialVelocity = velocity,
                            )
                        } else { // The element was swiped away.
                            launch { offsetY.animateDecay(velocity, decay) }
                            launch {
                                alpha.animateTo(targetValue = 0f, animationSpec = tween(300))
                                onDismissed()
                            }
                        }
                    }
                }
            }
        }
    }.offset {
        IntOffset(0, offsetY.value.roundToInt())
    }.alpha(alpha.value)
}
