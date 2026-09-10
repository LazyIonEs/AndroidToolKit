package org.tool.kit.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 声明页面和根级 ViewModel；实际实例由调用位置的 ViewModelStoreOwner 管理。 */
fun viewModelModule() = module {
    viewModel { org.tool.kit.feature.cleaner.CleanerViewModel(get(), get(), get(), get(), get<org.tool.kit.app.AppBootstrap>().storageCapacity) }
    viewModel { org.tool.kit.feature.junk.JunkCodeViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.iconfactory.IconFactoryViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.apk.ApkToolViewModel(get(), get(), get(), get(), get(),
        get<org.tool.kit.feature.signature.SigningPresets>().huaweiPath) }
    viewModel { org.tool.kit.feature.signature.ApkSigningViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.apk.ApkInformationViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.signature.SignatureInformationViewModel(get(), get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.keystore.KeyStoreGenerationViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.setting.SettingsViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.app.AppViewModel(get()) }
    viewModel { org.tool.kit.feature.update.UpdateViewModel(get(), get(), get(), get()) }
}
