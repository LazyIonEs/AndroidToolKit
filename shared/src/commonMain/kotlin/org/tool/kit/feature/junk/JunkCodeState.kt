package org.tool.kit.feature.junk

import org.tool.kit.domain.junk.*
import org.tool.kit.model.JunkMode

data class SingleJunkForm(
    val aarName: String = "junk_com_dev_junk_plugin_TT2.2.0.aar",
    val packageName: String = "com.dev.junk",
    val suffix: String = "plugin",
    val packageCount: String = "50",
    val activityCountPerPackage: String = "50",
    val resPrefix: String = "junk_",
)

data class MultiJunkForm(
    val outputDir: String = "junk",
    val aarCount: String = "50",
    val leastPackageCount: String = "5",
    val maximumPackageCount: String = "20",
    val leastActivityCountPerPackage: String = "5",
    val maximumActivityCountPerPackage: String = "20",
)

data class JunkCodeUiState(
    val outputPath: String,
    val mode: JunkMode,
    val single: SingleJunkForm = SingleJunkForm(),
    val multi: MultiJunkForm = MultiJunkForm(),
    val estimatedSize: String = "",
    val outputValidation: JunkOutputCheck = JunkOutputCheck(),
    val busy: Boolean = false,
)

sealed interface JunkCodeIntent {
    data class OutputPathChanged(val value: String) : JunkCodeIntent
    data class ModeChanged(val value: JunkMode) : JunkCodeIntent
    data class PackageNameChanged(val value: String) : JunkCodeIntent
    data class SuffixChanged(val value: String) : JunkCodeIntent
    data class PackageCountChanged(val value: String) : JunkCodeIntent
    data class ActivityCountChanged(val value: String) : JunkCodeIntent
    data class ResPrefixChanged(val value: String) : JunkCodeIntent
    data class OutputDirChanged(val value: String) : JunkCodeIntent
    data class AarCountChanged(val value: String) : JunkCodeIntent
    data class LeastPackagesChanged(val value: String) : JunkCodeIntent
    data class MaximumPackagesChanged(val value: String) : JunkCodeIntent
    data class LeastActivitiesChanged(val value: String) : JunkCodeIntent
    data class MaximumActivitiesChanged(val value: String) : JunkCodeIntent
    data object RandomSuffix : JunkCodeIntent
    data object RandomPrefix : JunkCodeIntent
    data object Refresh : JunkCodeIntent
    data object Submit : JunkCodeIntent
}

object JunkFormReducer {
    fun single(form: SingleJunkForm, intent: JunkCodeIntent): SingleJunkForm = when (intent) {
        is JunkCodeIntent.PackageNameChanged -> form.copy(packageName = intent.value,
            aarName = displayName(intent.value, form.suffix))
        is JunkCodeIntent.SuffixChanged -> form.copy(suffix = intent.value,
            aarName = displayName(form.packageName, intent.value))
        is JunkCodeIntent.PackageCountChanged -> form.copy(packageCount = intent.value)
        is JunkCodeIntent.ActivityCountChanged -> form.copy(activityCountPerPackage = intent.value)
        is JunkCodeIntent.ResPrefixChanged -> form.copy(resPrefix = intent.value)
        else -> form
    }
    fun multi(form: MultiJunkForm, intent: JunkCodeIntent): MultiJunkForm = when (intent) {
        is JunkCodeIntent.OutputDirChanged -> form.copy(outputDir = intent.value)
        is JunkCodeIntent.AarCountChanged -> form.copy(aarCount = intent.value)
        is JunkCodeIntent.LeastPackagesChanged -> form.copy(leastPackageCount = intent.value)
        is JunkCodeIntent.MaximumPackagesChanged -> form.copy(maximumPackageCount = intent.value)
        is JunkCodeIntent.LeastActivitiesChanged -> form.copy(leastActivityCountPerPackage = intent.value)
        is JunkCodeIntent.MaximumActivitiesChanged -> form.copy(maximumActivityCountPerPackage = intent.value)
        else -> form
    }
    private fun displayName(packageName: String, suffix: String) = "junk_${packageName.replace('.', '_')}_${suffix}_TT2.2.0.aar"
}

fun JunkCodeUiState.configuration(): JunkConfiguration = when (mode) {
    JunkMode.SINGLE -> with(single) { JunkConfiguration.Single(packageName + "." + suffix,
        packageCount.toIntOrNull() ?: 0, activityCountPerPackage.toIntOrNull() ?: 0, resPrefix) }
    JunkMode.MULTI -> with(multi) { JunkConfiguration.Multi(outputDir, aarCount.toIntOrNull() ?: 0,
        leastPackageCount.toIntOrNull() ?: 0, maximumPackageCount.toIntOrNull() ?: 0,
        leastActivityCountPerPackage.toIntOrNull() ?: 0, maximumActivityCountPerPackage.toIntOrNull() ?: 0) }
}

/** The original button validates both mode drafts, including its isEmpty/isBlank distinction. */
fun JunkCodeUiState.hasMissingFields(): Boolean = outputPath.isBlank() || with(single) {
    packageName.isBlank() || suffix.isBlank() || packageCount.isBlank() || activityCountPerPackage.isEmpty() || resPrefix.isBlank()
} || with(multi) {
    outputDir.isBlank() || aarCount.isBlank() || leastPackageCount.isBlank() || maximumPackageCount.isBlank() ||
        leastActivityCountPerPackage.isEmpty() || maximumActivityCountPerPackage.isBlank()
}
