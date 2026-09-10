package org.tool.kit.di

import org.koin.dsl.module
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase

/** 将业务用例绑定为可按需创建的对象，用例只依赖仓库接口。 */
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
