package org.tool.kit.migration

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.jetbrains.compose.resources.getString
import org.tool.kit.utils.formatFileSize
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.preferences.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.*
import org.tool.kit.feature.app.*
import org.tool.kit.feature.apk.*
import org.tool.kit.feature.apk.ApkToolIntent.*
import org.tool.kit.feature.signature.SigningCredentialsUi
import org.tool.kit.shared.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApkToolViewModelTest {
    @Test fun submissionCapturesEveryFieldAndPreferenceAndRejectsDuplicateGeneration() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); fill(f.vm); runCurrent()
            f.preferences.change(PreferenceChange.SignerSuffix(" suffix"))
            f.preferences.change(PreferenceChange.DuplicateRemoval(false))
            f.preferences.change(PreferenceChange.AlignFileSize(false))
            f.preferences.change(PreferenceChange.HuaweiAlignment(false))
            f.vm.onIntent(Submit); f.vm.onIntent(Submit); runCurrent()
            val request = f.pipeline.requests.single()
            assertEquals(listOf("/output", "/icon.png", "org.fixture", "32", "23", "12", "2.3", "中文 Empty"),
                listOf(request.outputDirectory, request.iconPath, request.packageName, request.targetSdkVersion,
                    request.minSdkVersion, request.versionCode, request.versionName, request.appName))
            val signing = assertNotNull(request.signing)
            assertEquals(SigningCredentials("/key.jks", "store", "first", "key"), signing.credentials)
            assertEquals(ApkSigningPolicy.V3, signing.policy)
            assertEquals(" suffix", signing.suffix); assertEquals("", signing.prefix)
            assertFalse(signing.overwrite); assertFalse(signing.userAlign); assertFalse(signing.huaweiAlignment)
            assertEquals("huawei", signing.huaweiPresetPath); assertEquals("apk-name.apk.idsig", signing.v4FileName)
            f.vm.onIntent(AppNameChanged("next")); f.vm.onIntent(StorePasswordChanged("changed"))
            f.preferences.change(PreferenceChange.OutputPath("/later")); runCurrent()
            f.pipeline.pending.single().complete(Unit); runCurrent()
            f.vm.uiState.first { !it.busy }
            assertEquals("/output/中文 Empty.apk", f.pipeline.signRequests.single().inputPath)
            assertEquals("store", f.pipeline.signRequests.single().credentials.storePassword)
            assertEquals("next", f.vm.uiState.value.form.appName)
            assertEquals("/later", f.vm.uiState.value.form.outputPath)
            assertEquals(SnackbarAction.OpenDirectory("/output/中文 Empty.apk"), events.single().snackbar.action)
            assertEquals("apk-tool", events.single().originFeature)
            assertEquals(UiMessage.Text(getString(Res.string.build_end, 2048L.formatFileSize())), events.single().snackbar.message)
            assertEquals(1, f.pipeline.cleanups)
        } finally { f.close() }
    }

    @Test fun disabledSigningAndFailedOptionalSigningKeepOriginalCompletionMessage() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(OutputPathChanged("/output")); runCurrent()
            f.vm.onIntent(Submit); runCurrent(); assertNull(f.pipeline.requests.single().signing)
            f.pipeline.pending.last().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            val first = events.single().snackbar
            f.vm.onIntent(EnableSignChanged(true)); runCurrent()
            // Old Generate does not block on completed key/password errors or blank credentials.
            f.vm.onIntent(KeyPathChanged("/missing.jks")); f.vm.onIntent(StorePasswordChanged("wrong")); runCurrent()
            assertTrue(f.vm.uiState.value.validation.keyError)
            f.pipeline.signResult = SignApkOutcome.Failure("wrong password")
            f.vm.onIntent(Submit); runCurrent()
            assertEquals(2, f.pipeline.requests.size)
            f.pipeline.pending.last().complete(Unit); runCurrent(); f.vm.uiState.first { !it.busy }
            assertEquals(first, events.last().snackbar)
            assertNotEquals(events.first().effectId, events.last().effectId)
        } finally { f.close() }
    }

    @Test fun buildFailureUsesOriginalTextOrFallbackAndAllowsRetry() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(OutputPathChanged("/output")); runCurrent()
            for (message in listOf(null, "", "decode error")) {
                f.vm.onIntent(Submit); runCurrent()
                f.pipeline.pending.last().completeExceptionally(Exception(message)); runCurrent()
                assertEquals(message?.let(UiMessage::Text) ?: UiMessage.Resource(Res.string.build_failure), events.last().snackbar.message)
                assertFalse(f.vm.uiState.value.busy)
            }
            assertEquals(3, f.pipeline.cleanups)
        } finally { f.close() }
    }

    @Test fun pendingInvalidAndEmptyChecksPreserveOriginalPriority() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent()
            f.vm.onIntent(OutputPathChanged("/output")); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            f.vm.onIntent(OutputPathChanged("")); f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_empty), events.last().snackbar.message)
            f.vm.onIntent(OutputPathChanged("/missing")); f.vm.onIntent(PackageNameChanged("")); runCurrent()
            f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_error), events.last().snackbar.message)
            f.vm.onIntent(OutputPathChanged("/output")); runCurrent()
            f.vm.onIntent(Submit); runCurrent()
            assertEquals(UiMessage.Resource(Res.string.check_empty), events.last().snackbar.message)
            assertTrue(f.pipeline.requests.isEmpty())
        } finally { f.close() }
    }

    @Test fun dropsUseOnlyTheFirstAcceptedPlatformItemAndTheSameCredentialReset() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        try {
            runCurrent(); fill(f.vm); runCurrent()
            f.vm.onIntent(FilesDropped(listOf("/readme.txt", "/later.png", "/later.jks"))); runCurrent()
            assertEquals("/icon.png", f.vm.uiState.value.form.icon)
            assertEquals("/key.jks", f.vm.uiState.value.form.credentials.path)
            f.vm.onIntent(FilesDropped(listOf("/first.png", "/later.jks"))); runCurrent()
            assertEquals("/first.png", f.vm.uiState.value.form.icon)
            assertEquals("/key.jks", f.vm.uiState.value.form.credentials.path)
            f.vm.onIntent(FilesDropped(listOf("/first.jks", "/later.png"))); runCurrent()
            assertEquals(SigningCredentialsUi(path = "/first.jks"), f.vm.uiState.value.form.credentials)
            f.vm.onIntent(FilesDropped(listOf("/upper.JKS"))); runCurrent()
            assertEquals("/first.jks", f.vm.uiState.value.form.credentials.path)
        } finally { f.close() }
    }

    @Test fun aClosedWindowDropsNonCooperativeCompletionAndCleansItsOperation() = runTest {
        val f = ToolFixture(StandardTestDispatcher(testScheduler))
        f.pipeline.nonCooperative = true
        val events = mutableListOf<AppEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.effects.effects.toList(events) }
        try {
            runCurrent(); f.vm.onIntent(OutputPathChanged("/output")); runCurrent(); f.vm.onIntent(Submit); runCurrent()
            f.store.clear(); runCurrent(); assertFalse(f.vm.uiState.value.busy)
            f.pipeline.pending.single().complete(Unit); runCurrent()
            assertTrue(events.isEmpty()); assertFalse(f.vm.uiState.value.busy)
            assertEquals(1, f.pipeline.cleanups)
        } finally { f.close() }
    }

    @Test fun lateAliasesAndPasswordsCannotRestoreCredentialsAfterToggleClearsTheKey() = runTest {
        val keys = ToolDeferredKeys()
        val f = ToolFixture(StandardTestDispatcher(testScheduler), keys = keys)
        try {
            runCurrent(); fill(f.vm); runCurrent()
            f.vm.onIntent(StorePasswordChanged("new")); runCurrent()
            keys.aliases.last().complete(listOf("current")); runCurrent()
            keys.aliases.first().complete(listOf("old")); runCurrent()
            assertEquals(listOf("current"), f.vm.uiState.value.form.credentials.aliases)
            f.vm.onIntent(EnableSignChanged(false)); runCurrent()
            keys.passwords.forEach { it.complete(false) }; runCurrent()
            assertEquals(SigningCredentialsUi(), f.vm.uiState.value.form.credentials)
            assertNull(f.vm.uiState.value.validation.credentials.aliasPasswordValid)
        } finally { keys.aliases.forEach { it.complete(null) }; keys.passwords.forEach { it.complete(false) }; f.close() }
    }

    @Test fun latePathChecksCannotReplaceNewPathAndRefreshRechecksDisk() = runTest {
        val queries = mutableListOf<Pair<String, CompletableDeferred<PathMetadata>>>()
        val storage = object : StorageRepository by AllPathsExist {
            override suspend fun inspectPath(path: String) = withContext(NonCancellable) {
                CompletableDeferred<PathMetadata>().also { queries += path to it }.await()
            }
        }
        val f = ToolFixture(StandardTestDispatcher(testScheduler), storage)
        try {
            runCurrent(); f.vm.onIntent(IconPathChanged("A")); runCurrent()
            f.vm.onIntent(IconPathChanged("B")); runCurrent(); f.vm.onIntent(IconPathChanged("A")); runCurrent()
            queries.last().second.complete(PathMetadata(true, false)); runCurrent()
            queries.dropLast(1).forEach { it.second.complete(PathMetadata(false, false)) }; runCurrent()
            assertFalse(f.vm.uiState.value.validation.iconError)
            f.vm.onIntent(Refresh); runCurrent()
            queries.forEach { it.second.complete(PathMetadata(false, false)) }; runCurrent()
            assertTrue(f.vm.uiState.value.validation.iconError)
        } finally { queries.forEach { it.second.complete(PathMetadata(false, false)) }; f.close() }
    }
}

private class ToolPreferences : PreferencesRepository {
    override val state = MutableStateFlow(PreferencesSnapshot(ready = true))
    override fun change(change: PreferenceChange): PreferencesSnapshot {
        val old = state.value; val next = old.changed(change)
        state.value = next.copy(outputPathVersion = old.outputPathVersion + if (next.userData.defaultOutputPath != old.userData.defaultOutputPath) 1 else 0)
        return state.value
    }
    override suspend fun awaitReady() = state.value
}
private class ToolPipeline : ApkToolRepository, ApkBuildSession, ApkBuildWorkspaces {
    val requests = mutableListOf<BuildApkRequest>()
    val pending = mutableListOf<CompletableDeferred<Unit>>()
    val signRequests = mutableListOf<SignApkRequest>()
    var nonCooperative = false
    var cleanups = 0
    var signResult: SignApkOutcome = SignApkOutcome.Success("signed", true)
    override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T =
        try { block(ApkBuildWorkspace("owned", "${request.outputDirectory}/${request.outputFileName}")) } finally { cleanups++ }
    override suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession {
        requests += request
        val gate = CompletableDeferred<Unit>().also { pending += it }
        if (nonCooperative) withContext(NonCancellable) { gate.await() } else gate.await()
        return this
    }
    override suspend fun updateManifest() {}
    override suspend fun updateAppName() {}
    override suspend fun copyIcon() {}
    override suspend fun saveMetadata(versionCode: Int) {}
    override suspend fun build() {}
    override suspend fun outputSize() = 2048L
}
@OptIn(ExperimentalCoroutinesApi::class)
private class ToolFixture(dispatcher: TestDispatcher,
    storage: StorageRepository = object : StorageRepository by AllPathsExist {
        override suspend fun inspectPath(path: String) = PathMetadata(!path.contains("missing"), !path.contains("missing"))
    }, keys: KeyStoreRepository = object : KeyStoreRepository by EmptyKeys {
        override suspend fun loadAliases(path: String, password: String) = listOf("first", "second")
        override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String) = true
    }) : AutoCloseable {
    val preferences = ToolPreferences()
    val effects = AppEffectSink()
    val pipeline = ToolPipeline()
    val store = ViewModelStore()
    val vm: ApkToolViewModel
    init {
        Dispatchers.setMain(dispatcher)
        vm = ApkToolViewModel(BuildApkUseCase(pipeline, SignApkUseCase { request ->
            pipeline.signRequests += request; pipeline.signResult
        }, pipeline), preferences, storage, keys, effects, "huawei")
        store.put("apk-tool", vm)
    }
    override fun close() { store.clear(); effects.close(); Dispatchers.resetMain() }
}
private fun fill(vm: ApkToolViewModel) {
    listOf(OutputPathChanged("/output"), IconPathChanged("/icon.png"), PackageNameChanged("org.fixture"),
        TargetSdkChanged("32"), MinSdkChanged("23"), VersionCodeChanged("12"), VersionNameChanged("2.3"),
        AppNameChanged("中文 Empty"), EnableSignChanged(true), KeyPathChanged("/key.jks"),
        StorePasswordChanged("store"), AliasPasswordChanged("key")).forEach(vm::onIntent)
}
private class ToolDeferredKeys : KeyStoreRepository by EmptyKeys {
    val aliases = mutableListOf<CompletableDeferred<List<String>?>>()
    val passwords = mutableListOf<CompletableDeferred<Boolean>>()
    override suspend fun loadAliases(path: String, password: String): List<String>? = withContext(NonCancellable) {
        CompletableDeferred<List<String>?>().also { aliases += it }.await()
    }
    override suspend fun validateAliasPassword(path: String, storePassword: String, alias: String?, password: String): Boolean =
        withContext(NonCancellable) { CompletableDeferred<Boolean>().also { passwords += it }.await() }
}
