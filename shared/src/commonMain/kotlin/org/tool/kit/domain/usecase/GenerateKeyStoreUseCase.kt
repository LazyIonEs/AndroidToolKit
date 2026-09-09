package org.tool.kit.domain.usecase

import org.tool.kit.domain.keystore.GenerateKeyStoreRequest
import org.tool.kit.domain.repository.KeyStoreRepository

class GenerateKeyStoreUseCase(private val repository: KeyStoreRepository) {
    suspend operator fun invoke(request: GenerateKeyStoreRequest) = repository.generate(request)
}
