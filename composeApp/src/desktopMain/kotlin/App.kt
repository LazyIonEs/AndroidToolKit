import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.readResourceBytes
import org.jetbrains.skia.Rect
import org.jetbrains.skia.skottie.Animation
import org.jetbrains.skia.sksg.InvalidationController
import theme.AppTheme
import toast.ToastUI
import toast.ToastUIState
import vm.MainViewModel
import kotlin.math.roundToInt

@Composable
fun App() {
    val viewModel = remember { MainViewModel() }
    val useDarkTheme = when(viewModel.darkMode) {
        1L -> false
        2L -> true
        else -> isSystemInDarkTheme()
    }
    AppTheme(useDarkTheme) {
        val modifier = Modifier
        Surface(color = MaterialTheme.colorScheme.background) {
            MainContentScreen(modifier, viewModel)
        }
    }
}

/**
 * 主要模块
 */
@Composable
fun MainContentScreen(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    val toastState = remember { ToastUIState() }
    val scope = rememberCoroutineScope()
    Box(modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
        Row(modifier.fillMaxWidth().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            val pages = Page.entries.toTypedArray()
            // 导航栏
            NavigationRail(modifier.fillMaxHeight().padding(start = 8.dp, end = 8.dp)) {
                Column(modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    pages.forEachIndexed { _, page ->
                        NavigationRailItem(
                            label = { Text(page.title) },
                            icon = { Icon(page.icon, contentDescription = page.title) },
                            selected = viewModel.uiPageIndex == page,
                            onClick = { viewModel.updateUiState(page) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
            // 主界面
            val content: @Composable (Page) -> Unit = { page ->
                when (page) {
                    Page.SIGNATURE_INFORMATION -> SignatureInformation(modifier, viewModel, toastState, scope)
                    Page.APK_INFORMATION -> ApkInformation(modifier, viewModel, toastState, scope)
                    Page.APK_SIGNATURE -> ApkSignature(modifier, viewModel, toastState, scope)
                    Page.SIGNATURE_GENERATION -> SignatureGeneration(modifier, scope)
                    Page.SET_UP -> SetUp(modifier, viewModel)
                }
            }
            // 淡入淡出切换页面
            Crossfade(targetState = viewModel.uiPageIndex, modifier = modifier.fillMaxHeight().fillMaxWidth(), content = content)
        }
        ToastUI(toastState)
    }
}

enum class Page(val title: String, val icon: ImageVector) {
    SIGNATURE_INFORMATION("签名信息", Icons.Rounded.Description),
    APK_INFORMATION("APK信息", Icons.Rounded.Android),
    APK_SIGNATURE("APK签名", Icons.Rounded.Pin),
    SIGNATURE_GENERATION("签名生成", Icons.Rounded.Key),
    SET_UP("设置", Icons.Rounded.Settings)
}

/**
 * 签名信息
 */
@Composable
private fun SignatureInformation(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    signature.SignatureInformation(modifier, viewModel, toastState, scope)
}

/**
 * APK信息
 */
@Composable
private fun ApkInformation(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    apk.ApkInformation(modifier, viewModel, toastState, scope)
}

/**
 * APK签名
 */
@Composable
private fun ApkSignature(modifier: Modifier = Modifier, viewModel: MainViewModel, toastState: ToastUIState, scope: CoroutineScope) {
    signature.ApkSignature(modifier, viewModel, toastState, scope)
}

/**
 * 签名生成
 */
@Composable
private fun SignatureGeneration(modifier: Modifier = Modifier, scope: CoroutineScope) {
    Box(modifier = modifier.padding(6.dp), contentAlignment = Alignment.Center) {
        LottieAnimation(scope, "files/lottie_404.json", modifier)
    }
}

/**
 * 设置
 */
@Composable
private fun SetUp(modifier: Modifier = Modifier, viewModel: MainViewModel) {
    setting.SetUp(modifier, viewModel)
}

/**
 * 加载json动画
 */
@OptIn(InternalResourceApi::class)
@Composable
fun LottieAnimation(scope: CoroutineScope, path: String, modifier: Modifier = Modifier) {
    var animation by remember { mutableStateOf<Animation?>(null) }
    scope.launch {
        val json = readResourceBytes(path).decodeToString()
        animation = Animation.makeFromString(json)
    }
    animation?.let { InfiniteAnimation(it, modifier.fillMaxSize()) }
}

@Composable
fun InfiniteAnimation(animation: Animation, modifier: Modifier) {
    val infiniteTransition = rememberInfiniteTransition()
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = animation.duration,
        animationSpec = infiniteRepeatable(
            animation = tween((animation.duration * 1000).roundToInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    val invalidationController = remember { InvalidationController() }
    animation.seekFrameTime(time, invalidationController)
    Canvas(modifier) {
        drawIntoCanvas {
            animation.render(
                canvas = it.nativeCanvas,
                dst = Rect.makeWH(size.width, size.height)
            )
        }
    }
}