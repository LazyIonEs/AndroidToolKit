package org.tool.kit.feature.app

import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

sealed interface UiMessage {
    data class Text(val value: String) : UiMessage
    data class Resource(val value: StringResource) : UiMessage
}
sealed interface SnackbarAction {
    data class OpenDirectory(val path: String?) : SnackbarAction
}
data class SnackbarMessage(
    val message: UiMessage,
    val actionLabel: String? = null,
    val withDismissAction: Boolean = false,
    val duration: SnackbarDuration = SnackbarDuration.Short,
    val action: SnackbarAction? = null,
)
/** 携带来源、业务操作编号和全局消息编号的一次性 UI 消息。 */
data class AppEffect(val originFeature: String, val operationId: Long, val effectId: Long, val snackbar: SnackbarMessage)

/** Single window mailbox; closed only with the app session, never by a Route. */
@OptIn(ExperimentalAtomicApi::class)
class AppEffectSink {
    private val sequence = AtomicLong(0)
    private val mailbox = Channel<AppEffect>(64)
    val effects = mailbox.receiveAsFlow()
    /** 向有界消息队列发送提示；队列已关闭时返回 false，排队等待仍响应协程取消。 */
    suspend fun send(origin: String, message: SnackbarMessage, operationId: Long = 0): Boolean {
        return try {
            mailbox.send(AppEffect(origin, operationId, sequence.addAndFetch(1), message))
            true
        } catch (_: kotlinx.coroutines.channels.ClosedSendChannelException) { false }
    }
    /** 停止接收新消息；只在应用会话结束时调用，普通页面离开不应关闭通道。 */
    fun close() = mailbox.close()
}

/** 页面可请求的桌面行为边界，由平台实现处理真实文件和系统调用。 */
interface DesktopActionHandler {
    suspend fun openDirectory(path: String?)
    /** 尝试打开安装包，返回是否成功交给系统处理，供调用方决定是否退出应用。 */
    suspend fun openInstaller(path: String): Boolean
    suspend fun logFilePath(): String?
    fun browse(url: String)
    fun exitAfterInstall()
}
