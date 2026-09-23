package org.tool.kit.feature.signature.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import org.tool.kit.feature.keystore.KeyStoreGenerationRoute

/**
 * @author      : LazyIonEs
 * @description : 密钥库生成页的导航条目注册，将导航键连接到 Route
 * @createDate  : 2026/1/20 17:14
 */
fun EntryProviderScope<NavKey>.signatureGenerationEntry() {
    entry<SignatureGenerationNavKey> {
        KeyStoreGenerationRoute()
    }
}
