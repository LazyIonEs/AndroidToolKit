package org.tool.kit.domain.apk

/** LibChecker v5 library rule classification codes. */
enum class ApkLibRuleType(val code: Int) {
    Native(0),
    Service(1),
    Activity(2),
    Receiver(3),
    Provider(4),
    Dex(5),
    Static(6),
    Permission(7),
    Metadata(8),
    Action(9);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): ApkLibRuleType? = byCode[code]
    }
}

/** Single normalized rule loaded from a v5 rules bundle. */
data class ApkLibRule(
    val id: Int,
    val uuid: String,
    val name: String,
    val label: String,
    val type: ApkLibRuleType,
    val iconIndex: Int,
    val iconId: String?,
    val isSimpleColorIcon: Boolean,
    val isRegexRule: Boolean,
    val regexName: String?,
    val priority: Int,
    val hasDetail: Boolean,
)

/** Metadata describing the currently active LibChecker v5 rules bundle. */
data class ApkLibRulesMetadata(
    val schemaVersion: Int,
    val dataVersion: Long,
    val ruleCount: Int,
    val contentSha256: String,
    val sourceRevision: String,
    val isBundled: Boolean = true,
)

/** Lightweight match result attached to APK entries and SDK summaries. */
data class ApkLibRuleMatch(
    val ruleId: Int,
    val uuid: String,
    val label: String,
    val type: ApkLibRuleType,
    val iconId: String?,
    val isSimpleColorIcon: Boolean,
    val hasDetail: Boolean,
    val matchedByRegex: Boolean,
    val ruleName: String,
)

/** Localized library detail fetched on demand from upstream or local cache. */
data class ApkLibRuleDetail(
    val uuid: String,
    val ruleId: Int,
    val label: String,
    val devTeam: String,
    val description: String,
    val sourceLink: String,
    val contributors: List<String>,
    val locale: String,
)

/** Source category where a rule match originated during APK inspection. */
enum class ApkLibrarySource {
    Native,
    Activity,
    Service,
    Receiver,
    Provider,
    Dex,
    Static,
    Action,
}

/** Individual matched APK artifact paired with its source category and rule. */
data class ApkDetectedEntry(
    val name: String,
    val source: ApkLibrarySource,
    val rule: ApkLibRuleMatch,
)

/** Aggregated third-party SDK summary grouped by library UUID. */
data class ApkRecognizedSdk(
    val uuid: String,
    val label: String,
    val iconId: String?,
    val isSimpleColorIcon: Boolean,
    val hasDetail: Boolean,
    val primaryRuleId: Int,
    val sources: Set<ApkLibrarySource>,
    val entries: List<ApkDetectedEntry>,
)

/** Complete rule-matching outcome attached to an inspected APK. */
data class ApkRuleAnalysis(
    val sdks: List<ApkRecognizedSdk>,
    val dexMatches: List<ApkDetectedEntry>,
    val staticMatches: List<ApkDetectedEntry>,
    val actionMatches: List<ApkDetectedEntry>,
    val rulesVersion: Long,
)

/** Outcome status of checking and applying an online LibChecker rules update. */
enum class ApkRuleUpdateStatus {
    Updated,
    AlreadyUpToDate,
    Failed,
}

/** Result returned after checking or updating the LibChecker rules bundle. */
data class ApkRuleUpdateResult(
    val status: ApkRuleUpdateStatus,
    val metadata: ApkLibRulesMetadata?,
    val message: String? = null,
)
