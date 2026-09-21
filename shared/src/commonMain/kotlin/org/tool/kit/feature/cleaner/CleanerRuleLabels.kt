package org.tool.kit.feature.cleaner

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.cleaner.CleanerCondition
import org.tool.kit.domain.cleaner.CleanerRuleGroup
import org.tool.kit.domain.cleaner.CleanerRuleIssue
import org.tool.kit.domain.cleaner.CleanerTarget
import org.tool.kit.domain.cleaner.CleanerTextField
import org.tool.kit.domain.cleaner.ConditionRelation
import org.tool.kit.domain.cleaner.TextOperator
import org.tool.kit.domain.cleaner.defaultBuildRule
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.cleaner_absolute_path
import org.tool.kit.shared.generated.resources.cleaner_and
import org.tool.kit.shared.generated.resources.cleaner_broad_warning
import org.tool.kit.shared.generated.resources.cleaner_contains
import org.tool.kit.shared.generated.resources.cleaner_default_rule
import org.tool.kit.shared.generated.resources.cleaner_directory
import org.tool.kit.shared.generated.resources.cleaner_directory_name
import org.tool.kit.shared.generated.resources.cleaner_directory_size
import org.tool.kit.shared.generated.resources.cleaner_duplicate_name
import org.tool.kit.shared.generated.resources.cleaner_duplicate_rule
import org.tool.kit.shared.generated.resources.cleaner_empty_conditions
import org.tool.kit.shared.generated.resources.cleaner_empty_value
import org.tool.kit.shared.generated.resources.cleaner_ends_with
import org.tool.kit.shared.generated.resources.cleaner_equals
import org.tool.kit.shared.generated.resources.cleaner_file
import org.tool.kit.shared.generated.resources.cleaner_file_name
import org.tool.kit.shared.generated.resources.cleaner_file_size
import org.tool.kit.shared.generated.resources.cleaner_greater_than
import org.tool.kit.shared.generated.resources.cleaner_invalid_depth
import org.tool.kit.shared.generated.resources.cleaner_invalid_id
import org.tool.kit.shared.generated.resources.cleaner_invalid_schema
import org.tool.kit.shared.generated.resources.cleaner_invalid_size
import org.tool.kit.shared.generated.resources.cleaner_name_separator
import org.tool.kit.shared.generated.resources.cleaner_no_enabled_rules
import org.tool.kit.shared.generated.resources.cleaner_or
import org.tool.kit.shared.generated.resources.cleaner_relative_path
import org.tool.kit.shared.generated.resources.cleaner_space_warning
import org.tool.kit.shared.generated.resources.cleaner_starts_with
import org.tool.kit.shared.generated.resources.cleaner_summary
import org.tool.kit.shared.generated.resources.cleaner_value_not_set

@Composable
internal fun ruleName(rule: CleanerRuleGroup): String =
    if (rule.id == "android-build" && rule.name == defaultBuildRule().name) stringResource(Res.string.cleaner_default_rule) else rule.name

@Composable
internal fun targetLabel(target: CleanerTarget) =
    stringResource(if (target == CleanerTarget.FILE) Res.string.cleaner_file else Res.string.cleaner_directory)

@Composable
internal fun operatorLabel(operator: TextOperator) = stringResource(
    when (operator) {
        TextOperator.EQUALS -> Res.string.cleaner_equals
        TextOperator.STARTS_WITH -> Res.string.cleaner_starts_with
        TextOperator.ENDS_WITH -> Res.string.cleaner_ends_with
        TextOperator.CONTAINS -> Res.string.cleaner_contains
    }
)

@Composable
internal fun issueLabel(issue: CleanerRuleIssue) = stringResource(
    when (issue) {
        CleanerRuleIssue.NO_ENABLED_RULES -> Res.string.cleaner_no_enabled_rules
        CleanerRuleIssue.EMPTY_CONDITIONS -> Res.string.cleaner_empty_conditions
        CleanerRuleIssue.EMPTY_VALUE -> Res.string.cleaner_empty_value
        CleanerRuleIssue.INVALID_SIZE -> Res.string.cleaner_invalid_size
        CleanerRuleIssue.NAME_SEPARATOR -> Res.string.cleaner_name_separator
        CleanerRuleIssue.ABSOLUTE_PATH -> Res.string.cleaner_absolute_path
        CleanerRuleIssue.INVALID_DEPTH -> Res.string.cleaner_invalid_depth
        CleanerRuleIssue.DIRECTORY_SIZE -> Res.string.cleaner_directory_size
        CleanerRuleIssue.INVALID_SCHEMA -> Res.string.cleaner_invalid_schema
        CleanerRuleIssue.INVALID_ID -> Res.string.cleaner_invalid_id
        CleanerRuleIssue.SPACE_WARNING -> Res.string.cleaner_space_warning
        CleanerRuleIssue.BROAD_WARNING -> Res.string.cleaner_broad_warning
        CleanerRuleIssue.DUPLICATE_RULE -> Res.string.cleaner_duplicate_rule
        CleanerRuleIssue.DUPLICATE_NAME -> Res.string.cleaner_duplicate_name
    }
)

@Composable
internal fun conditionParts(
    target: CleanerTarget,
    condition: CleanerCondition
): Triple<String, String, String> =
    when (condition) {
        is CleanerCondition.Text -> Triple(
            stringResource(
                if (condition.field == CleanerTextField.RELATIVE_PATH) Res.string.cleaner_relative_path
                else if (target == CleanerTarget.FILE) Res.string.cleaner_file_name else Res.string.cleaner_directory_name
            ),
            operatorLabel(condition.operator),
            "“${condition.value.ifEmpty { stringResource(Res.string.cleaner_value_not_set) }}”",
        )

        is CleanerCondition.FileSizeGreaterThan -> Triple(
            stringResource(Res.string.cleaner_file_size),
            stringResource(Res.string.cleaner_greater_than),
            "${condition.displayValue.ifEmpty { stringResource(Res.string.cleaner_value_not_set) }} ${condition.displayUnit.name}",
        )
    }

@Composable
internal fun conditionLabel(target: CleanerTarget, condition: CleanerCondition): String {
    val (field, operator, value) = conditionParts(target, condition)
    return "$field $operator $value"
}

@Composable
internal fun ruleSummary(rule: CleanerRuleGroup): String {
    val conditions = rule.conditions.map { conditionLabel(rule.target, it) }
    val join =
        " ${stringResource(if (rule.relation == ConditionRelation.ALL) Res.string.cleaner_and else Res.string.cleaner_or)} "
    return stringResource(
        Res.string.cleaner_summary,
        targetLabel(rule.target),
        conditions.joinToString(join)
    )
}
