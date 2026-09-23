package org.tool.kit.tests.feature.signature

import androidx.lifecycle.ViewModelStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.signature.*
import org.tool.kit.domain.usecase.VerifySignatureUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.signature.*
import org.tool.kit.feature.signature.SignatureInformationIntent.*
import org.tool.kit.model.CopyMode
import org.tool.kit.shared.generated.resources.*
import org.tool.kit.tests.support.ControlledClipboard
import org.tool.kit.tests.support.EmptyKeys
import org.tool.kit.tests.support.signatureFixture

@OptIn(ExperimentalCoroutinesApi::class)
class SignatureInformationViewModelTest {
    @Test fun apkResultsRetainFailedFlagAndOldRequestsCannotReplaceNewestResult() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            runCurrent()
            f.vm.onIntent(VerifyApk("A.apk")); assertTrue(f.vm.uiState.value.busy); runCurrent()
            f.vm.onIntent(VerifyApk("B.apk")); runCurrent()
            f.repository.results[1].complete(Result.success(signatureFixture.copy(path = "B.apk"))); runCurrent()
            assertFalse(f.vm.uiState.value.busy)
            assertFalse(f.vm.uiState.value.result!!.isSuccess)
            assertTrue(f.vm.hasResult.value)
            f.repository.results[0].complete(Result.success(signatureFixture.copy(path = "A.apk"))); runCurrent()
            assertEquals("B.apk", f.vm.uiState.value.result!!.path)
        }
    }

    @Test fun aliasesRejectSlowFileAndPasswordResultsAndClosingResetsSessionOnly() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            runCurrent()
            f.vm.onIntent(VerifyApk("result.apk")); runCurrent()
            f.repository.results.single().complete(Result.success(signatureFixture)); runCurrent()
            f.vm.onIntent(KeyStoreSelected("A.jks")); f.vm.onIntent(PasswordChanged("A password")); runCurrent()
            f.vm.onIntent(KeyStoreSelected("B.jks")); runCurrent()
            assertEquals("A password", f.vm.uiState.value.passwordDialog!!.password, "Existing open dialog keeps its original remember scope")
            assertNull(f.vm.uiState.value.passwordDialog!!.aliases)
            f.vm.onIntent(PasswordChanged("B password")); runCurrent()
            f.keys.results[1].complete(listOf("first", "second")); runCurrent()
            f.vm.onIntent(AliasSelected("second"))
            f.keys.results[0].complete(listOf("stale A")); runCurrent()
            assertEquals("second", f.vm.uiState.value.passwordDialog!!.selectedAlias)
            f.vm.onIntent(PasswordChanged("pending")); runCurrent()
            f.vm.onIntent(DismissPasswordDialog)
            f.keys.results[2].complete(listOf("late")); runCurrent()
            assertNull(f.vm.uiState.value.passwordDialog)
            assertEquals(signatureFixture.path, f.vm.uiState.value.result!!.path)
            f.vm.onIntent(KeyStoreSelected("C.jks")); runCurrent()
            assertEquals("", f.vm.uiState.value.passwordDialog!!.password)
        }
    }

    @Test fun certificateRequiresResolvedAliasAndCapturesCredentialsBeforeClosing() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            f.vm.onIntent(KeyStoreSelected("test.jks")); f.vm.onIntent(VerifyCertificate); runCurrent()
            f.vm.onIntent(PasswordChanged("secret")); f.vm.onIntent(VerifyCertificate); runCurrent()
            assertEquals(2, events.size)
            assertTrue(events.all { it.snackbar.message == UiMessage.Resource(Res.string.wrong_key_store_password) })
            assertNotEquals(events[0].effectId, events[1].effectId)
            f.keys.results[0].complete(emptyList()); runCurrent()
            f.vm.onIntent(VerifyCertificate); runCurrent(); assertEquals(3, events.size)
            f.vm.onIntent(PasswordChanged("final password")); runCurrent()
            f.keys.results[1].complete(listOf("one", "two")); runCurrent()
            f.vm.onIntent(AliasSelected("two")); f.vm.onIntent(VerifyCertificate)
            assertNull(f.vm.uiState.value.passwordDialog)
            assertTrue(f.vm.uiState.value.busy)
            f.vm.onIntent(VerifyCertificate); runCurrent()
            assertEquals(listOf("test.jks", "final password", "two"), f.repository.credentials.single())
            f.repository.results.single().complete(Result.success(signatureFixture.copy(isApk = false, isSuccess = true))); runCurrent()
            assertFalse(f.vm.uiState.value.result!!.isApk)
            assertFalse(f.vm.busy.value)
        }
    }

    @Test fun failureFallbacksBlankExceptionTextAndSameMessageRetryKeepOriginalRules() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            for (message in listOf(null, "", "broken", "broken")) {
                f.vm.onIntent(VerifyApk("failed.apk")); runCurrent()
                f.repository.results.last().complete(Result.failure(Exception(message))); runCurrent()
                assertEquals(VerificationPhase.Idle, f.vm.uiState.value.phase)
                assertNull(f.vm.uiState.value.result)
                assertFalse(f.vm.busy.value)
            }
            assertEquals(UiMessage.Resource(Res.string.apk_signature_verification_failed), events[0].snackbar.message)
            assertEquals(UiMessage.Text(""), events[1].snackbar.message)
            assertEquals(events[2].snackbar.message, events[3].snackbar.message)
            assertEquals(4, events.map { it.effectId }.distinct().size)
        }
    }

    @Test fun fourCopyFormatsAreExactAndEachNotificationWaitsForClipboardAcknowledgement() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            val expected = listOf("AB:CD:09", "ab:cd:09", "ABCD09", "abcd09")
            for ((index, mode) in CopyMode.entries.withIndex()) {
                f.vm.onIntent(CopyModeChanged(mode)); f.vm.onIntent(CopyFingerprint("Ab:cD:09")); runCurrent()
                assertEquals(expected[index], f.clipboard.values.last())
                assertEquals(index, events.size)
                assertEquals(mode.name, f.preferences.state.value.copyMode.name)
                f.clipboard.done.last().complete(Unit); runCurrent()
                assertEquals(index + 1, events.size)
                assertEquals(UiMessage.Resource(Res.string.copied_to_clipboard), events.last().snackbar.message)
            }
            f.vm.onIntent(CopyText(" Raw:Value\n")); runCurrent()
            assertEquals(" Raw:Value\n", f.clipboard.values.last())
            f.clipboard.done.last().completeExceptionally(IllegalStateException("fixture clipboard unavailable")); runCurrent()
            assertEquals(4, events.size, "Failed clipboard write never announces success")
            assertEquals(4, events.map { it.effectId }.distinct().size)
        }
    }

    @Test fun clearedWindowRejectsLateVerificationAndClipboardCompletionAndReleasesState() = runTest {
        SignatureVmFixture(StandardTestDispatcher(testScheduler)).use { f ->
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
            runCurrent()
            f.vm.onIntent(VerifyApk("slow.apk")); f.vm.onIntent(CopyText("fixture")); runCurrent()
            f.store.clear(); runCurrent()
            f.repository.results.single().complete(Result.success(signatureFixture))
            f.clipboard.done.single().complete(Unit); runCurrent()
            assertFalse(f.vm.uiState.value.busy)
            assertNull(f.vm.uiState.value.result)
            assertTrue(events.isEmpty())
        }
    }
}

internal class MemorySignaturePreferences : PreferencesRepository {
    private val value = MutableStateFlow(PreferencesSnapshot(ready = true))
    override val state = value.asStateFlow()
    override fun change(change: PreferenceChange): PreferencesSnapshot = value.value.changed(change).also { value.value = it }
    override suspend fun awaitReady() = value.value
}
internal class ControlledSignatureRepository : SignatureRepository {
    val results = mutableListOf<CompletableDeferred<Result<SignatureVerification>>>()
    val credentials = mutableListOf<List<String>>()
    override suspend fun verifyApk(path: String): Result<SignatureVerification> = withContext(NonCancellable) {
        CompletableDeferred<Result<SignatureVerification>>().also { results += it }.await()
    }
    override suspend fun verifyCertificate(path: String, password: String, alias: String): Result<SignatureVerification> {
        credentials += listOf(path, password, alias)
        return verifyApk(path)
    }
}
internal class SignatureKeys : KeyStoreRepository by EmptyKeys {
    val results = mutableListOf<CompletableDeferred<List<String>?>>()
    override suspend fun loadAliases(path: String, password: String): List<String>? = withContext(NonCancellable) {
        CompletableDeferred<List<String>?>().also { results += it }.await()
    }
}
@OptIn(ExperimentalCoroutinesApi::class)
internal class SignatureVmFixture(dispatcher: TestDispatcher) : AutoCloseable {
    val effects = AppEffectSink()
    val preferences = MemorySignaturePreferences()
    val repository = ControlledSignatureRepository()
    val keys = SignatureKeys()
    val clipboard = ControlledClipboard()
    val store = ViewModelStore()
    val vm: SignatureInformationViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = SignatureInformationViewModel(VerifySignatureUseCase(repository), keys, preferences, clipboard, effects)
        store.put("signature", vm)
    }
    override fun close() {
        store.clear()
        repository.results.forEach { it.complete(Result.failure(Exception("fixture cleanup"))) }
        keys.results.forEach { it.complete(null) }
        clipboard.done.forEach { it.complete(Unit) }
        effects.close(); Dispatchers.resetMain()
    }
}
