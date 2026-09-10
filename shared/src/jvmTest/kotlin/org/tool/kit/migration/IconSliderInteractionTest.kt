package org.tool.kit.migration

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.Dispatchers
import org.junit.Test
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.repository.DefaultPreferencesRepository
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.iconfactory.*
import org.tool.kit.theme.AppTheme
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, ExperimentalSettingsApi::class, ExperimentalMaterial3Api::class)
class IconSliderInteractionTest {
    @Test fun bothPngThumbsAndJpegSliderPersistOnlyAfterPointerRelease() = runDesktopComposeUiTest(width = 800, height = 800) {
        val physical = MapSettings()
        val dispatchers = AppDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Main.immediate)
        val preferences = DefaultPreferencesRepository(PreferencesDataSource(physical.toFlowSettings(Dispatchers.Unconfined), dispatchers.io), dispatchers)
        val effects = AppEffectSink()
        val vm = IconFactoryViewModel(unusedGenerateIcons(), preferences, AllPathsExist, effects)
        val store = ViewModelStore().also { it.put("icon-sliders", vm) }
        try {
            setContent {
                val state by vm.uiState.collectAsState()
                AppTheme(false) { Column { Compression(state.settings, state.draft, vm::onIntent) } }
            }
            waitUntil { preferences.state.value.ready }
            runOnIdle { vm.onIntent(IconFactoryIntent.SheetOpened) }
            onNodeWithText("有损压缩").performClick()
            waitUntil { !preferences.state.value.iconFactoryData.lossless }
            val sliders = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress), useUnmergedTree = true)
            sliders.assertCountEquals(4)
            for (index in 0..2) {
                val initial = preferences.state.value.iconFactoryData
                val initialRevision = preferences.state.value.revision
                val slider = sliders[index]
                if (index < 2) slider.performMouseInput {
                    moveTo(center); press(); moveBy(Offset(-40f, 0f))
                } else slider.performMouseInput {
                    moveTo(centerRight.copy(x = centerRight.x - 20f)); press(); moveTo(center)
                }
                runOnIdle {
                    assertEquals(initial, preferences.state.value.iconFactoryData)
                    assertEquals(initialRevision, preferences.state.value.revision)
                }
                slider.performMouseInput { release() }
                waitUntil { preferences.state.value.iconFactoryData != initial }
                waitUntil { preferences.state.value.persistedRevision == preferences.state.value.revision }
            }
            val saved = preferences.state.value.iconFactoryData
            assertEquals(saved.minimum, physical.getInt("icon_factory_data.minimum", -1))
            assertEquals(saved.target, physical.getInt("icon_factory_data.target", -1))
            assertEquals(saved.quality, physical.getFloat("icon_factory_data.quality", -1f))
        } finally { runOnIdle { store.clear(); preferences.close(); effects.close() } }
    }
}
