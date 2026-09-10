package org.tool.kit.tests.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import java.lang.reflect.Proxy
import kotlin.reflect.KClass
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.Test
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.viewmodel.resolveViewModel
import org.tool.kit.app.AppBootstrap
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.source.PreferencesStorage
import org.tool.kit.di.desktopModules
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.feature.apk.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.cleaner.CleanerViewModel
import org.tool.kit.feature.iconfactory.IconFactoryViewModel
import org.tool.kit.feature.junk.JunkCodeViewModel
import org.tool.kit.feature.keystore.KeyStoreGenerationViewModel
import org.tool.kit.feature.setting.SettingsViewModel
import org.tool.kit.feature.signature.*
import org.tool.kit.feature.update.UpdateViewModel
import org.tool.kit.tests.support.AllPathsExist

/** Resolve the production graph with every external capability replaced before first resolution. */
@OptIn(ExperimentalSettingsApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, KoinInternalApi::class)
class ArchitectureGraphTest {
    @Test fun allElevenOwnersResolveSharePreferencesAndCloseWithoutExternalWork() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        var reads = 0
        var writes = 0
        var closed = 0
        val unexpected = mutableListOf<String>()
        val store = ViewModelStore()
        val container = koinApplication {
            modules(desktopModules({ MapSettings().toFlowSettings(dispatcher) },
                AppDispatchers(dispatcher, dispatcher, dispatcher)) + module {
                single<PreferencesStorage> { object : PreferencesStorage {
                    override suspend fun read() = PreferencesSnapshot(ready = true).also { reads++ }
                    override suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot) { writes++ }
                } }
                single<StorageRepository> { AllPathsExist }
                single<KeyStoreRepository> { forbiddenCalls(unexpected) }
                single<SignatureRepository> { forbiddenCalls(unexpected) }
                single<ApkInformationRepository> { forbiddenCalls(unexpected) }
                single<ApkSigningRepository> { forbiddenCalls(unexpected) }
                single<ApkToolRepository> { forbiddenCalls(unexpected) }
                single<ApkBuildWorkspaces> { forbiddenCalls(unexpected) }
                single<ImageProcessor> { forbiddenCalls(unexpected) }
                single<IconOutputs> { forbiddenCalls(unexpected) }
                single<JunkCodeRepository> { forbiddenCalls(unexpected) }
                single<BuildCachesRepository> { forbiddenCalls(unexpected) }
                single<UpdateRepository> { forbiddenCalls(unexpected) }
                single<ApkIconDecoder> { forbiddenCalls(unexpected) }
                single<ClipboardWriter> { forbiddenCalls(unexpected) }
                single<DesktopActionHandler> { forbiddenCalls(unexpected) }
                single<JunkSizeEstimator> { JunkSizeEstimator { _, _ -> 42L } }
                single<JunkTokenGenerator> { forbiddenCalls(unexpected) }
                single { SigningPresets(listOf(SigningPreset("All", "All")), "All", "fixture-huawei.apk") }
            })
        }
        try {
            val preferences = container.koin.get<PreferencesRepository>()
            runCurrent()
            container.koin.get<AppBootstrap>().prepare()
            val types: List<KClass<out ViewModel>> = listOf(AppViewModel::class, SettingsViewModel::class,
                UpdateViewModel::class, KeyStoreGenerationViewModel::class, SignatureInformationViewModel::class,
                ApkInformationViewModel::class, ApkSigningViewModel::class, ApkToolViewModel::class,
                IconFactoryViewModel::class, JunkCodeViewModel::class, CleanerViewModel::class)
            fun <T : ViewModel> resolve(type: KClass<T>) = resolveViewModel(type, store, type.qualifiedName,
                CreationExtras.Empty, scope = container.koin.scopeRegistry.rootScope)
            val owners = types.map { type -> resolve(type).also { it.addCloseable { closed++ } } }
            runCurrent()
            types.zip(owners).forEach { (type, owner) -> assertSame(owner, resolve(type)) }
            assertEquals(11, owners.toSet().size)
            assertSame(preferences, container.koin.get<PreferencesRepository>())
            assertEquals(1, reads)
            assertEquals(StorageCapacity(1_000, 400), (owners.last() as CleanerViewModel).uiState.value.capacity)
            preferences.change(PreferenceChange.OutputPath("fake-shared-output"))
            runCurrent()
            assertEquals("fake-shared-output", (owners[3] as KeyStoreGenerationViewModel).uiState.value.form.keyStorePath)
            assertEquals("fake-shared-output", (owners[6] as ApkSigningViewModel).uiState.value.form.outputPath)
            assertEquals("fake-shared-output", (owners[7] as ApkToolViewModel).uiState.value.form.outputPath)
            assertEquals("fake-shared-output", (owners[8] as IconFactoryViewModel).uiState.value.form.outputPath)
            assertEquals("fake-shared-output", (owners[9] as JunkCodeViewModel).uiState.value.outputPath)
            assertEquals(1, writes)
            assertTrue(unexpected.isEmpty(), "No FFI, process, network, clipboard, or generator work at construction: $unexpected")
            store.clear()
            runCurrent()
            assertEquals(11, closed)
            store.clear()
            assertEquals(11, closed)
        } finally {
            store.clear()
            container.close()
            Dispatchers.resetMain()
        }
    }
}

private inline fun <reified T> forbiddenCalls(calls: MutableList<String>): T =
    Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { proxy, method, args ->
        when (method.name) {
            "toString" -> "ForbiddenCalls<${T::class.simpleName}>"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> error("Unexpected external capability: ${T::class.simpleName}.${method.name}".also(calls::add))
        }
    } as T
