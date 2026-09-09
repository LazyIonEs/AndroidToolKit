package org.tool.kit.feature.signature.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.russhwolf.settings.ExperimentalSettingsApi
import org.tool.kit.feature.signature.SignatureInformationRoute
import org.tool.kit.feature.signature.SignatureInformationViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
@OptIn(ExperimentalSettingsApi::class)
fun EntryProviderScope<NavKey>.signatureInformationEntry(viewModel: SignatureInformationViewModel) {
    entry<SignatureInformationNavKey> {
        SignatureInformationRoute(viewModel = viewModel)
    }
}