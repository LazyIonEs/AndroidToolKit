package org.tool.kit.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.tool.kit.vm.MainViewModel

fun viewModelModule() = module {
    viewModel { MainViewModel(get()) }
}
