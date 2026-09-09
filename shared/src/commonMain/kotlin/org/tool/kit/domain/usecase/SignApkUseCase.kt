package org.tool.kit.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.tool.kit.domain.repository.ApkSigningRepository
import org.tool.kit.domain.signing.SignApkRequest

class SignApkUseCase(private val repository: ApkSigningRepository) {
    suspend operator fun invoke(request: SignApkRequest) = repository.sign(request)

    /** Input order is also the notification/output order, regardless of completion order. */
    suspend fun batch(requests: List<SignApkRequest>) = coroutineScope {
        requests.map { request -> async { invoke(request) } }.awaitAll()
    }
}
