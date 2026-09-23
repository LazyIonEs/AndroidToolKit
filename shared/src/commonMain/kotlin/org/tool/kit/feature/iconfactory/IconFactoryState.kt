package org.tool.kit.feature.iconfactory

import org.tool.kit.model.IconFactoryData
import org.tool.kit.model.JpegAlgorithm
import org.tool.kit.model.PngAlgorithm
import kotlin.math.roundToInt

data class IconFactoryForm(
    val inputPath: String? = null,
    val outputPath: String = "",
    val fileDir: String = "res",
    val iconDir: String = "mipmap",
    val iconName: String = "ic_launcher",
)

/** 滑块编辑中的临时值；拖动过程中不写入持久化设置。 */
data class IconSettingsDraft(val compressionSpeed: Float, val minimum: Float, val target: Float, val jpegQuality: Float) {
    companion object {
        fun from(settings: IconFactoryData) = IconSettingsDraft(settings.percentage * 10f,
            settings.minimum.toFloat(), settings.target.toFloat(), settings.quality)
    }
}

data class IconResultUi(val path: String, val previewAvailable: Boolean)

data class IconFactoryUiState(
    val form: IconFactoryForm,
    val settings: IconFactoryData,
    val draft: IconSettingsDraft = IconSettingsDraft.from(settings),
    val result: List<IconResultUi>? = null,
    val busy: Boolean = false,
    val sheetOpen: Boolean = false,
)

sealed interface IconFactoryIntent {
    data class InputChanged(val path: String?) : IconFactoryIntent
    data class FileSelected(val path: String) : IconFactoryIntent
    data class FilesDropped(val paths: List<String>) : IconFactoryIntent
    data class OutputPathChanged(val value: String) : IconFactoryIntent
    data class FileDirChanged(val value: String) : IconFactoryIntent
    data class IconDirChanged(val value: String) : IconFactoryIntent
    data class IconNameChanged(val value: String) : IconFactoryIntent
    data class LosslessChanged(val value: Boolean) : IconFactoryIntent
    data class PngAlgorithmChanged(val value: PngAlgorithm) : IconFactoryIntent
    data class JpegAlgorithmChanged(val value: JpegAlgorithm) : IconFactoryIntent
    data class CompressionSpeedChanged(val value: Float) : IconFactoryIntent
    data object CompressionSpeedCommitted : IconFactoryIntent
    data class PngRangeChanged(val start: Float, val end: Float) : IconFactoryIntent
    data object PngRangeCommitted : IconFactoryIntent
    data class JpegQualityChanged(val value: Float) : IconFactoryIntent
    data object JpegQualityCommitted : IconFactoryIntent
    data object CompressionEditorEntered : IconFactoryIntent
    data object LossyEditorEntered : IconFactoryIntent
    data object SheetOpened : IconFactoryIntent
    data object SheetClosed : IconFactoryIntent
    data object PageEntered : IconFactoryIntent
    data object PageLeft : IconFactoryIntent
    data object Submit : IconFactoryIntent
}

object IconFactoryFormReducer {
    /** 只修改指定表单字段，保留其余输入。 */
    fun field(form: IconFactoryForm, intent: IconFactoryIntent): IconFactoryForm = when (intent) {
        is IconFactoryIntent.InputChanged -> form.copy(inputPath = intent.path)
        is IconFactoryIntent.OutputPathChanged -> form.copy(outputPath = intent.value)
        is IconFactoryIntent.FileDirChanged -> form.copy(fileDir = intent.value)
        is IconFactoryIntent.IconDirChanged -> form.copy(iconDir = intent.value)
        is IconFactoryIntent.IconNameChanged -> form.copy(iconName = intent.value)
        else -> form
    }
}

object IconSettingsReducer {
    /** 更新面板草稿，并维持 PNG 目标质量的最低可选值。 */
    fun draft(draft: IconSettingsDraft, intent: IconFactoryIntent): IconSettingsDraft = when (intent) {
        is IconFactoryIntent.CompressionSpeedChanged -> draft.copy(compressionSpeed = intent.value)
        is IconFactoryIntent.PngRangeChanged -> draft.copy(minimum = intent.start, target = if (intent.end < 30) 30f else intent.end)
        is IconFactoryIntent.JpegQualityChanged -> draft.copy(jpegQuality = intent.value)
        else -> draft
    }

    /** 把明确的提交事件归并到最新设置，只写对应参数以免覆盖其他设置项。 */
    fun committed(settings: IconFactoryData, draft: IconSettingsDraft, intent: IconFactoryIntent): IconFactoryData = when (intent) {
        is IconFactoryIntent.LosslessChanged -> settings.copy(lossless = intent.value)
        is IconFactoryIntent.PngAlgorithmChanged -> settings.copy(pngTypIdx = intent.value)
        is IconFactoryIntent.JpegAlgorithmChanged -> settings.copy(jpegTypIdx = intent.value)
        IconFactoryIntent.CompressionSpeedCommitted -> {
            // 滑块的 0–10 表示值换算为持久化比例，再映射为量化速度和 PNG 优化等级。
            val percentage = "%.2f".format(draft.compressionSpeed.toBigDecimal().divide(10f.toBigDecimal()).toFloat()).toFloat()
            settings.copy(percentage = percentage, speed = 11 - (10 * percentage).roundToInt(), preset = (7 * percentage).roundToInt() - 1)
        }
        IconFactoryIntent.PngRangeCommitted -> settings.copy(minimum = draft.minimum.roundToInt(), target = draft.target.roundToInt())
        IconFactoryIntent.JpegQualityCommitted -> settings.copy(quality = draft.jpegQuality.roundToInt().toFloat())
        else -> settings
    }
}
