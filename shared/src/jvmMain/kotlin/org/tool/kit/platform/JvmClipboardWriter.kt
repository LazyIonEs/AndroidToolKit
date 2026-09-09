package org.tool.kit.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.feature.app.ClipboardWriter
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class JvmClipboardWriter(private val main: CoroutineDispatcher) : ClipboardWriter {
    override suspend fun write(value: String) = withContext(main) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
    }
}
