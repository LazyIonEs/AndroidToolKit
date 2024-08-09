package ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import file.FileSelectorType
import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.DarkThemeConfig
import utils.LottieAnimation
import utils.checkFile
import utils.toFileExtensions
import vm.MainViewModel

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/16 15:09
 * @Description : 通用UI
 * @Version     : 1.0
 */

/**
 * 文件选择按钮
 * @param value 输入框的值
 * @param expanded 是否折叠
 * @param fileSelectorType 文件选择类型
 * @param onFileSelector 文件选择回调
 */
@Composable
fun FileButton(
    value: String,
    expanded: Boolean,
    vararg fileSelectorType: FileSelectorType,
    onFileSelector: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    ExtendedFloatingActionButton(
        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
        onClick = {
            scope.launch {
                val file = FileKit.pickFile(
                    type = PickerType.File(fileSelectorType.toFileExtensions()),
                    mode = PickerMode.Single
                )
                if (fileSelectorType.checkFile(file?.path ?: return@launch)) {
                    onFileSelector(file.path ?: return@launch)
                }
            }
        }, icon = { Icon(Icons.Rounded.DriveFolderUpload, value) }, text = {
            Text(value)
        }, expanded = expanded
    )
}

/**
 * 文件输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param fileSelectorType 文件选择类型
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FileInput(
    value: String,
    label: String,
    isError: Boolean,
    vararg fileSelectorType: FileSelectorType,
    onValueChange: (String) -> Unit
) {
    FileInput(
        value = value,
        label = label,
        isError = isError,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
        trailingIcon = null,
        fileSelectorType = fileSelectorType,
        onValueChange = onValueChange
    )
}

/**
 * 文件输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param fileSelectorType 文件选择类型
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FileInput(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    vararg fileSelectorType: FileSelectorType,
    onValueChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurrentTextField(
            modifier = modifier.weight(1f),
            value = value,
            label = label,
            isError = isError,
            trailingIcon = trailingIcon,
            onValueChange = onValueChange,
        )
        SmallFloatingActionButton(onClick = {
            scope.launch {
                val file = FileKit.pickFile(
                    type = PickerType.File(fileSelectorType.toFileExtensions()),
                    mode = PickerMode.Single
                )
                if (fileSelectorType.checkFile(file?.path ?: return@launch)) {
                    onValueChange(file.path ?: return@launch)
                }
            }
        }) {
            Icon(Icons.Rounded.FolderOpen, "选择文件")
        }
    }
}

/**
 * 文件夹输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FolderInput(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        CurrentTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = value,
            label = label,
            isError = isError,
            onValueChange = onValueChange
        )
        val scope = rememberCoroutineScope()
        SmallFloatingActionButton(onClick = {
            scope.launch {
                val directory = FileKit.pickDirectory()
                onValueChange(directory?.path ?: return@launch)
            }
        }) {
            Icon(Icons.Rounded.FolderOpen, "选择文件夹")
        }
    }
}

/**
 * 字符串输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun StringInput(
    value: String,
    label: String,
    isError: Boolean,
    realOnly: Boolean = false,
    onValueChange: (String) -> Unit
) {
    CurrentTextField(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        value = value,
        label = label,
        realOnly = realOnly,
        isError = isError,
        onValueChange = onValueChange
    )
}

/**
 * 数字输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun IntInput(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit) {
    val pattern = remember { Regex("^\\d+\$") }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        value = value,
        onValueChange = { validityPeriod ->
            if (validityPeriod.isEmpty() || validityPeriod.matches(pattern)) {
                onValueChange(validityPeriod)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/**
 * 密码输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun PasswordInput(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        isError = isError,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

/**
 * 上传动画
 * @param dragging 是否在拖拽中
 * @param scope 协程作用域
 */
@Composable
fun UploadAnimate(dragging: Boolean, scope: CoroutineScope) {
    AnimatedVisibility(
        visible = dragging,
        enter = fadeIn() + slideIn(
            tween(
                durationMillis = 400, easing = LinearOutSlowInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) },
        exit = slideOut(
            tween(
                durationMillis = 400, easing = FastOutLinearInEasing
            )
        ) { fullSize -> IntOffset(fullSize.width, fullSize.height) } + fadeOut(),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
            )
        ) {
            LottieAnimation(scope, "files/upload.json")
        }
    }
}

/**
 * 加载中动画
 * @param visible 是否显示
 * @param scope 协程作用域
 */
@Composable
fun LoadingAnimate(visible: Boolean, viewModel: MainViewModel, scope: CoroutineScope) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp, end = 80.dp), contentAlignment = Alignment.Center
        ) {
            val useDarkTheme = when (viewModel.themeConfig.value) {
                DarkThemeConfig.LIGHT -> false
                DarkThemeConfig.DARK -> true
                DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
            }
            if (useDarkTheme) {
                LottieAnimation(scope, "files/lottie_loading_light.json")
            } else {
                LottieAnimation(scope, "files/lottie_loading_dark.json")
            }
        }
    }
}

/**
 * 通用输入框
 */
@Composable
private fun CurrentTextField(
    modifier: Modifier,
    value: String,
    label: String,
    isError: Boolean,
    realOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        singleLine = true,
        isError = isError,
        readOnly = realOnly,
        trailingIcon = trailingIcon
    )
}