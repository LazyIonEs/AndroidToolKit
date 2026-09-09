package org.tool.kit.migration

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withContext
import org.junit.Test
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.PathMetadata
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.model.ApkToolInfo as ApkSignature
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.vm.*
import org.tool.kit.core.validation.KeyAliasesValidation
import androidx.lifecycle.ViewModelStore
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class)
class Phase2ValidationTest {
    @Test fun aliasesRejectOlderAAfterAToBToAAndAfterDialogClose() = runTest {
        val keys = DeferredKeys()
        val validator = KeyAliasesValidation(backgroundScope, keys)
        for (password in listOf("A", "B", "A")) {
            validator.validate("fixture", password)
            runCurrent()
        }
        keys.aliases[0].complete(listOf("old-A"))
        keys.aliases[1].complete(listOf("old-B"))
        runCurrent()
        assertTrue(validator.state.value.pending)
        assertNull(validator.state.value.aliases)
        keys.aliases[2].complete(listOf("latest-A"))
        runCurrent()
        assertEquals(listOf("latest-A"), validator.state.value.aliases)
        validator.validate("another-store", "C")
        runCurrent()
        validator.reset()
        keys.aliases[3].complete(listOf("closed-dialog"))
        runCurrent()
        assertNull(validator.state.value.aliases)
        assertFalse(validator.state.value.pending)
    }

    @Test fun signingChecksRejectSlowPasswordAndPathResultsWithoutReloadingOnUnrelatedEdits() = runTest {
        val keys = DeferredKeys()
        var aliases: List<String>? = null
        val checks = LegacySignValidation(backgroundScope, AllPathsExist, keys) { aliases = it }
        var form = ApkSignature(_keyStorePath = "fixture", keyStorePassword = "store",
            keyStoreAlisaList = arrayListOf("alias"), keyStoreAlisaPassword = "old")
        checks.formChanged(form)
        runCurrent()
        form = form.copy(keyStoreAlisaPassword = "new")
        checks.formChanged(form)
        runCurrent()
        keys.passwords[1].complete(false)
        runCurrent()
        keys.passwords[0].complete(true)
        runCurrent()
        assertFalse(checks.state.value.aliasPasswordValid!!)
        checks.formChanged(form.copy(outputPath = "unrelated-output"))
        runCurrent()
        assertEquals(2, keys.passwords.size)

        checks.passwordChanged(form)
        runCurrent()
        // Changing the store invalidates both request streams, even if the old FFI/provider ignores cancellation.
        checks.formChanged(form.copy().also { it.keyStorePath = "new-store" })
        keys.aliases.single().complete(listOf("stale-alias"))
        keys.passwords.drop(2).forEach { it.complete(true) }
        runCurrent()
        assertNull(aliases)
        assertNull(checks.state.value.aliasPasswordValid)
    }

    @Test fun pathQueriesAreDeduplicatedAndRejectLateResultsForTheSameText() = runTest {
        val pending = mutableListOf<CompletableDeferred<PathMetadata>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String): PathMetadata = withContext(NonCancellable) {
                CompletableDeferred<PathMetadata>().also { pending += it }.await()
            }
        }
        val paths = LegacyPathChecks(backgroundScope, storage)
        val field = LegacyPathField.APK_TOOL_OUTPUT
        paths.validate(field, "A", PathKind.DIRECTORY)
        runCurrent()
        repeat(20) { paths.validate(field, "A", PathKind.DIRECTORY) }
        assertEquals(1, pending.size)
        paths.validate(field, "B", PathKind.DIRECTORY)
        runCurrent()
        paths.validate(field, "A", PathKind.DIRECTORY)
        runCurrent()
        pending[2].complete(PathMetadata(false, false))
        runCurrent()
        pending[0].complete(PathMetadata(false, true))
        pending[1].complete(PathMetadata(false, true))
        runCurrent()
        assertTrue(paths.state.value.getValue(field).isError)
        paths.validate(field, "", PathKind.DIRECTORY)
        assertFalse(paths.state.value.getValue(field).isError)
        assertFalse(paths.state.value.getValue(field).pending)
    }

    @Test fun settingsDraftsUpdateBeforeQueuedWritesAndFieldsDoNotOverwriteEachOther() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val dispatcher = StandardTestDispatcher(testScheduler)
        val settings = MapSettings()
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), dispatcher)
        val repository = org.tool.kit.data.repository.DefaultPreferencesRepository(source,
            org.tool.kit.core.coroutine.AppDispatchers(dispatcher, dispatcher, dispatcher))
        val sink = org.tool.kit.feature.app.AppEffectSink()
        val vm = org.tool.kit.feature.settings.SettingsViewModel(repository, AllPathsExist, sink, RecordingDesktopActions())
        val store = ViewModelStore().also { it.put("phase2", vm) }
        try {
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.OutputPath(" /draft/path "))
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.SignerSuffix("-draft"))
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.OutputPath(" /latest/path "))
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.SignerSuffix(""))
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.Theme(DarkThemeConfig.DARK))
            vm.onIntent(org.tool.kit.feature.settings.SettingsIntent.DuplicateRemoval(false))
            assertEquals(" /latest/path ", vm.uiState.value.preferences.userData.defaultOutputPath)
            assertEquals("", vm.uiState.value.preferences.userData.defaultSignerSuffix)
            assertEquals(0, settings.size, "Persistence has not run yet")
            runCurrent()
            assertEquals(" /latest/path ", repository.state.value.userData.defaultOutputPath)
            assertEquals("", repository.state.value.userData.defaultSignerSuffix)
            assertFalse(repository.state.value.userData.duplicateFileRemoval)
            assertEquals(" /latest/path ", vm.uiState.value.preferences.userData.defaultOutputPath)
            assertEquals("", vm.uiState.value.preferences.userData.defaultSignerSuffix)
            assertEquals("", settings.getString("user_data.defaultSignerSuffix", "missing"))
        } finally {
            store.clear()
            repository.close()
            sink.close()
            Dispatchers.resetMain()
        }
    }

    @Test fun explicitPageRefreshRechecksAPathThatChangedOnDisk() = runTest {
        var exists = false
        var reads = 0
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String): PathMetadata {
                reads++
                return PathMetadata(false, exists)
            }
        }
        val checks = LegacyPathChecks(backgroundScope, storage)
        val field = LegacyPathField.APK_TOOL_OUTPUT
        checks.validate(field, "same-path", PathKind.DIRECTORY)
        runCurrent()
        assertTrue(checks.state.value.getValue(field).isError)
        exists = true
        checks.refresh(field)
        assertTrue(checks.state.value.getValue(field).pending)
        runCurrent()
        assertFalse(checks.state.value.getValue(field).isError)
        assertEquals(2, reads)
    }
}

internal object AllPathsExist : StorageRepository {
    override suspend fun readCapacity() = StorageCapacity(1_000, 400)
    override suspend fun inspectPath(path: String) = PathMetadata(true, true)
}

internal object EmptyKeys : KeyStoreRepository {
    override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome = error("Unexpected key generation")
    override suspend fun loadAliases(path: String, password: String): List<String>? = null
    override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String) = false
}

private class DeferredKeys : KeyStoreRepository {
    override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome = error("Unexpected key generation")
    val aliases = mutableListOf<CompletableDeferred<List<String>?>>()
    val passwords = mutableListOf<CompletableDeferred<Boolean>>()
    override suspend fun loadAliases(path: String, password: String): List<String>? = withContext(NonCancellable) {
        CompletableDeferred<List<String>?>().also { aliases += it }.await()
    }
    override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean =
        withContext(NonCancellable) { CompletableDeferred<Boolean>().also { passwords += it }.await() }
}
