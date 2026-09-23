package org.tool.kit.tests.feature.iconfactory

import kotlin.test.*
import org.junit.Test
import org.tool.kit.domain.preferences.PreferencesSnapshot
import org.tool.kit.feature.iconfactory.*
import org.tool.kit.feature.iconfactory.IconFactoryIntent.*
import org.tool.kit.model.*

class IconFactoryFormReducerTest {
    private val settings = PreferencesSnapshot().iconFactoryData

    @Test fun formDefaultsAndFieldEditsPreserveOriginalStrings() {
        val initial = IconFactoryForm()
        assertNull(initial.inputPath)
        assertEquals(listOf("", "res", "mipmap", "ic_launcher"), listOf(initial.outputPath, initial.fileDir, initial.iconDir, initial.iconName))
        var form = initial
        for ((intent, expected) in listOf(
            OutputPathChanged(" output ") to initial.copy(outputPath = " output "),
            FileDirChanged(" ../res 中文 ") to initial.copy(fileDir = " ../res 中文 "),
            IconDirChanged("") to initial.copy(iconDir = ""),
            IconNameChanged(" icon name ") to initial.copy(iconName = " icon name "),
            InputChanged(" source.PNG ") to initial.copy(inputPath = " source.PNG "),
        )) assertEquals(expected, IconFactoryFormReducer.field(initial, intent))
        form = IconFactoryFormReducer.field(form, InputChanged("input.png"))
        assertNull(IconFactoryFormReducer.field(form, InputChanged(null)).inputPath)
        assertEquals(form, IconFactoryFormReducer.field(form, Submit))
    }

    @Test fun draftKeepsSliderPrecisionAndPngTargetFloorUntilRelease() {
        val original = IconSettingsDraft.from(settings)
        val range = IconSettingsReducer.draft(original, PngRangeChanged(12.4f, 22.1f))
        assertEquals(12.4f, range.minimum); assertEquals(30f, range.target)
        val changed = IconSettingsReducer.draft(range, JpegQualityChanged(43.6f))
        assertEquals(43.6f, changed.jpegQuality)
        val committed = IconSettingsReducer.committed(settings, changed, PngRangeCommitted)
        assertEquals(12, committed.minimum); assertEquals(30, committed.target)
        assertEquals(settings.quality, committed.quality)
        assertEquals(44f, IconSettingsReducer.committed(committed, changed, JpegQualityCommitted).quality)
        assertEquals(original, IconSettingsDraft.from(settings))
    }

    @Test fun speedAndAlgorithmsUseOriginalMappingsWithoutChangingOtherSettings() {
        val draft = IconSettingsDraft.from(settings)
        for ((value, percentage, speed, preset) in listOf(
            listOf(1f, .1f, 10f, 0f), listOf(5.555f, .56f, 5f, 3f), listOf(10f, 1f, 1f, 6f))) {
            val actual = IconSettingsReducer.committed(settings, draft.copy(compressionSpeed = value), CompressionSpeedCommitted)
            assertEquals(settings.copy(percentage = percentage, speed = speed.toInt(), preset = preset.toInt()), actual)
        }
        assertEquals(settings.copy(lossless = false), IconSettingsReducer.committed(settings, draft, LosslessChanged(false)))
        for (algorithm in PngAlgorithm.entries) assertEquals(settings.copy(pngTypIdx = algorithm), IconSettingsReducer.committed(settings, draft, PngAlgorithmChanged(algorithm)))
        for (algorithm in JpegAlgorithm.entries) assertEquals(settings.copy(jpegTypIdx = algorithm), IconSettingsReducer.committed(settings, draft, JpegAlgorithmChanged(algorithm)))
    }
}
