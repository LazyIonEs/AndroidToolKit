package org.tool.kit.tests.domain

import kotlin.test.*
import org.junit.Test
import org.tool.kit.domain.cleaner.*

class CleanerRulesTest {
    private fun matches(rule: CleanerRuleGroup, name: String = "build", target: CleanerTarget = CleanerTarget.DIRECTORY, path: String = "app/$name", bytes: Long? = 1024) =
        matchingCleanerRules(listOf(rule), target, name, path, bytes).isNotEmpty()

    @Test fun exactFullNameCaseAndTypeAreIndependentFromAnyConditions() {
        val rule = defaultBuildRule()
        assertTrue(matches(rule)); assertFalse(matches(rule, "build.foo")); assertFalse(matches(rule, "Build"))
        assertFalse(matches(rule, target = CleanerTarget.FILE))
        assertFalse(matches(rule.copy(target = CleanerTarget.FILE, relation = ConditionRelation.ANY)))
        assertFalse(matches(rule.copy(enabled = false)))
    }

    @Test fun allAndAnyRespectTheTargetGateAndReturnEveryRuleInOrder() {
        val name = CleanerCondition.Text(value = "build")
        val path = CleanerCondition.Text(CleanerTextField.RELATIVE_PATH, TextOperator.STARTS_WITH, "other/")
        val all = defaultBuildRule().copy(conditions = listOf(name, path))
        val any = all.copy(id = "any", relation = ConditionRelation.ANY, defaultSelected = false)
        assertFalse(matches(all)); assertTrue(matches(any)); assertFalse(matches(any, target = CleanerTarget.FILE))
        val found = matchingCleanerRules(listOf(all, any, defaultBuildRule()), CleanerTarget.DIRECTORY, "build", "app/build", null)
        assertEquals(listOf("any", "android-build"), found.map { it.id })
        assertTrue(found.any { it.defaultSelected })
    }

    @Test fun textOperatorsPreserveWhitespaceAndNormalizeOnlyRelativePaths() {
        for (op in listOf(TextOperator.STARTS_WITH, TextOperator.ENDS_WITH, TextOperator.CONTAINS)) {
            val text = when (op) { TextOperator.STARTS_WITH -> "bu"; TextOperator.ENDS_WITH -> "ild"; else -> "uil" }
            assertTrue(matches(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(operator = op, value = text)))))
            assertFalse(matches(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(operator = op, value = "BUILD")))))
        }
        val relative = defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(CleanerTextField.RELATIVE_PATH, TextOperator.EQUALS, "app\\build")))
        assertTrue(matches(relative, path = "app/build")); assertTrue(matches(relative, path = "app\\build"))
        assertFalse(matches(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(value = " build ")))))
        assertTrue(matches(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(value = " build "))), name = " build "))
    }

    @Test fun sizeUsesStrictGreaterThanAndMissingSizeNeverMatches() {
        val rule = CleanerRuleGroup("log", "Logs", target = CleanerTarget.FILE, conditions = listOf(
            CleanerCondition.Text(operator = TextOperator.ENDS_WITH, value = ".log"),
            CleanerCondition.FileSizeGreaterThan(1024, "1", SizeUnit.KB)))
        assertFalse(matches(rule, "a.log", CleanerTarget.FILE, bytes = 1024))
        assertTrue(matches(rule, "a.log", CleanerTarget.FILE, bytes = 1025))
        assertFalse(matches(rule, "a.txt", CleanerTarget.FILE, bytes = 1025))
        assertFalse(matches(rule, "a.log", CleanerTarget.FILE, bytes = null))
        assertFalse(matches(rule.copy(target = CleanerTarget.DIRECTORY), "a.log", bytes = 1025))
    }

    @Test fun decimalUnitsConvertExactlyAndRejectOverflowInvalidOrSubByteValues() {
        assertEquals(1024, sizeInBytes("1", SizeUnit.KB))
        assertEquals(1048576, sizeInBytes("1", SizeUnit.MB))
        assertEquals(1073741824, sizeInBytes("1", SizeUnit.GB))
        assertEquals(1536, sizeInBytes("1.5", SizeUnit.KB))
        assertEquals(1, sizeInBytes("0.001", SizeUnit.KB))
        assertEquals(Long.MAX_VALUE, sizeInBytes("9007199254740991.9990234375", SizeUnit.KB))
        for (value in listOf("9007199254740992", "1e10", "Infinity", "NaN", "-1", "0", "", "0.00001", " 1 ", "1,5")) assertNull(sizeInBytes(value, SizeUnit.KB), value)
    }

    @Test fun validationBlocksInvalidEnabledRulesButPreservesIncompleteDisabledDrafts() {
        val empty = CleanerRuleGroup("empty", "Empty", conditions = emptyList())
        assertFalse(validateCleanerRules(CleanerRuleConfig(rules = listOf(empty))).valid)
        val draft = CleanerRuleConfig(rules = listOf(defaultBuildRule(), empty.copy(enabled = false)))
        assertTrue(validateCleanerRules(draft).valid)
        assertEquals(setOf("empty"), validateCleanerRules(draft).invalidDisabledIds)
        assertFalse(validateCleanerRules(draft.copy(rules = listOf(empty.copy(enabled = false)))).valid)
        for (depth in listOf(0, 51)) assertFalse(validateCleanerRules(draft.copy(maxDepth = depth)).valid)
        for (value in listOf("a/b", "a\\b")) assertTrue(CleanerRuleIssue.NAME_SEPARATOR in validateRule(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(value = value)))))
        for (value in listOf("/tmp", "C:\\tmp", "\\\\server\\share")) assertTrue(CleanerRuleIssue.ABSOLUTE_PATH in validateRule(defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(CleanerTextField.RELATIVE_PATH, value = value)))))
        val invalidFolder = defaultBuildRule().copy(id = "invalid-folder", enabled = false, conditions = listOf(CleanerCondition.FileSizeGreaterThan(1024, "1", SizeUnit.KB)))
        assertTrue(CleanerRuleIssue.DIRECTORY_SIZE in validateRule(invalidFolder))
        assertFalse(validateCleanerRules(CleanerRuleConfig(rules = listOf(defaultBuildRule(), invalidFolder))).valid)
    }

    @Test fun warningOnlyConfigurationRemainsSaveableAndIdsAreUnique() {
        val rule = defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(operator = TextOperator.CONTAINS, value = " a")))
        val validation = validateCleanerRules(CleanerRuleConfig(rules = listOf(rule, rule.copy(id = "copy"))))
        assertTrue(validation.valid)
        assertEquals(setOf(CleanerRuleIssue.SPACE_WARNING, CleanerRuleIssue.BROAD_WARNING, CleanerRuleIssue.DUPLICATE_NAME, CleanerRuleIssue.DUPLICATE_RULE), validation.warnings.toSet())
        assertFalse(validateCleanerRules(CleanerRuleConfig(rules = listOf(rule, rule))).valid)
    }

    @Test fun scanRequestCopiesEnabledRulesAndNormalizesPersistedPaths() {
        val conditions = mutableListOf<CleanerCondition>(CleanerCondition.Text(CleanerTextField.RELATIVE_PATH, value = "app\\build"))
        val rules = mutableListOf(defaultBuildRule().copy(conditions = conditions), defaultBuildRule().copy(id = "disabled", enabled = false))
        val request = CleanerScanRequest.from("root", CleanerRuleConfig(revision = 7, maxDepth = 3, includeHidden = false, rules = rules))
        conditions.clear(); rules.clear()
        assertEquals(7, request.configRevision); assertEquals(3, request.maxDepth); assertFalse(request.includeHidden)
        assertEquals("app/build", (request.rules.single().conditions.single() as CleanerCondition.Text).value)
    }
}
