package org.tool.kit.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.tool.kit.domain.repository.ApkSigningRepository
import org.tool.kit.domain.signing.SignApkRequest

class SignApkUseCase(private val repository: ApkSigningRepository) {
    /** 执行单个签名请求，保留已存在输出和签名失败等不同结果。 */
    suspend operator fun invoke(request: SignApkRequest) = repository.sign(request)

    /** 并发签名但按输入顺序返回结果，使通知和输出顺序不受完成先后影响。 */
    suspend fun batch(requests: List<SignApkRequest>) = coroutineScope {
        requests.map { request -> async { invoke(request) } }.awaitAll()
    }
}
