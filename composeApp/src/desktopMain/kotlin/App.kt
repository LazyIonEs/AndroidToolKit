import androidx.compose.animation.Crossfade
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import apk.information.ApkInformation
import setting.SetUp
import apk.signature.ApkSignature
import signature.generation.SignatureGeneration
import signature.information.SignatureInformation
import theme.AppTheme
import toast.ToastUI
import toast.ToastUIState
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pages = Page.entries.toTypedArray()
            // 导航栏
            NavigationRail(modifier.fillMaxHeight().padding(start = 8.dp, end = 8.dp)) {
                Column(
                    modifier = modifier.fillMaxHeight(),
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
                    Page.SIGNATURE_INFORMATION -> SignatureInformation(modifier, viewModel, toastState, scope)
                    Page.APK_INFORMATION -> ApkInformation(modifier, viewModel, toastState, scope)
                    Page.APK_SIGNATURE -> ApkSignature(modifier, viewModel, toastState, scope)
                    Page.SIGNATURE_GENERATION -> SignatureGeneration(modifier, viewModel, toastState, scope)
                    Page.SET_UP -> SetUp(modifier, viewModel)
                }
            }
            // 淡入淡出切换页面
            Crossfade(targetState = viewModel.uiPageIndex, modifier = modifier.fillMaxSize(), content = content)
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