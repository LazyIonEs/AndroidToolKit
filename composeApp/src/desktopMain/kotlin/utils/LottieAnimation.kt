package utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import org.jetbrains.skia.sksg.InvalidationController
import org.tool.kit.composeapp.generated.resources.Res
import kotlin.math.roundToInt

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/7 16:54
 * @Description : 加载json动画
 * @Version     : 1.0
 */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun LottieAnimation(scope: CoroutineScope, path: String, modifier: Modifier = Modifier) {
    var animation by remember { mutableStateOf<Animation?>(null) }
    scope.launch {
        val json = Res.readBytes(path).decodeToString()
        animation = Animation.makeFromString(json)
    }
    animation?.let { InfiniteAnimation(it, modifier.fillMaxSize()) }
}

@Composable
private fun InfiniteAnimation(animation: Animation, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = animation.duration, animationSpec = infiniteRepeatable(
            animation = tween((animation.duration * 1000).roundToInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val invalidationController = remember { InvalidationController() }
    animation.seekFrameTime(time, invalidationController)
    Canvas(modifier) {
        drawIntoCanvas {
            animation.render(
                canvas = it.nativeCanvas, dst = Rect.makeWH(size.width, size.height)
            )
        }
    }
}