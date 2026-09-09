package org.tool.kit.domain.repository

import org.tool.kit.domain.signing.SignApkOutcome
import org.tool.kit.domain.signing.SignApkRequest

fun interface ApkSigningRepository {
    suspend fun sign(request: SignApkRequest): SignApkOutcome
}
