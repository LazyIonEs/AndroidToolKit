package org.tool.kit.di

import org.koin.dsl.module
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase

fun domainModule() = module {
    factory { org.tool.kit.domain.usecase.ReadApkInformationUseCase(get()) }
    factory { GenerateKeyStoreUseCase(get()) }
    factory { org.tool.kit.domain.usecase.VerifySignatureUseCase(get()) }
}
