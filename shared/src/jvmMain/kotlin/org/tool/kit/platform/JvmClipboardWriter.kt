package org.tool.kit.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.feature.app.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class JvmClipboardWriter(private val main: CoroutineDispatcher) : ClipboardWriter {
    /** 切换到主线程写系统剪贴板，让页面可在写入完成后再显示成功提示。 */
    override suspend fun write(value: String) = withContext(main) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}
