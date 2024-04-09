import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import theme.AppTheme
import toast.ToastUI
import toast.ToastUIState
import ui.*
import vm.MainViewModel

@Composable
fun App() {
    val viewModel = remember { MainViewModel() }
    val useDarkTheme = when (viewModel.darkMode) {
        1L -> false
        2L -> true
        else -> isSystemInDarkTheme()
    }
    viewModel.initInternal()
    AppTheme(useDarkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainContentScreen(viewModel)
        }
    }
}

/**
 * 主要模块
 */
@Composable
fun MainContentScreen(viewModel: MainViewModel) {
    val toastState = remember { ToastUIState() }
    val scope = rememberCoroutineScope()
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically
        ) {
            val pages = Page.entries.toTypedArray()
            // 导航栏
            NavigationRail(Modifier.fillMaxHeight().padding(start = 8.dp, end = 8.dp)) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                    Page.SIGNATURE_INFORMATION -> SignatureInformation(viewModel, toastState, scope)
                    Page.APK_INFORMATION -> ApkInformation(viewModel, toastState, scope)
                    Page.APK_SIGNATURE -> ApkSignature(viewModel, toastState, scope)
                    Page.SIGNATURE_GENERATION -> SignatureGeneration(viewModel, toastState, scope)
                    Page.JUNK_CODE -> JunkCode(viewModel, toastState, scope)
                    Page.SET_UP -> SetUp(viewModel)
                }
            }
            // 淡入淡出切换页面
            Crossfade(targetState = viewModel.uiPageIndex, modifier = Modifier.fillMaxSize(), content = content)
        }
        ToastUI(toastState)
    }
}

enum class Page(val title: String, val icon: ImageVector) {
    SIGNATURE_INFORMATION("签名信息", Icons.Rounded.Description),
    APK_INFORMATION("APK信息", Icons.Rounded.Android),
    APK_SIGNATURE("APK签名", Icons.Rounded.Pin),
    SIGNATURE_GENERATION("签名生成", Icons.Rounded.Key),
    JUNK_CODE("垃圾代码", Icons.Rounded.DonutLarge),
    SET_UP("设置", Icons.Rounded.Settings)
}