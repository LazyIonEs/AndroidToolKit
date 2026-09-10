package org.tool.kit.di

import org.koin.dsl.module
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase

fun domainModule() = module {
    factory { org.tool.kit.domain.usecase.ScanBuildCachesUseCase(get()) }
    factory { org.tool.kit.domain.usecase.DeleteBuildCachesUseCase(get()) }
    factory { org.tool.kit.domain.usecase.GenerateJunkCodeUseCase(get()) }
    factory { org.tool.kit.domain.usecase.EstimateJunkSizeUseCase(get()) }
    factory { org.tool.kit.domain.usecase.GenerateIconsUseCase(get(), get()) }
    factory { org.tool.kit.domain.usecase.BuildApkUseCase(get(), get(), get()) }
    factory { org.tool.kit.domain.usecase.SignApkUseCase(get()) }
    factory { org.tool.kit.domain.usecase.ReadApkInformationUseCase(get()) }
    factory { GenerateKeyStoreUseCase(get()) }
    factory { org.tool.kit.domain.usecase.VerifySignatureUseCase(get()) }
}
