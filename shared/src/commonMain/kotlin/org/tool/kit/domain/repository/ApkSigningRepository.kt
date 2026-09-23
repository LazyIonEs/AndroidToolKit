package org.tool.kit.domain.repository

import org.tool.kit.domain.signing.SignApkOutcome
import org.tool.kit.domain.signing.SignApkRequest

fun interface ApkSigningRepository {
    /** 根据覆盖、对齐和签名方案执行签名，并区分输出冲突与执行失败。 */
    suspend fun sign(request: SignApkRequest): SignApkOutcome
}
