package org.tool.kit.vm

import kotlinx.coroutines.CoroutineScope
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.feature.signature.SigningCredentialsUi
import org.tool.kit.feature.signature.SigningCredentialsValidation
import org.tool.kit.model.Sign

/** Temporary adapter for ApkTool's mutable form, removed in Phase 6. */
class LegacySignValidation(scope: CoroutineScope, storage: StorageRepository, keyStores: KeyStoreRepository,
    onAliases: (List<String>?) -> Unit) {
    private val delegate = SigningCredentialsValidation(scope, storage, keyStores, onAliases)
    val state = delegate.state
    private fun Sign.snapshot() = SigningCredentialsUi(keyStorePath, keyStorePassword,
        keyStoreAlisaList?.toList(), keyStoreAlisaIndex, keyStoreAlisaPassword)
    fun refreshAliasPassword(form: Sign) = delegate.refreshAliasPassword(form.snapshot())
    fun formChanged(form: Sign) = delegate.formChanged(form.snapshot())
    fun passwordChanged(form: Sign) = delegate.passwordChanged(form.snapshot())
}
