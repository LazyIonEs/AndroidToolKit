package org.tool.kit.domain.usecase

import org.tool.kit.domain.keystore.GenerateKeyStoreRequest
import org.tool.kit.domain.repository.KeyStoreRepository

class GenerateKeyStoreUseCase(private val repository: KeyStoreRepository) {
    /** 将已收集的证书和密钥参数交给仓库生成，返回输出路径或失败原因。 */
    suspend operator fun invoke(request: GenerateKeyStoreRequest) = repository.generate(request)
}
