package org.tool.kit.migration

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.domain.keystore.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase
import org.tool.kit.feature.app.*
import org.tool.kit.feature.keystore.*
import org.tool.kit.feature.keystore.KeyStoreGenerationIntent.*
import org.tool.kit.model.DestStoreSize
import org.tool.kit.model.DestStoreType
import org.tool.kit.shared.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class KeyStoreGenerationViewModelTest {
    @Test fun allFieldsKeepRawInputAndOriginalValidationAndRequiredBoundaries() = runTest {
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(events) }
        try {
            runCurrent()
            val vm = fixture.vm
            assertEquals("sign.jks", vm.uiState.value.form.keyStoreName)
            assertEquals("25", vm.uiState.value.form.validityPeriod)
            fillKeyStore(vm); runCurrent()
            assertEquals(" Author ", vm.uiState.value.form.authorName)
            assertEquals("027", vm.uiState.value.form.validityPeriod)
            assertEquals("alias fixture", vm.uiState.value.form.keyStoreAlisa)
            assertEquals("Unit", vm.uiState.value.form.organizationalUnit)
            assertEquals("Org", vm.uiState.value.form.organizational)
            assertEquals("City", vm.uiState.value.form.city)
            assertEquals("Province", vm.uiState.value.form.province)
            assertEquals("CN", vm.uiState.value.form.countryCode)
            listOf("UPPER.JKS", "file.p12", "file.jks ").forEach {
                vm.onIntent(FileNameChanged(it)); assertTrue(vm.uiState.value.validation.fileNameError)
            }
            vm.onIntent(FileNameChanged(" leading.keystore")); assertFalse(vm.uiState.value.validation.fileNameError)
            vm.onIntent(StoreConfirmationChanged("wrong")); vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            vm.onIntent(StoreConfirmationChanged("")); assertFalse(vm.uiState.value.validation.storeConfirmationError)
            vm.onIntent(AliasConfirmationChanged("wrong")); assertTrue(vm.uiState.value.validation.aliasConfirmationError)
            val blankFields = listOf(OutputPathChanged(" "), FileNameChanged(" "), StorePasswordChanged(" "),
                StoreConfirmationChanged(" "), AliasChanged(" "), AliasPasswordChanged(" "), AliasConfirmationChanged(" "),
                ValidityChanged(" "), AuthorNameChanged(" "), OrganizationalUnitChanged(" "), OrganizationChanged(" "),
                CityChanged(" "), ProvinceChanged(" "), CountryCodeChanged(" "))
            for (field in blankFields) {
                fillKeyStore(vm); vm.onIntent(field); runCurrent()
                vm.onIntent(Submit); runCurrent()
                val expected = if (field is StorePasswordChanged || field is AliasPasswordChanged)
                    Res.string.check_error else Res.string.check_empty
                assertEquals(UiMessage.Resource(expected), events.last().snackbar.message, field::class.simpleName)
            }
            assertTrue(fixture.keys.requests.isEmpty())
            assertFalse(vm.uiState.value.busy)
        } finally { fixture.close() }
    }

    @Test fun submitCapturesFormAndSettingsOnceAndDuplicateSubmitDoesNotStartAnotherJob() = runTest {
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(events) }
        try {
            runCurrent(); fillKeyStore(fixture.vm); runCurrent()
            fixture.preferences.change(PreferenceChange.StoreType(DestStoreType.PKCS12))
            fixture.preferences.change(PreferenceChange.StoreSize(DestStoreSize.ONE_THOUSAND_TWENTY_FOUR))
            fixture.vm.onIntent(Submit); fixture.vm.onIntent(Submit)
            assertTrue(fixture.vm.uiState.value.busy)
            runCurrent()
            val request = fixture.keys.requests.single()
            assertEquals(KeyStoreFormat.PKCS12, request.format)
            assertEquals(1024, request.keySize)
            assertEquals("/fixture output", request.outputDirectory)
            assertEquals("store password", request.storePassword)
            assertEquals("alias password", request.aliasPassword)
            assertEquals("027", request.validityYears)
            fixture.vm.onIntent(FileNameChanged("next.keystore"))
            fixture.vm.onIntent(StorePasswordChanged("next password"))
            fixture.preferences.change(PreferenceChange.OutputPath("/next output"))
            fixture.preferences.change(PreferenceChange.StoreType(DestStoreType.JKS)); runCurrent()
            fixture.keys.results.single().complete(GenerateKeyStoreOutcome.Success("/fixture output/sign.jks")); runCurrent()
            fixture.vm.uiState.first { !it.busy }
            assertFalse(fixture.vm.uiState.value.busy)
            assertEquals("next.keystore", fixture.vm.uiState.value.form.keyStoreName)
            assertEquals("next password", fixture.vm.uiState.value.form.keyStorePassword)
            assertEquals("/next output", fixture.vm.uiState.value.form.keyStorePath)
            assertEquals("store password", request.storePassword)
            val effect = events.single()
            assertEquals("keystore", effect.originFeature)
            assertEquals(1L, effect.operationId)
            assertEquals(UiMessage.Resource(Res.string.create_signature_successfully), effect.snackbar.message)
            assertEquals(SnackbarAction.OpenDirectory("/fixture output/sign.jks"), effect.snackbar.action)
            assertTrue(effect.snackbar.withDismissAction)
            assertNotNull(effect.snackbar.actionLabel)
        } finally { fixture.close() }
    }

    @Test fun failedAndExceptionalJobsClearBusyAndAllowRetryWithDistinctEffects() = runTest {
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(events) }
        try {
            runCurrent(); fillKeyStore(fixture.vm); runCurrent()
            fixture.vm.onIntent(Submit); runCurrent()
            fixture.keys.results.last().complete(GenerateKeyStoreOutcome.Failure()); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.signature_creation_failed), events.last().snackbar.message)
            assertFalse(fixture.vm.uiState.value.busy)
            fixture.vm.onIntent(Submit); runCurrent()
            fixture.keys.results.last().completeExceptionally(IllegalStateException("fixture failure")); runCurrent()
            assertEquals(UiMessage.Text("fixture failure"), events.last().snackbar.message)
            assertFalse(fixture.vm.uiState.value.busy)
            fixture.vm.onIntent(Submit); runCurrent()
            fixture.keys.results.last().complete(GenerateKeyStoreOutcome.Failure("fixture failure")); runCurrent()
            assertEquals(events[1].snackbar.message, events[2].snackbar.message)
            assertNotEquals(events[1].effectId, events[2].effectId)
            assertEquals(listOf(1L, 2L, 3L), events.map { it.operationId })
        } finally { fixture.close() }
    }

    @Test fun oldPathValidationCannotOverwriteNewInputAndRefreshRechecksTheSamePath() = runTest {
        val pending = mutableListOf<Pair<String, CompletableDeferred<PathMetadata>>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String) = withContext(NonCancellable) {
                CompletableDeferred<PathMetadata>().also { pending += path to it }.await()
            }
        }
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler), storage)
        try {
            runCurrent()
            fixture.vm.onIntent(OutputPathChanged("A")); runCurrent()
            fixture.vm.onIntent(OutputPathChanged("B")); runCurrent()
            assertTrue(fixture.vm.uiState.value.validation.outputPathPending)
            pending.last().second.complete(PathMetadata(false, false)); runCurrent()
            pending.dropLast(1).forEach { it.second.complete(PathMetadata(false, true)) }; runCurrent()
            assertTrue(fixture.vm.uiState.value.validation.outputPathError)
            fixture.vm.onIntent(Refresh); runCurrent()
            assertEquals("B", pending.last().first)
            pending.last().second.complete(PathMetadata(false, true)); runCurrent()
            assertFalse(fixture.vm.uiState.value.validation.outputPathError)
            assertFalse(fixture.vm.uiState.value.validation.outputPathPending)
        } finally {
            pending.forEach { it.second.complete(PathMetadata(false, false)) }
            fixture.close()
        }
    }

    @Test fun submitRechecksCapturedDirectoryWhilePendingAndRejectsChangedDiskState() = runTest {
        var exists = true
        var block = false
        val gates = mutableListOf<CompletableDeferred<PathMetadata>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String): PathMetadata {
                if (block) return CompletableDeferred<PathMetadata>().also { gates += it }.await()
                return PathMetadata(false, exists)
            }
        }
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler), storage)
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(events) }
        try {
            runCurrent(); fillKeyStore(fixture.vm); runCurrent()
            exists = false
            fixture.vm.onIntent(Submit); runCurrent()
            assertTrue(fixture.keys.requests.isEmpty())
            assertEquals(UiMessage.Resource(Res.string.check_error), events.single().snackbar.message)
            block = true
            fixture.vm.onIntent(OutputPathChanged("/pending request")); runCurrent()
            fixture.vm.onIntent(Submit); runCurrent()
            assertTrue(fixture.vm.uiState.value.busy)
            assertTrue(fixture.keys.requests.isEmpty())
            gates.last().complete(PathMetadata(false, true)); runCurrent()
            assertEquals("/pending request", fixture.keys.requests.single().outputDirectory)
            fixture.keys.results.single().complete(GenerateKeyStoreOutcome.Success("/pending request/sign.jks")); runCurrent()
            fixture.vm.uiState.first { !it.busy }
            assertFalse(fixture.vm.uiState.value.busy)
        } finally { gates.forEach { it.complete(PathMetadata(false, true)) }; fixture.close() }
    }

    @Test fun clearingWindowCancelsOwnedJobAndIgnoresNonCooperativeLateResult() = runTest {
        val fixture = KeyStoreFixture(StandardTestDispatcher(testScheduler))
        fixture.keys.nonCooperative = true
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { fixture.effects.effects.toList(events) }
        try {
            runCurrent(); fillKeyStore(fixture.vm); runCurrent()
            fixture.vm.onIntent(Submit); runCurrent()
            fixture.store.clear(); runCurrent()
            assertTrue(fixture.vm.uiState.value.busy, "Uninterruptible work has not yet released its output")
            fixture.keys.results.single().complete(GenerateKeyStoreOutcome.Success("late")); runCurrent()
            assertFalse(fixture.vm.uiState.value.busy)
            assertTrue(events.isEmpty(), "A closed window must not publish a late success or cancellation error")
        } finally { fixture.close() }
    }
}

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
internal class KeyStoreFixture(dispatcher: TestDispatcher, storage: StorageRepository = AllPathsExist) : AutoCloseable {
    val effects = AppEffectSink()
    val preferences = DefaultPreferencesRepository(PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), dispatcher),
        AppDispatchers(dispatcher, dispatcher, dispatcher))
    val keys = ControlledKeyGeneration()
    val store = ViewModelStore()
    val vm: KeyStoreGenerationViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = KeyStoreGenerationViewModel(GenerateKeyStoreUseCase(keys), preferences, storage, effects)
        store.put("keys", vm)
    }
    override fun close() { store.clear(); preferences.close(); effects.close(); Dispatchers.resetMain() }
}

internal class ControlledKeyGeneration : KeyStoreRepository by EmptyKeys {
    val requests = mutableListOf<GenerateKeyStoreRequest>()
    val results = mutableListOf<CompletableDeferred<GenerateKeyStoreOutcome>>()
    var nonCooperative = false
    override suspend fun generate(request: GenerateKeyStoreRequest): GenerateKeyStoreOutcome {
        requests += request
        val result = CompletableDeferred<GenerateKeyStoreOutcome>().also { results += it }
        return if (nonCooperative) withContext(NonCancellable) { result.await() } else result.await()
    }
}

internal fun fillKeyStore(vm: KeyStoreGenerationViewModel) {
    listOf(OutputPathChanged("/fixture output"), FileNameChanged("sign.jks"), StorePasswordChanged("store password"),
        StoreConfirmationChanged("store password"), AliasChanged("alias fixture"), AliasPasswordChanged("alias password"),
        AliasConfirmationChanged("alias password"), ValidityChanged("027"), AuthorNameChanged(" Author "),
        OrganizationalUnitChanged("Unit"), OrganizationChanged("Org"), CityChanged("City"), ProvinceChanged("Province"),
        CountryCodeChanged("CN")).forEach(vm::onIntent)
}
