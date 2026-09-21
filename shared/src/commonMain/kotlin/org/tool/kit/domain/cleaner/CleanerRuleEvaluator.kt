package org.tool.kit.domain.cleaner

enum class CleanerRuleIssue {
    NO_ENABLED_RULES, EMPTY_CONDITIONS, EMPTY_VALUE, INVALID_SIZE, NAME_SEPARATOR, ABSOLUTE_PATH,
    INVALID_DEPTH, DIRECTORY_SIZE, INVALID_SCHEMA, INVALID_ID, SPACE_WARNING, BROAD_WARNING,
    DUPLICATE_RULE, DUPLICATE_NAME,
}

data class CleanerRuleValidation(
    val errors: List<CleanerRuleIssue>,
    val warnings: List<CleanerRuleIssue>,
    val invalidDisabledIds: Set<String> = emptySet(),
) { val valid get() = errors.isEmpty() }

fun validateRule(rule: CleanerRuleGroup): List<CleanerRuleIssue> = buildList {
    if (rule.conditions.isEmpty()) add(CleanerRuleIssue.EMPTY_CONDITIONS)
    for (condition in rule.conditions) when (condition) {
        is CleanerCondition.Text -> {
            if (condition.value.isEmpty()) add(CleanerRuleIssue.EMPTY_VALUE)
            if (condition.field == CleanerTextField.NAME && condition.value.any { it == '/' || it == '\\' }) add(CleanerRuleIssue.NAME_SEPARATOR)
            val path = normalizeRelativePath(condition.value)
            if (condition.field == CleanerTextField.RELATIVE_PATH && (path.startsWith('/') || Regex("^[A-Za-z]:.*").matches(path))) add(CleanerRuleIssue.ABSOLUTE_PATH)
        }
        is CleanerCondition.FileSizeGreaterThan -> {
            if (rule.target == CleanerTarget.DIRECTORY) add(CleanerRuleIssue.DIRECTORY_SIZE)
            if (sizeInBytes(condition.displayValue, condition.displayUnit) != condition.bytes || condition.bytes <= 0) add(CleanerRuleIssue.INVALID_SIZE)
        }
    }
}.distinct()

fun ruleWarnings(rule: CleanerRuleGroup): List<CleanerRuleIssue> = buildList {
    val texts = rule.conditions.filterIsInstance<CleanerCondition.Text>()
    if (texts.any { it.value != it.value.trim() }) add(CleanerRuleIssue.SPACE_WARNING)
    if (texts.any { it.operator != TextOperator.EQUALS && it.value.length <= 2 } ||
        (rule.conditions.isNotEmpty() && rule.conditions.all { it is CleanerCondition.FileSizeGreaterThan })) add(CleanerRuleIssue.BROAD_WARNING)
}

fun validateCleanerRules(config: CleanerRuleConfig): CleanerRuleValidation {
    val errors = buildList {
        if (config.schemaVersion != 1 || config.revision < 0 || config.revision == Long.MAX_VALUE) add(CleanerRuleIssue.INVALID_SCHEMA)
        if (config.maxDepth !in 1..50) add(CleanerRuleIssue.INVALID_DEPTH)
        if (config.rules.none { it.enabled }) add(CleanerRuleIssue.NO_ENABLED_RULES)
        if (config.rules.any { it.id.isBlank() } || config.rules.map { it.id }.distinct().size != config.rules.size) add(CleanerRuleIssue.INVALID_ID)
        // An incomplete disabled draft is allowed, but a folder can never have a size condition.
        if (config.rules.any { it.target == CleanerTarget.DIRECTORY && it.conditions.any { c -> c is CleanerCondition.FileSizeGreaterThan } }) add(CleanerRuleIssue.DIRECTORY_SIZE)
        config.rules.filter { it.enabled }.forEach { addAll(validateRule(it)) }
    }
    val warnings = buildList {
        config.rules.forEach { addAll(ruleWarnings(it)) }
        if (config.rules.groupBy { it.name }.any { it.value.size > 1 }) add(CleanerRuleIssue.DUPLICATE_NAME)
        if (config.rules.groupBy { Triple(it.target, it.relation, it.conditions) }.any { it.value.size > 1 }) add(CleanerRuleIssue.DUPLICATE_RULE)
    }
    return CleanerRuleValidation(errors.distinct(), warnings.distinct(), config.rules.filter { !it.enabled && validateRule(it).isNotEmpty() }.map { it.id }.toSet())
}

/** Target gating always precedes ALL/ANY; invalid and disabled rules can never match. */
fun matchingCleanerRules(rules: List<CleanerRuleGroup>, target: CleanerTarget, name: String, relativePath: String, bytes: Long?): List<CleanerRuleGroup> =
    rules.filter { rule ->
        rule.enabled && rule.target == target && validateRule(rule).isEmpty() && run {
            val matches: (CleanerCondition) -> Boolean = { condition -> when (condition) {
                is CleanerCondition.FileSizeGreaterThan -> bytes != null && bytes > condition.bytes
                is CleanerCondition.Text -> {
                    val actual = if (condition.field == CleanerTextField.NAME) name else normalizeRelativePath(relativePath)
                    val expected = if (condition.field == CleanerTextField.NAME) condition.value else normalizeRelativePath(condition.value)
                    when (condition.operator) {
                        TextOperator.EQUALS -> actual == expected
                        TextOperator.STARTS_WITH -> actual.startsWith(expected)
                        TextOperator.ENDS_WITH -> actual.endsWith(expected)
                        TextOperator.CONTAINS -> expected in actual
                    }
                }
            } }
            if (rule.relation == ConditionRelation.ALL) rule.conditions.all(matches) else rule.conditions.any(matches)
        }
    }
