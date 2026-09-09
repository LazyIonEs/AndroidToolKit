package org.tool.kit.domain.repository

import org.tool.kit.domain.signature.SignatureVerification

interface SignatureRepository {
    suspend fun verifyApk(path: String): Result<SignatureVerification>
    suspend fun verifyCertificate(path: String, password: String, alias: String): Result<SignatureVerification>
}
