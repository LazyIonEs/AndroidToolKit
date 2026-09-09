package org.tool.kit.feature.signature.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.tool.kit.feature.keystore.KeyStoreGenerationRoute
import org.tool.kit.feature.keystore.KeyStoreGenerationViewModel

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/20 17:14
 */
fun EntryProviderScope<NavKey>.signatureGenerationEntry(viewModel: KeyStoreGenerationViewModel) {
    entry<SignatureGenerationNavKey> {
        KeyStoreGenerationRoute(viewModel = viewModel)
    }
}