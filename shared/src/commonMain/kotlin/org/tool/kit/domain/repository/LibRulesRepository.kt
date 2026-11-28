package org.tool.kit.domain.repository

import org.tool.kit.domain.apk.ApkLibRuleDetail
import org.tool.kit.domain.apk.ApkLibRulesMetadata
import org.tool.kit.domain.apk.ApkRuleUpdateResult
import org.tool.kit.domain.apk.LibRuleMatcher

/** Provides compiled LibChecker v5 rules, SVG icon bytes, cached cloud details, and updates. */
interface LibRulesRepository {
    suspend fun metadata(): ApkLibRulesMetadata
    suspend fun matcher(): LibRuleMatcher
    suspend fun iconBytes(iconId: String): ByteArray?
    suspend fun detail(uuid: String, ruleId: Int, preferChinese: Boolean): Result<ApkLibRuleDetail?>
    suspend fun checkAndUpdate(): ApkRuleUpdateResult
}
