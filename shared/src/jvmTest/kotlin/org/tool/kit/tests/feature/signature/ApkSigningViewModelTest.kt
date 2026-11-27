package org.tool.kit.tests.feature.signature

import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.SignApkUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.signature.*
import org.tool.kit.feature.signature.ApkSigningIntent.*
import org.tool.kit.model.SignaturePolicy
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.tests.support.EmptyKeys
import org.tool.kit.tests.support.TestSigningProfiles

@OptIn(ExperimentalCoroutinesApi::class)
class ApkSigningViewModelTest {
    @Test fun submitFreezesAllFieldsAndSettingsAndRejectsDuplicates() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fillSigning(f.vm); runCurrent()
            f.preferences.change(PreferenceChange.SignerSuffix(" suffix"))
            f.preferences.change(PreferenceChange.DuplicateRemoval(false))
            f.preferences.change(PreferenceChange.AlignFileSize(false))
            f.preferences.change(PreferenceChange.HuaweiAlignment(false))
            f.vm.onIntent(PolicyChanged(SignaturePolicy.V4)); f.vm.onIntent(V4NameChanged("custom.idsig"))
            f.vm.onIntent(Submit); f.vm.onIntent(Submit); runCurrent()
            val captured = f.signer.requests.single()
            assertEquals("/input 中文.apk", captured.inputPath)
            assertEquals("/output", captured.outputDirectory)
            assertEquals(" prefix ", captured.prefix)
            assertEquals(" suffix", captured.suffix)
            assertFalse(captured.overwrite); assertFalse(captured.userAlign); assertFalse(captured.huaweiAlignment)
            assertTrue(captured.align); assertEquals(ApkSigningPolicy.V4, captured.policy)
            assertEquals("custom.idsig", captured.v4FileName)
            assertEquals(SigningCredentials("/key.jks", "store password", "first", "alias password"), captured.credentials)
            f.vm.onIntent(PrefixChanged("next")); f.vm.onIntent(StorePasswordChanged("next"))
            f.preferences.change(PreferenceChange.OutputPath("/next")); runCurrent()
            f.signer.results.single().complete(SignApkOutcome.Success("/output/signed.apk", true)); runCurrent()
            f.vm.uiState.first { !it.busy }
            assertFalse(f.vm.uiState.value.busy)
            assertEquals("next", f.vm.uiState.value.form.outputPrefix)
            assertEquals("/next", f.vm.uiState.value.form.outputPath)
            assertEquals("store password", captured.credentials.storePassword)
            assertEquals(listOf(captured.credentials), f.profiles.saves)
            assertEquals(listOf(SavedSigningProfile("/key.jks", "first")), f.vm.uiState.value.savedSigningProfiles)
            assertEquals(UiMessage.Resource(Res.string.apk_is_signed_successfully), events.single().snackbar.message)
            assertEquals(SnackbarAction.OpenDirectory("/output/signed.apk"), events.single().snackbar.action)
            assertEquals("apk-signing", events.single().originFeature)
        } finally { f.close() }
    }

    @Test fun allExpandsInPresetOrderAndOneNotificationTargetsLastInput() = runTest {
        val inspected = mutableListOf<String>()
        val f = SigningFixture(StandardTestDispatcher(testScheduler), object : StorageRepository by SigningStorage {
            override suspend fun inspectPath(path: String): PathMetadata { inspected += path; return SigningStorage.inspectPath(path) }
        })
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fillSigning(f.vm); runCurrent()
            f.vm.onIntent(ApkPathChanged("All")); f.vm.onIntent(PolicyChanged(SignaturePolicy.V4)); runCurrent()
            f.vm.onIntent(Submit); f.vm.onIntent(Submit); runCurrent()
            assertEquals(listOf("oppo", "vivo", "huawei", "xiaomi", "qq", "honor"), f.signer.requests.map { it.inputPath })
            assertFalse(inspected.contains("All"))
            assertEquals(1, f.signer.requests.map { it.v4FileName }.distinct().size)
            f.signer.results.withIndex().reversed().forEach { (i, result) -> result.complete(SignApkOutcome.Success("output-$i", true)) }
            runCurrent()
            assertEquals(1, events.size)
            assertEquals(SnackbarAction.OpenDirectory("output-5"), events.single().snackbar.action)
            assertEquals(1, f.profiles.saves.size)
            assertFalse(f.vm.uiState.value.busy)
        } finally { f.close() }
    }

    @Test fun partialBatchFailureOrMissingOutputUsesOriginalGenericFailure() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fillSigning(f.vm); f.vm.onIntent(ApkPathChanged("All")); runCurrent()
            for (failure in listOf(SignApkOutcome.Failure("detail"), SignApkOutcome.Success("missing", false))) {
                val start = f.signer.results.size
                f.vm.onIntent(Submit); runCurrent()
                f.signer.results.drop(start).forEachIndexed { i, result -> result.complete(if (i == 1) failure else SignApkOutcome.Success("$i", true)) }
                runCurrent()
                assertEquals(UiMessage.Resource(Res.string.signature_failed), events.last().snackbar.message)
                assertFalse(f.vm.uiState.value.busy)
            }
            assertEquals(2, events.size)
            assertNotEquals(events[0].effectId, events[1].effectId)
            assertTrue(f.profiles.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun singleFailureKeepsMessageIncludingEmptyStringAndRetryClearsBusy() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fillSigning(f.vm); runCurrent()
            for (message in listOf(null, "", "wrong password")) {
                f.vm.onIntent(Submit); runCurrent()
                f.signer.results.last().complete(SignApkOutcome.Failure(message)); runCurrent()
                assertEquals(message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.signature_failed), events.last().snackbar.message)
                assertFalse(f.vm.uiState.value.busy)
            }
            f.vm.onIntent(Submit); runCurrent()
            f.signer.results.last().complete(SignApkOutcome.Success("absent", false)); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.apk_is_signed_successfully), events.last().snackbar.message)
            assertTrue(f.profiles.saves.isEmpty())
        } finally { f.close() }
    }

    @Test fun requiredAndInvalidFieldsUseCheckErrorBeforeSigning() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent()
            val bad = listOf(ApkPathChanged(" "), OutputPathChanged(" "), KeyPathChanged(" "), StorePasswordChanged(" "),
                AliasPasswordChanged(" "), ApkPathChanged("/missing.apk"), OutputPathChanged("/missing"), KeyPathChanged("/missing.jks"))
            for (field in bad) {
                fillSigning(f.vm); runCurrent(); f.vm.onIntent(field); runCurrent(); f.vm.onIntent(Submit); runCurrent()
                assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            }
            assertTrue(f.signer.requests.isEmpty())
            f.vm.onIntent(PolicyChanged(SignaturePolicy.V2Only)); f.vm.onIntent(PolicyChanged(SignaturePolicy.V2Only)); runCurrent()
            assertEquals(List(2) { UiMessage.Resource(Res.string.v2_tips) }, events.takeLast(2).map { it.snackbar.message })
        } finally { f.close() }
    }

    @Test fun droppedFilesPickFirstApkAndKeyAndUseSameResetAndNameRules() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        try {
            runCurrent(); fillSigning(f.vm); runCurrent()
            f.vm.onIntent(FilesDropped(listOf("/readme.txt", "/first.JKS", "/first.APK", "/second.apk", "/second.keystore"))); runCurrent()
            assertEquals("/second.apk", f.vm.uiState.value.form.apkPath)
            assertEquals(SigningCredentialsUi(path = "/second.keystore"), f.vm.uiState.value.form.credentials)
            assertEquals(" prefix -second.apk.idsig", f.vm.uiState.value.form.v4FileName)
        } finally { f.close() }
    }

    @Test fun latePathAndNameResultsCannotOverrideNewPrefixManualNameOrNewPath() = runTest {
        val pending = mutableListOf<Pair<String, CompletableDeferred<PathMetadata>>>()
        val storage = object : StorageRepository by SigningStorage {
            override suspend fun inspectPath(path: String) = withContext(NonCancellable) {
                CompletableDeferred<PathMetadata>().also { pending += path to it }.await()
            }
        }
        val f = SigningFixture(StandardTestDispatcher(testScheduler), storage)
        try {
            runCurrent(); f.vm.onIntent(ApkPathChanged("A.apk")); runCurrent()
            f.vm.onIntent(PrefixChanged("old")); runCurrent()
            f.vm.onIntent(ApkPathChanged("B.apk")); f.vm.onIntent(PrefixChanged("new")); runCurrent()
            pending.filter { it.first == "B.apk" }.forEach { it.second.complete(PathMetadata(true, false, "B.apk")) }; runCurrent()
            assertEquals("new-B.apk.idsig", f.vm.uiState.value.form.v4FileName)
            f.vm.onIntent(V4NameChanged("manual.idsig"))
            pending.forEach { it.second.complete(PathMetadata(false, false, "A.apk")) }; runCurrent()
            assertEquals("manual.idsig", f.vm.uiState.value.form.v4FileName)
            assertFalse(f.vm.uiState.value.validation.apkError)
            f.vm.onIntent(ApkPathChanged("A.apk")); runCurrent(); f.vm.onIntent(V4NameChanged("last.idsig"))
            pending.forEach { it.second.complete(PathMetadata(true, false, "A.apk")) }; runCurrent()
            assertEquals("last.idsig", f.vm.uiState.value.form.v4FileName)
        } finally { pending.forEach { it.second.complete(PathMetadata(false, false)) }; f.close() }
    }

    @Test fun closedWindowDropsNonCooperativeCompletionAndCancellationEffects() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        f.signer.nonCooperative = true
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fillSigning(f.vm); runCurrent(); f.vm.onIntent(Submit); runCurrent()
            f.store.clear(); runCurrent(); assertFalse(f.vm.uiState.value.busy)
            f.signer.results.single().complete(SignApkOutcome.Success("late", true)); runCurrent()
            assertFalse(f.vm.uiState.value.busy); assertTrue(events.isEmpty())
        } finally { f.close() }
    }

    @Test fun staleAliasesAndPasswordValidationCannotCommitAfterKeyChange() = runTest {
        val keys = DeferredSigningKeys()
        val f = SigningFixture(StandardTestDispatcher(testScheduler), keys = keys)
        try {
            runCurrent(); fillSigning(f.vm); runCurrent()
            f.vm.onIntent(StorePasswordChanged("new")); runCurrent()
            keys.aliases.last().complete(listOf("current")); runCurrent()
            keys.aliases.first().complete(listOf("stale")); runCurrent()
            assertEquals(listOf("current"), f.vm.uiState.value.form.credentials.aliases)
            f.vm.onIntent(KeyPathChanged("/new.jks")); runCurrent()
            keys.passwords.forEach { it.complete(false) }; runCurrent()
            assertEquals(SigningCredentialsUi(path = "/new.jks"), f.vm.uiState.value.form.credentials)
            assertNull(f.vm.uiState.value.validation.credentials.aliasPasswordValid)
        } finally { keys.aliases.forEach { it.complete(null) }; keys.passwords.forEach { it.complete(false) }; f.close() }
    }

    @Test fun selectingSavedProfileRestoresAliasByNameAndOptOutKeepsHistory() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler))
        val saved = SigningCredentials("/saved.jks", "saved store password", "second", "saved alias password")
        val profile = SavedSigningProfile(saved.path, "second")
        try {
            f.profiles.save(saved)
            fillSigning(f.vm)
            f.vm.onIntent(Refresh)
            runCurrent()
            assertTrue(f.preferences.state.value.saveSigningProfiles)
            assertEquals(listOf(profile), f.vm.uiState.value.savedSigningProfiles)

            f.preferences.change(PreferenceChange.SaveSigningProfiles(false))
            f.vm.onIntent(SigningProfileSelected(profile))
            runCurrent()
            val credentials = f.vm.uiState.value.form.credentials
            assertEquals(saved.path, credentials.path)
            assertEquals(saved.storePassword, credentials.storePassword)
            assertEquals(saved.alias, credentials.aliases?.getOrNull(credentials.aliasIndex))
            assertEquals(saved.aliasPassword, credentials.aliasPassword)
            assertEquals(true, f.vm.uiState.value.validation.credentials.aliasPasswordValid)
            f.vm.onIntent(AliasPasswordChanged("new alias password"))
            runCurrent()
            f.vm.onIntent(Submit)
            runCurrent()
            f.signer.results.single().complete(SignApkOutcome.Success("signed.apk", true))
            runCurrent()
            assertEquals(listOf(saved), f.profiles.saves)
            assertEquals(listOf(profile), f.vm.uiState.value.savedSigningProfiles)
        } finally { f.close() }
    }

    @Test fun typingOverridesAnInFlightProfileSelection() = runTest {
        val selection = CompletableDeferred<SigningCredentials?>()
        val saved = SigningCredentials("/saved.jks", "store", "second", "alias")
        val f = SigningFixture(StandardTestDispatcher(testScheduler), overrideProfiles = object : SigningProfileRepository by TestSigningProfiles() {
            override suspend fun load(profile: SavedSigningProfile) = withContext(NonCancellable) { selection.await() }
        })
        try {
            f.vm.onIntent(SigningProfileSelected(SavedSigningProfile(saved.path, "second")))
            runCurrent()
            f.vm.onIntent(KeyPathChanged("/new.jks"))
            selection.complete(saved)
            runCurrent()
            assertEquals(SigningCredentialsUi(path = "/new.jks"), f.vm.uiState.value.form.credentials)
            assertFalse(f.vm.uiState.value.validation.pending)
        } finally { selection.complete(null); f.close() }
    }

    @Test fun savingFailureStillReportsTheSignedApkWithItsOutputAction() = runTest {
        val f = SigningFixture(StandardTestDispatcher(testScheduler), overrideProfiles = object : SigningProfileRepository by TestSigningProfiles() {
            override suspend fun save(credentials: SigningCredentials): List<SavedSigningProfile> = error("Unavailable credential store")
        })
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            fillSigning(f.vm)
            runCurrent()
            f.vm.onIntent(Submit)
            runCurrent()
            f.signer.results.single().complete(SignApkOutcome.Success("signed.apk", true))
            runCurrent()
            assertEquals(UiMessage.Resource(Res.string.apk_signed_profile_save_failed), events.single().snackbar.message)
            assertEquals(SnackbarAction.OpenDirectory("signed.apk"), events.single().snackbar.action)
            assertFalse(f.vm.uiState.value.busy)
        } finally { f.close() }
    }
}

private object SigningStorage : StorageRepository {
    override suspend fun readCapacity() = StorageCapacity(0, 0)
    override suspend fun inspectPath(path: String) = PathMetadata(!path.contains("missing"), !path.contains("missing"), path.substringAfterLast('/'))
}
private class SigningPreferences : PreferencesRepository {
    override val state = MutableStateFlow(PreferencesSnapshot(ready = true))
    override fun change(change: PreferenceChange): PreferencesSnapshot {
        val old = state.value
        val next = old.changed(change)
        state.value = next.copy(outputPathVersion = old.outputPathVersion + if (next.userData.defaultOutputPath != old.userData.defaultOutputPath) 1 else 0)
        return state.value
    }
    override suspend fun awaitReady() = state.value
}
private class ControlledSigning : ApkSigningRepository {
    val requests = mutableListOf<SignApkRequest>()
    val results = mutableListOf<CompletableDeferred<SignApkOutcome>>()
    var nonCooperative = false
    override suspend fun sign(request: SignApkRequest): SignApkOutcome {
        requests += request
        val result = CompletableDeferred<SignApkOutcome>().also { results += it }
        return if (nonCooperative) withContext(NonCancellable) { result.await() } else result.await()
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
private class SigningFixture(dispatcher: TestDispatcher, storage: StorageRepository = SigningStorage,
    keys: KeyStoreRepository = object : KeyStoreRepository by EmptyKeys {
        override suspend fun loadAliases(path: String, password: String) = listOf("first", "second")
        override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String) = true
    }, overrideProfiles: SigningProfileRepository? = null) : AutoCloseable {
    val preferences = SigningPreferences()
    val effects = AppEffectSink()
    val signer = ControlledSigning()
    val profiles = TestSigningProfiles()
    val store = ViewModelStore()
    val vm: ApkSigningViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = ApkSigningViewModel(SignApkUseCase(signer), preferences, storage, keys, effects,
            SigningPresets(listOf("oppo", "vivo", "huawei", "xiaomi", "qq", "honor", "All").map { SigningPreset(it, it) }, "All", "huawei"),
            overrideProfiles ?: profiles)
        store.put("signing", vm)
    }
    override fun close() { store.clear(); effects.close(); Dispatchers.resetMain() }
}
private fun fillSigning(vm: ApkSigningViewModel) {
    listOf(ApkPathChanged("/input 中文.apk"), OutputPathChanged("/output"), PrefixChanged(" prefix "),
        KeyPathChanged("/key.jks"), StorePasswordChanged("store password"), AliasPasswordChanged("alias password")).forEach(vm::onIntent)
}

private class DeferredSigningKeys : KeyStoreRepository {
    override suspend fun generate(request: org.tool.kit.domain.keystore.GenerateKeyStoreRequest): org.tool.kit.domain.keystore.GenerateKeyStoreOutcome = error("Unexpected key generation")
    val aliases = mutableListOf<CompletableDeferred<List<String>?>>()
    val passwords = mutableListOf<CompletableDeferred<Boolean>>()
    override suspend fun loadAliases(path: String, password: String): List<String>? = withContext(NonCancellable) {
        CompletableDeferred<List<String>?>().also { aliases += it }.await()
    }
    override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean =
        withContext(NonCancellable) { CompletableDeferred<Boolean>().also { passwords += it }.await() }
}
