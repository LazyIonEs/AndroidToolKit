package org.tool.kit.tests.feature.iconfactory

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.iconfactory.Compression
import org.tool.kit.feature.iconfactory.IconFactoryViewModel
import org.tool.kit.tests.support.AllPathsExist
import org.tool.kit.tests.support.release
import org.tool.kit.tests.support.unusedGenerateIcons
import org.tool.kit.theme.AppTheme

@OptIn(ExperimentalTestApi::class, ExperimentalSettingsApi::class, ExperimentalMaterial3Api::class)
class IconSettingsCommitTest {
    @Test fun compressionSliderPublishesPreferencesOnlyAfterPointerRelease() = runDesktopComposeUiTest(width = 800, height = 572) {
        val physical = MapSettings()
        val dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main.immediate)
        val preferences = DefaultPreferencesRepository(PreferencesDataSource(physical.toFlowSettings(Dispatchers.Unconfined), dispatchers.io), dispatchers)
        val effects = AppEffectSink()
        val vm = IconFactoryViewModel(unusedGenerateIcons(), preferences, AllPathsExist, effects)
        val store = ViewModelStore().also { it.put("icon", vm) }
        try {
            setContent { val state by vm.uiState.collectAsState(); AppTheme(false) { Column { Compression(state.settings, state.draft, vm::onIntent) } } }
            waitUntil { preferences.state.value.ready }
            val initial = preferences.state.value.iconFactoryData
            val slider = onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            slider.performMouseInput { moveTo(centerRight.copy(x = centerRight.x - 20f)); press(); moveTo(center) }
            runOnIdle {
                assertEquals(initial, preferences.state.value.iconFactoryData)
                assertFalse(physical.hasKey("icon_factory_data.percentage"))
            }
            slider.performMouseInput { release() }
            waitUntil { preferences.state.value.iconFactoryData != initial }
            waitUntil { preferences.state.value.persistedRevision == preferences.state.value.revision }
            assertEquals(preferences.state.value.iconFactoryData.percentage, physical.getFloat("icon_factory_data.percentage", -1f))
        } finally { runOnIdle { store.clear(); preferences.close(); effects.close() } }
    }
}
