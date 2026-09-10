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
    /** 在 IO 线程按页面允许的扩展名或可执行性筛选路径；取消选择返回 null。 */
    suspend fun acceptPickerPath(path: String?, types: List<FileSelectorType>): String? = withContext(io) {
        path?.takeIf { types.toTypedArray().checkFile(it) }
    }

    /** 把文件 URI 转为路径并按原顺序过滤不存在的条目，存在性判断不跟随符号链接。 */
    suspend fun resolveDrop(uris: List<String>): List<Path> = withContext(io) {
        uris.mapNotNull { URI(it).toPath().takeIf { path -> path.exists(LinkOption.NOFOLLOW_LINKS) } }
    }
}
