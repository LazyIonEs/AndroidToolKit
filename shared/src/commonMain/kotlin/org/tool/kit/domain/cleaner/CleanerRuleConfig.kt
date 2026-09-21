package org.tool.kit.domain.cleaner

import kotlinx.serialization.Serializable

@Serializable
data class CleanerRuleConfig(
    val schemaVersion: Int = 1,
    val revision: Long = 0,
    val maxDepth: Int = 10,
    val includeHidden: Boolean = true,
    val rules: List<CleanerRuleGroup> = listOf(defaultBuildRule()),
)

@Serializable
data class CleanerRuleGroup(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val target: CleanerTarget = CleanerTarget.DIRECTORY,
    val relation: ConditionRelation = ConditionRelation.ALL,
    val conditions: List<CleanerCondition> = emptyList(),
    val defaultSelected: Boolean = false,
)

@Serializable enum class CleanerTarget { FILE, DIRECTORY }
@Serializable enum class ConditionRelation { ALL, ANY }
@Serializable enum class CleanerTextField { NAME, RELATIVE_PATH }
@Serializable enum class TextOperator { EQUALS, STARTS_WITH, ENDS_WITH, CONTAINS }
@Serializable enum class SizeUnit(val bytes: Long) { KB(1024), MB(1048576), GB(1073741824) }

@Serializable
sealed interface CleanerCondition {
    @Serializable data class Text(
        val field: CleanerTextField = CleanerTextField.NAME,
        val operator: TextOperator = TextOperator.EQUALS,
        val value: String = "",
    ) : CleanerCondition
    @Serializable data class FileSizeGreaterThan(
        val bytes: Long = 0,
        val displayValue: String = "",
        val displayUnit: SizeUnit = SizeUnit.MB,
    ) : CleanerCondition
}

fun defaultBuildRule() = CleanerRuleGroup(
    id = "android-build", name = "Android build", defaultSelected = true,
    conditions = listOf(CleanerCondition.Text(value = "build")),
)

fun CleanerRuleConfig.normalized() = copy(rules = rules.map { rule -> rule.copy(conditions = rule.conditions.map {
    if (it is CleanerCondition.Text && it.field == CleanerTextField.RELATIVE_PATH) it.copy(value = normalizeRelativePath(it.value)) else it
}) })

fun normalizeRelativePath(value: String) = value.replace('\\', '/')

/** Decimal arithmetic without floating point rounding or Long overflow; fractional bytes are truncated. */
fun sizeInBytes(value: String, unit: SizeUnit): Long? {
    if (!Regex("[0-9]+(\\.[0-9]+)?").matches(value) || value.length > 128) return null
    val decimals = value.substringAfter('.', "").length
    var carry = 0L
    val product = buildString {
        for (digit in value.filter { it != '.' }.reversed()) {
            val next = (digit - '0') * unit.bytes + carry
            append(('0'.code + (next % 10).toInt()).toChar()); carry = next / 10
        }
        while (carry > 0) { append(('0'.code + (carry % 10).toInt()).toChar()); carry /= 10 }
    }.reversed().padStart(decimals + 1, '0').dropLast(decimals).trimStart('0')
    return product.toLongOrNull()?.takeIf { it > 0 }
}

data class CleanerScanRequest(
    val root: String,
    val configRevision: Long,
    val maxDepth: Int,
    val includeHidden: Boolean,
    val rules: List<CleanerRuleGroup>,
) {
    companion object {
        fun from(root: String, config: CleanerRuleConfig) = CleanerScanRequest(root, config.revision,
            config.maxDepth, config.includeHidden, config.normalized().rules.filter { it.enabled }.map {
                it.copy(conditions = it.conditions.toList())
            })
    }
}
