package org.tool.kit.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.tool.kit.model.FileSelectorType
import org.tool.kit.utils.checkFile
import java.net.URI
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.toPath

class DesktopFileSelection(private val io: CoroutineDispatcher) {
    suspend fun acceptPickerPath(path: String?, types: List<FileSelectorType>): String? = withContext(io) {
        path?.takeIf { types.toTypedArray().checkFile(it) }
    }

    suspend fun resolveDrop(uris: List<String>): List<Path> = withContext(io) {
        uris.mapNotNull { URI(it).toPath().takeIf { path -> path.exists(LinkOption.NOFOLLOW_LINKS) } }
    }
}
