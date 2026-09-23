package org.tool.kit.domain.usecase

import org.tool.kit.domain.repository.SignatureRepository

class VerifySignatureUseCase(private val repository: SignatureRepository) {
    /** 读取 APK 的签名校验结果及签名证书信息。 */
    suspend fun apk(path: String) = repository.verifyApk(path)
    /** 使用密钥库密码读取指定别名的证书信息，密码不会进入展示结果。 */
    suspend fun certificate(path: String, password: String, alias: String) = repository.verifyCertificate(path, password, alias)
}
