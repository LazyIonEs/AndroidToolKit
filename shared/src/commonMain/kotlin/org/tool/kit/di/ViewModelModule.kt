package org.tool.kit.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.tool.kit.vm.MainViewModel

fun viewModelModule() = module {
    viewModel { org.tool.kit.feature.apk.ApkInformationViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.signature.SignatureInformationViewModel(get(), get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.keystore.KeyStoreGenerationViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.settings.SettingsViewModel(get(), get(), get(), get()) }
    viewModel { org.tool.kit.feature.app.AppViewModel(get()) }
    viewModel { org.tool.kit.feature.update.UpdateViewModel(get(), get(), get(), get()) }
    viewModel { MainViewModel(get(), get(), get(), get(), get<org.tool.kit.app.AppBootstrap>().storageCapacity) }
}
