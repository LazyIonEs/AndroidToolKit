package org.tool.kit.domain.usecase

import org.tool.kit.domain.repository.SignatureRepository

class VerifySignatureUseCase(private val repository: SignatureRepository) {
    suspend fun apk(path: String) = repository.verifyApk(path)
    suspend fun certificate(path: String, password: String, alias: String) = repository.verifyCertificate(path, password, alias)
}
