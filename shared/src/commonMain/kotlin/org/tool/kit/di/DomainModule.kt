package org.tool.kit.di

import org.koin.dsl.module
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase

fun domainModule() = module {
    factory { GenerateKeyStoreUseCase(get()) }
}
