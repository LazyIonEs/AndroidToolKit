package org.tool.kit.feature.ui

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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.tool.kit.utils.LottieAnimation

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/4/16 15:09
 * @Description : 通用UI
 * @Version     : 1.0
 */

/**
 * 文件输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onPickerRequest 请求平台文件选择器
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FileInput(
    value: String,
    label: String,
    isError: Boolean,
    onPickerRequest: () -> Unit,
    onValueChange: (String) -> Unit
) {
    FileInput(
        value = value,
        label = label,
        isError = isError,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp),
        trailingIcon = null,
        onPickerRequest = onPickerRequest,
        onValueChange = onValueChange
    )
}

/**
 * 文件输入框
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onPickerRequest 请求平台文件选择器
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FileInput(
    value: String,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    onPickerRequest: () -> Unit,
    onValueChange: (String) -> Unit,
) {
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
        SmallFloatingActionButton(onClick = onPickerRequest) {
            Icon(Icons.Rounded.FolderOpen, "FolderOpen")
        }
    }
}

/**
 * 文件夹输入框，手动输入和系统选择都由外部回调处理，本组件不访问文件系统。
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun FolderInput(
    value: String, label: String, isError: Boolean,
    onPickerRequest: () -> Unit, onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurrentTextField(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 3.dp).weight(1f),
            value = value,
            label = label,
            isError = isError,
            onValueChange = onValueChange
        )
        SmallFloatingActionButton(onClick = onPickerRequest) {
            Icon(Icons.Rounded.FolderOpen, "FolderOpen")
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
    trailingIcon: @Composable (() -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    CurrentTextField(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 72.dp, bottom = 3.dp),
        value = value,
        label = label,
        realOnly = realOnly,
        isError = isError,
        onValueChange = onValueChange,
        trailingIcon = trailingIcon
    )
}

/**
 * 数字输入框，仅接收纯数字或空字符串，保留清空输入的编辑中间态。
 * @param value 输入框的值
 * @param label 输入框的标签
 * @param isError 是否错误
 * @param onValueChange 输入值改变回调
 */
@Composable
fun IntInput(value: String, label: String, isError: Boolean, onValueChange: (String) -> Unit) {
    val pattern = remember { Regex("^\\d+$") }
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
 */
@Composable
fun UploadAnimate(dragging: Boolean) {
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
            LottieAnimation("files/upload.json")
        }
    }
}

/**
 * 页面加载遮罩，拦截本页内容点击；放置范围由 FeaturePage 决定，侧栏仍可操作。
 * @param visible 是否显示
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoadingAnimate(visible: Boolean, useDarkTheme: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandHorizontally(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onClick { } // 拦截点击事件
            , contentAlignment = Alignment.Center
        ) {
            if (useDarkTheme) {
                LottieAnimation("files/lottie_loading_light.json")
            } else {
                LottieAnimation("files/lottie_loading_dark.json")
            }
        }
    }
}

/**
 * 无内部表单副本的通用输入框，值和错误标记均由调用方提供。
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
