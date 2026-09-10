package org.tool.kit.domain.repository

import org.tool.kit.domain.signature.SignatureVerification

interface SignatureRepository {
    /** 验证 APK 并映射可展示的签名方案、证书或错误结果。 */
    suspend fun verifyApk(path: String): Result<SignatureVerification>
    /** 读取密钥库中指定别名的 X.509 证书并转换为展示模型。 */
    suspend fun verifyCertificate(path: String, password: String, alias: String): Result<SignatureVerification>
}
