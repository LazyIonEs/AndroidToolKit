package org.tool.kit.domain.repository

import org.tool.kit.domain.icon.GenerateIconsRequest
import org.tool.kit.domain.icon.IconOutputFiles

interface IconOutputs {
    /** Serialize outputs for a complete request, including cleanup after a non-cancellable native call. */
    suspend fun <T> use(request: GenerateIconsRequest, block: suspend (IconOutputSession) -> T): T
}

interface IconOutputSession {
    val outputDirectory: String
    /** Retain final outputs; clean only this density's reserved _resize path in finally. */
    suspend fun <T> density(name: String, suffix: String, block: suspend (IconOutputFiles) -> T): T
}
