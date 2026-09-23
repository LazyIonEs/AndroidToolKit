package org.tool.kit.tests.data

import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.*
import com.russhwolf.settings.coroutines.toFlowSettings
import com.russhwolf.settings.serialization.encodeValue
import java.util.concurrent.Executors
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.keystore.*
import org.tool.kit.feature.setting.*
import org.tool.kit.model.*
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.EmptyKeys
import org.tool.kit.tests.support.RecordingDesktopActions
import org.tool.kit.tests.support.junkViewModel
import org.tool.kit.tests.support.unusedBuildApk
import org.tool.kit.tests.support.unusedGenerateIcons

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class, ExperimentalSerializationApi::class)
class PreferencesRepositoryTest {
    @Test fun oldPhysicalFixtureLoadsBeforeFirstVisibleSnapshotAndQueuedFieldsKeepUntouchedValues() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val physical = MapSettings()
        val old = UserData("/old physical", false, "-old", false, DestStoreType.PKCS12, DestStoreSize.ONE_THOUSAND_TWENTY_FOUR)
        physical.encodeValue(UserData.serializer(), "user_data", old)
        physical.putString("theme_config", "DARK")
        val actual = PreferencesDataSource(physical.toFlowSettings(Dispatchers.Unconfined), dispatcher)
        val gate = CompletableDeferred<Unit>()
        val repository = DefaultPreferencesRepository(object : PreferencesStorage by actual {
            override suspend fun read(): PreferencesSnapshot { gate.await(); return actual.read() }
        }, AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            runCurrent()
            assertFalse(repository.state.value.ready)
            repository.change(PreferenceChange.SignerSuffix("-typed during load"))
            repository.change(PreferenceChange.OutputPath(" /latest path "))
            gate.complete(Unit)
            runCurrent()
            val state = repository.awaitReady()
            assertEquals(old.copy(defaultOutputPath = " /latest path ", defaultSignerSuffix = "-typed during load"), state.userData)
            assertEquals(ThemePreference.DARK, state.themeConfig)
            assertEquals(state.userData, actual.read().userData)
            assertEquals(2L, state.persistedRevision)
            assertEquals(1, physical.getInt("user_data.destStoreType", -1))
            assertEquals(0, physical.getInt("user_data.destStoreSize", -1))
        } finally { repository.close(); Dispatchers.resetMain() }
    }

    @Test fun heldDiskWriteCannotRollBackLaterInputAndToolFormsReceiveOnePathVersion() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val actual = PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), dispatcher)
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        val repository = DefaultPreferencesRepository(object : PreferencesStorage by actual {
            override suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot) {
                if (++writes == 1) gate.await()
                actual.write(change, snapshot)
            }
        }, AppDispatchers(dispatcher, dispatcher, dispatcher))
        val sink = AppEffectSink()
        val vm = SettingsViewModel(repository, AllPathsExist, sink, RecordingDesktopActions())
        val junk = junkViewModel(repository, AllPathsExist, sink)
        val signing = org.tool.kit.feature.signature.ApkSigningViewModel(org.tool.kit.domain.usecase.SignApkUseCase { error("Unexpected signing") },
            repository, AllPathsExist, EmptyKeys, sink, org.tool.kit.feature.signature.SigningPresets(emptyList(), "All", "Huawei"))
        val icons = org.tool.kit.feature.iconfactory.IconFactoryViewModel(unusedGenerateIcons(), repository, AllPathsExist, sink)
        val apkTool = org.tool.kit.feature.apk.ApkToolViewModel(unusedBuildApk(), repository, AllPathsExist, EmptyKeys, sink, "Huawei")
        val keys = KeyStoreGenerationViewModel(GenerateKeyStoreUseCase(EmptyKeys), repository, AllPathsExist, sink)
        val store = ViewModelStore().also { it.put("settings", vm); it.put("junk", junk); it.put("keys", keys); it.put("signing", signing); it.put("apk-tool", apkTool); it.put("icons", icons) }
        fun paths() = listOf(signing.uiState.value.form.outputPath, keys.uiState.value.form.keyStorePath,
            junk.uiState.value.outputPath, icons.uiState.value.form.outputPath, apkTool.uiState.value.form.outputPath)
        fun chooseCustomPaths() {
            signing.onIntent(org.tool.kit.feature.signature.ApkSigningIntent.OutputPathChanged("sign custom"))
            keys.onIntent(KeyStoreGenerationIntent.OutputPathChanged("key custom"))
            junk.onIntent(org.tool.kit.feature.junk.JunkCodeIntent.OutputPathChanged("junk custom"))
            icons.onIntent(org.tool.kit.feature.iconfactory.IconFactoryIntent.OutputPathChanged("icon custom"))
            apkTool.onIntent(org.tool.kit.feature.apk.ApkToolIntent.OutputPathChanged("apk tool custom"))
        }
        try {
            runCurrent()
            chooseCustomPaths()
            vm.onIntent(SettingsIntent.OutputPath("first"))
            runCurrent()
            assertEquals(List(5) { "first" }, paths())
            repeat(30) { index ->
                vm.onIntent(SettingsIntent.SignerSuffix("suffix $index"))
                vm.onIntent(SettingsIntent.OutputPath(" output $index "))
                vm.onIntent(SettingsIntent.AlignFileSize(index % 2 == 0))
            }
            assertEquals(" output 29 ", vm.uiState.value.preferences.userData.defaultOutputPath)
            runCurrent()
            assertEquals(List(5) { " output 29 " }, paths())
            chooseCustomPaths()
            val custom = paths()
            vm.onIntent(SettingsIntent.AlwaysShowLabel(true))
            vm.onIntent(SettingsIntent.OutputPath(" output 29 ")) // identical default does not rebroadcast
            runCurrent()
            assertEquals(custom, paths())
            gate.complete(Unit)
            runCurrent()
            assertEquals(custom, paths(), "Disk acknowledgements cannot overwrite self-selected output folders")
            assertEquals(repository.state.value.revision, repository.state.value.persistedRevision)
            assertEquals(repository.state.value.userData, actual.read().userData)
            assertEquals("suffix 29", vm.uiState.value.preferences.userData.defaultSignerSuffix)
            val later = org.tool.kit.feature.apk.ApkToolViewModel(unusedBuildApk(), repository, AllPathsExist, EmptyKeys, sink, "Huawei")
            val laterJunk = junkViewModel(repository, AllPathsExist, sink)
            store.put("laterJunk", laterJunk)
            val laterIcons = org.tool.kit.feature.iconfactory.IconFactoryViewModel(unusedGenerateIcons(), repository, AllPathsExist, sink)
            val laterKeys = KeyStoreGenerationViewModel(GenerateKeyStoreUseCase(EmptyKeys), repository, AllPathsExist, sink)
            store.put("later", later)
            store.put("laterKeys", laterKeys)
            store.put("laterIcons", laterIcons)
            runCurrent()
            assertEquals(" output 29 ", laterJunk.uiState.value.outputPath)
            assertEquals(" output 29 ", later.uiState.value.form.outputPath)
            assertEquals(" output 29 ", laterKeys.uiState.value.form.keyStorePath)
            assertEquals(" output 29 ", laterIcons.uiState.value.form.outputPath)
        } finally { store.clear(); repository.close(); sink.close(); Dispatchers.resetMain() }
    }

    @Test fun realPhysicalAccessRunsOnInjectedIoAndNoKeysChangeOnRead() = runBlocking {
        val io = Executors.newSingleThreadExecutor { Thread(it, "preferences-test-io") }.asCoroutineDispatcher()
        val threads = mutableListOf<String>()
        val physical = MapSettings("theme_config" to "DARK")
        val observed = object : ObservableSettings by physical {
            override fun getStringOrNull(key: String): String? { threads += Thread.currentThread().name; return physical.getStringOrNull(key) }
            override fun putString(key: String, value: String) { threads += Thread.currentThread().name; physical.putString(key, value) }
        }
        try {
            val source = PreferencesDataSource(observed.toFlowSettings(Dispatchers.Unconfined), io)
            assertTrue(threads.isEmpty(), "Constructor must not touch physical settings")
            val state = source.read()
            assertEquals(setOf("theme_config"), physical.keys)
            source.write(PreferenceChange.SignerSuffix("new"), state.changed(PreferenceChange.SignerSuffix("new")))
            assertTrue(threads.isNotEmpty())
            assertTrue(threads.all { it.substringBefore(" @coroutine") == "preferences-test-io" }, threads.toString())
            assertEquals("new", source.read().userData.defaultSignerSuffix)
        } finally { io.close() }
    }

    @Test fun failedFieldIsRetriedBeforeLaterWritesCanAcknowledgePersistence() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val actual = PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), dispatcher)
        var failOnce = true
        val repository = DefaultPreferencesRepository(object : PreferencesStorage by actual {
            override suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot) {
                if (failOnce) { failOnce = false; throw IllegalStateException("fixture failure") }
                actual.write(change, snapshot)
            }
        }, AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            runCurrent()
            repository.change(PreferenceChange.SignerSuffix("survives failure")); runCurrent()
            assertEquals(0L, repository.state.value.persistedRevision)
            assertNotNull(repository.state.value.writeFailure)
            repository.change(PreferenceChange.AlwaysShowLabel(true)); runCurrent()
            assertEquals(2L, repository.state.value.persistedRevision)
            assertNull(repository.state.value.writeFailure)
            assertEquals("survives failure", actual.read().userData.defaultSignerSuffix)
            assertTrue(actual.read().isAlwaysShowLabel)
        } finally { repository.close() }
    }

    @Test fun settingsValidationRejectsOldPathAndRefreshRechecksTheSameDirectory() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val repository = DefaultPreferencesRepository(PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), dispatcher),
            AppDispatchers(dispatcher, dispatcher, dispatcher))
        val pending = mutableListOf<CompletableDeferred<org.tool.kit.domain.repository.PathMetadata>>()
        val storage = object : org.tool.kit.domain.repository.StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String) = withContext(NonCancellable) {
                CompletableDeferred<org.tool.kit.domain.repository.PathMetadata>().also { pending += it }.await()
            }
        }
        val sink = AppEffectSink()
        val vm = SettingsViewModel(repository, storage, sink, RecordingDesktopActions())
        val store = ViewModelStore().also { it.put("settings", vm) }
        try {
            runCurrent()
            vm.onIntent(SettingsIntent.OutputPath("A")); runCurrent()
            vm.onIntent(SettingsIntent.OutputPath("B")); runCurrent()
            pending.last().complete(org.tool.kit.domain.repository.PathMetadata(false, false)); runCurrent()
            pending.dropLast(1).forEach { it.complete(org.tool.kit.domain.repository.PathMetadata(false, true)) }; runCurrent()
            assertTrue(vm.uiState.value.outputPathError)
            vm.onIntent(SettingsIntent.Refresh); runCurrent()
            pending.last().complete(org.tool.kit.domain.repository.PathMetadata(false, true)); runCurrent()
            assertFalse(vm.uiState.value.outputPathError)
        } finally { pending.forEach { it.complete(org.tool.kit.domain.repository.PathMetadata(false, false)) }; store.clear(); repository.close(); sink.close(); Dispatchers.resetMain() }
    }
}
