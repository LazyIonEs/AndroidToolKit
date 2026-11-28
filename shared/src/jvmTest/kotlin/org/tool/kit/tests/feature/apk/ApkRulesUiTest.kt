package org.tool.kit.tests.feature.apk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.PreferencesStorage
import org.tool.kit.domain.apk.ApkAlignment
import org.tool.kit.domain.apk.ApkArchiveInformation
import org.tool.kit.domain.apk.ApkComponent
import org.tool.kit.domain.apk.ApkComponentType
import org.tool.kit.domain.apk.ApkDetectedEntry
import org.tool.kit.domain.apk.ApkExportedDeclaration
import org.tool.kit.domain.apk.ApkLibRule
import org.tool.kit.domain.apk.ApkLibRuleDetail
import org.tool.kit.domain.apk.ApkLibRuleMatch
import org.tool.kit.domain.apk.ApkLibRuleType
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.ApkLibrarySource
import org.tool.kit.domain.apk.ApkManifestExtras
import org.tool.kit.domain.apk.ApkNativeLibrary
import org.tool.kit.domain.apk.ApkRecognizedSdk
import org.tool.kit.domain.apk.ApkRuleAnalysis
import org.tool.kit.domain.apk.ApkRuleUpdateResult
import org.tool.kit.domain.apk.ApkRuleUpdateStatus
import org.tool.kit.domain.apk.LibRuleMatcher
import org.tool.kit.domain.preferences.PreferenceChange
import org.tool.kit.domain.preferences.PreferencesSnapshot
import org.tool.kit.domain.repository.LibRulesRepository
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.feature.apk.ApkIconDecoder
import org.tool.kit.feature.apk.ApkInformationIntent
import org.tool.kit.feature.apk.ApkInformationPhase
import org.tool.kit.feature.apk.ApkInformationScreen
import org.tool.kit.feature.apk.ApkInformationUiState
import org.tool.kit.feature.apk.ApkInformationViewModel
import org.tool.kit.feature.apk.LibRuleIconDecoder
import org.tool.kit.feature.apk.RuleDetailState
import org.tool.kit.feature.apk.ruleDetailKey
import org.tool.kit.feature.app.AppEffect
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.app.UiMessage
import org.tool.kit.feature.setting.SettingsIntent
import org.tool.kit.feature.setting.SettingsViewModel
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.lib_rules_update_success
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.ControlledClipboard
import org.tool.kit.tests.support.FixtureApkRepository
import org.tool.kit.tests.support.RecordingDesktopActions
import org.tool.kit.theme.AppTheme
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalSettingsApi::class, ExperimentalTestApi::class)
class ApkRulesUiTest {
    private val flutterUuid = "AEF9680F-4A43-4EDC-A5B8-8119D23BCD21"

    @Test
    fun apkViewModelPopulatesRulesLoadsDetailAndRefreshesOnUpdate() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        val effects = AppEffectSink()
        try {
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { effects.effects.toList(events) }
            val apkRepo = object : FixtureApkRepository() {
                override suspend fun components(path: String) = listOf(
                    ApkComponent("io.flutter.embedding.android.FlutterActivity", ApkComponentType.Activity, ApkExportedDeclaration.Enabled, "p")
                )

                override suspend fun archive(path: String) = ApkArchiveInformation(
                    files = emptyList(),
                    nativeLibraries = listOf(
                        ApkNativeLibrary("lib/arm64-v8a/libflutter.so", "arm64-v8a", 100, 100, false, ApkAlignment.Aligned, ApkAlignment.Aligned)
                    ),
                    overheadBytes = 0,
                )

                override suspend fun dexPackages(path: String) = setOf("io.flutter.embedding.engine")
                override suspend fun manifestExtras(path: String) = ApkManifestExtras()
            }
            val rulesRepo = FakeLibRulesRepository(flutterUuid)
            val vm = ApkInformationViewModel(
                read = ReadApkInformationUseCase(apkRepo, rulesRepo),
                decoder = ApkIconDecoder { error("No app icon") },
                clipboard = ControlledClipboard(),
                effects = effects,
                libRules = rulesRepo,
                ruleIcons = LibRuleIconDecoder { ImageBitmap(16, 16) },
                actions = RecordingDesktopActions(),
            )
            store.put("vm", vm)

            vm.onIntent(ApkInformationIntent.ReadApk("app.apk"))
            runCurrent()
            val result = assertNotNull(vm.uiState.value.result)
            val analysis = assertNotNull(result.ruleAnalysis)
            assertEquals(1, analysis.sdks.size)
            assertEquals("Flutter", analysis.sdks.single().label)
            assertEquals(50L, vm.uiState.value.rulesMetadata?.dataVersion)
            assertTrue(vm.uiState.value.ruleIcons.containsKey("ic_lib_flutter"))

            vm.onIntent(ApkInformationIntent.LoadRuleDetail(flutterUuid, 71))
            runCurrent()
            val loaded = vm.uiState.value.ruleDetails[ruleDetailKey(flutterUuid, 71)]
            assertIs<RuleDetailState.Loaded>(loaded)
            assertEquals("Google", loaded.detail.devTeam)

            vm.onIntent(ApkInformationIntent.UpdateLibRules)
            runCurrent()
            assertEquals(51L, vm.uiState.value.rulesMetadata?.dataVersion)
            assertEquals(51L, vm.uiState.value.result?.ruleAnalysis?.rulesVersion)
            assertTrue(events.any { it.snackbar.message == UiMessage.Resource(Res.string.lib_rules_update_success) })
        } finally {
            store.clear()
            effects.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun settingsViewModelLoadsMetadataOnRefreshAndUpdatesRules() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val effects = AppEffectSink()
        val prefs = DefaultPreferencesRepository(object : PreferencesStorage {
            override suspend fun read() = PreferencesSnapshot(ready = true)
            override suspend fun write(change: PreferenceChange, snapshot: PreferencesSnapshot) {}
        }, AppDispatchers(dispatcher, dispatcher, dispatcher))
        try {
            val events = mutableListOf<AppEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { effects.effects.toList(events) }
            val rulesRepo = FakeLibRulesRepository(flutterUuid)
            val vm = SettingsViewModel(prefs, AllPathsExist, effects, RecordingDesktopActions(), rulesRepo)
            runCurrent()
            assertEquals(null, vm.uiState.value.rulesMetadata)

            vm.onIntent(SettingsIntent.Refresh)
            runCurrent()
            assertEquals(50L, vm.uiState.value.rulesMetadata?.dataVersion)

            vm.onIntent(SettingsIntent.UpdateLibRules)
            runCurrent()
            assertEquals(51L, vm.uiState.value.rulesMetadata?.dataVersion)
            assertTrue(events.any { it.snackbar.message == UiMessage.Resource(Res.string.lib_rules_update_success) })
        } finally {
            prefs.close()
            effects.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sdkTabAndEnrichedComponentsAndLibrariesRenderAndNavigate() = runDesktopComposeUiTest(width = 900, height = 650) {
        val flutterMatch = ApkLibRuleMatch(
            ruleId = 71,
            uuid = flutterUuid,
            label = "Flutter",
            type = ApkLibRuleType.Native,
            iconId = "ic_lib_flutter",
            isSimpleColorIcon = false,
            hasDetail = true,
            matchedByRegex = false,
            ruleName = "libflutter.so",
        )
        val sample = ApkInformationTestData.sample.copy(
            components = listOf(
                ApkComponent(
                    "io.flutter.embedding.android.FlutterActivity",
                    ApkComponentType.Activity,
                    ApkExportedDeclaration.Enabled,
                    "com.example.app",
                    rule = flutterMatch.copy(type = ApkLibRuleType.Activity, ruleName = "io.flutter.embedding.android.FlutterActivity"),
                )
            ),
            archive = ApkInformationTestData.sample.archive!!.copy(
                nativeLibraries = listOf(
                    ApkNativeLibrary("lib/arm64-v8a/libflutter.so", "arm64-v8a", 5_000_000, 2_000_000, true, ApkAlignment.Aligned, ApkAlignment.NotApplicable, rule = flutterMatch)
                )
            ),
            ruleAnalysis = ApkRuleAnalysis(
                sdks = listOf(
                    ApkRecognizedSdk(
                        uuid = flutterUuid,
                        label = "Flutter",
                        iconId = "ic_lib_flutter",
                        isSimpleColorIcon = false,
                        hasDetail = true,
                        primaryRuleId = 71,
                        sources = setOf(ApkLibrarySource.Native, ApkLibrarySource.Activity, ApkLibrarySource.Dex),
                        entries = listOf(
                            ApkDetectedEntry("lib/arm64-v8a/libflutter.so", ApkLibrarySource.Native, flutterMatch),
                            ApkDetectedEntry("io.flutter.embedding.android.FlutterActivity", ApkLibrarySource.Activity, flutterMatch),
                            ApkDetectedEntry("io.flutter.embedding.engine", ApkLibrarySource.Dex, flutterMatch),
                        ),
                    )
                ),
                dexMatches = listOf(ApkDetectedEntry("io.flutter.embedding.engine", ApkLibrarySource.Dex, flutterMatch)),
                staticMatches = emptyList(),
                actionMatches = emptyList(),
                rulesVersion = 50L,
            ),
        )
        val detail = ApkLibRuleDetail(
            uuid = flutterUuid,
            ruleId = 71,
            label = "Flutter",
            devTeam = "Google",
            description = "Flutter cross-platform UI toolkit.",
            sourceLink = "https://flutter.dev",
            contributors = listOf("Absinthe"),
            locale = "en",
        )
        var state by mutableStateOf(
            ApkInformationUiState(
                phase = ApkInformationPhase.Result,
                inputFile = "/app.apk",
                result = sample,
                ruleIcons = mapOf("ic_lib_flutter" to ImageBitmap(16, 16)),
                rulesMetadata = ApkLibRulesMetadata(5, 50L, 3053, "sha", "rev", isBundled = true),
            )
        )
        val openedUrls = mutableListOf<String>()
        setContent {
            AppTheme(false) {
                ApkInformationScreen(
                    state = state,
                    useDarkTheme = false,
                    onIntent = { intent ->
                        when (intent) {
                            is ApkInformationIntent.LoadRuleDetail -> {
                                val key = ruleDetailKey(intent.uuid, intent.ruleId)
                                state = state.copy(ruleDetails = state.ruleDetails + (key to RuleDetailState.Loaded(detail)))
                            }
                            is ApkInformationIntent.OpenUrl -> openedUrls += intent.url
                            else -> Unit
                        }
                    },
                    onPickFile = {},
                    dragging = false,
                    dropTarget = ApkInformationTestData.target,
                )
            }
        }

        onNodeWithTag("apk-tab-5").performClick().assertIsSelected()
        onNodeWithTag("apk-sdk-$flutterUuid").assertIsDisplayed().performClick()
        onNodeWithTag("apk-sdk-detail").assertIsDisplayed()
        onNodeWithText("Google").assertIsDisplayed()
        onNodeWithText("Flutter cross-platform UI toolkit.").assertIsDisplayed()
        onNodeWithTag("apk-rule-open-link").performClick()
        assertEquals(listOf("https://flutter.dev"), openedUrls)

        onNodeWithTag("apk-detail-back").performClick()
        onNodeWithTag("apk-detail-search-toggle").performClick()
        onNodeWithTag("apk-detail-search").performTextInput("Flutter")
        onNodeWithTag("apk-sdk-$flutterUuid").assertIsDisplayed()

        onNodeWithTag("apk-tab-2").performClick()
        onNodeWithTag("apk-component-Activity:io.flutter.embedding.android.FlutterActivity").performClick()
        onNodeWithTag("apk-rule-detail-section").assertIsDisplayed()

        onNodeWithTag("apk-tab-3").performClick()
        onNodeWithTag("apk-library-lib/arm64-v8a/libflutter.so").performClick()
        onNodeWithTag("apk-rule-detail-section").assertIsDisplayed()
    }

    private class FakeLibRulesRepository(private val flutterUuid: String) : LibRulesRepository {
        private var version = 50L
        private var bundled = true

        override suspend fun metadata() = ApkLibRulesMetadata(
            schemaVersion = 5,
            dataVersion = version,
            ruleCount = 2,
            contentSha256 = "sha$version",
            sourceRevision = "rev$version",
            isBundled = bundled,
        )

        override suspend fun matcher() = LibRuleMatcher(
            listOf(
                ApkLibRule(
                    id = 71,
                    uuid = flutterUuid,
                    name = "libflutter.so",
                    label = "Flutter",
                    type = ApkLibRuleType.Native,
                    iconIndex = 20,
                    iconId = "ic_lib_flutter",
                    isSimpleColorIcon = false,
                    isRegexRule = false,
                    regexName = null,
                    priority = 71,
                    hasDetail = true,
                ),
                ApkLibRule(
                    id = 72,
                    uuid = flutterUuid,
                    name = "io.flutter.embedding.android.FlutterActivity",
                    label = "Flutter",
                    type = ApkLibRuleType.Activity,
                    iconIndex = 20,
                    iconId = "ic_lib_flutter",
                    isSimpleColorIcon = false,
                    isRegexRule = false,
                    regexName = null,
                    priority = 72,
                    hasDetail = true,
                ),
            )
        )

        override suspend fun iconBytes(iconId: String): ByteArray? = null

        override suspend fun detail(uuid: String, ruleId: Int, preferChinese: Boolean): Result<ApkLibRuleDetail?> =
            Result.success(
                ApkLibRuleDetail(
                    uuid = uuid,
                    ruleId = ruleId,
                    label = "Flutter",
                    devTeam = "Google",
                    description = "Flutter engine",
                    sourceLink = "https://flutter.dev",
                    contributors = listOf("Absinthe"),
                    locale = if (preferChinese) "zh-Hans" else "en",
                )
            )

        override suspend fun checkAndUpdate(): ApkRuleUpdateResult {
            version = 51L
            bundled = false
            return ApkRuleUpdateResult(ApkRuleUpdateStatus.Updated, metadata())
        }
    }
}
