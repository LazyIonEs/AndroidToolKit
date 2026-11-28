package org.tool.kit.data.source.rules

import kotlinx.serialization.Serializable
import org.tool.kit.domain.apk.ApkLibRuleDetail

@Serializable
data class RulesMetadataDto(
    val schemaVersion: Int,
    val dataVersion: Long,
    val ruleCount: Int,
    val contentSha256: String = "",
    val sourceRevision: String = "",
    val minimumReader: Map<String, Int> = emptyMap(),
)

@Serializable
data class CoreRulesFileDto(
    val schemaVersion: Int,
    val rules: List<CoreRuleDto> = emptyList(),
)

@Serializable
data class CoreRuleDto(
    val id: Int,
    val uuid: String,
    val name: String,
    val label: String,
    val type: Int,
    val iconIndex: Int = -1,
    val iconId: String? = null,
    val isSimpleColorIcon: Boolean = false,
    val isRegexRule: Boolean = false,
    val regexName: String? = null,
    val priority: Int = 0,
    val detailPath: String? = null,
    val labelEn: String? = null,
)

@Serializable
data class RulesManifestDto(
    val schemaVersion: Int,
    val dataVersion: Long,
    val ruleCount: Int,
    val contentSha256: String = "",
    val sourceRevision: String = "",
    val minimumReader: Map<String, Int> = emptyMap(),
    val artifacts: Map<String, RulesArtifactDto> = emptyMap(),
)

@Serializable
data class RulesArtifactDto(
    val path: String,
    val sha256: String,
    val size: Long,
    val schemaVersion: Int,
    val minimumReaderVersion: Int = 5,
)

@Serializable
data class CanonicalLibraryDto(
    val schemaVersion: Int = 5,
    val uuid: String = "",
    val iconId: String? = null,
    val data: List<LocaleEntryDto> = emptyList(),
    val matchers: List<CanonicalMatcherDto> = emptyList(),
)

@Serializable
data class CanonicalMatcherDto(
    val id: Int = 0,
    val detailData: List<LocaleEntryDto>? = null,
)

@Serializable
data class LocaleEntryDto(
    val locale: String = "",
    val data: LocaleDetailDto? = null,
)

@Serializable
data class LocaleDetailDto(
    val label: String = "",
    val dev_team: String = "",
    val description: String = "",
    val source_link: String = "",
    val rule_contributors: List<String> = emptyList(),
)

internal fun resolveLocalizedDetail(
    library: CanonicalLibraryDto,
    uuid: String,
    ruleId: Int,
    preferChinese: Boolean,
): ApkLibRuleDetail? {
    val entries = library.matchers.firstOrNull { it.id == ruleId }?.detailData ?: library.data
    val validEntries = entries.filter { it.data != null }
    if (validEntries.isEmpty()) return null
    val selected = if (preferChinese) {
        validEntries.firstOrNull { it.locale.equals("zh-Hans", ignoreCase = true) }
            ?: validEntries.firstOrNull { it.locale.startsWith("zh", ignoreCase = true) }
            ?: validEntries.firstOrNull { it.locale.startsWith("en", ignoreCase = true) }
            ?: validEntries.first()
    } else {
        validEntries.firstOrNull { it.locale.equals("en", ignoreCase = true) }
            ?: validEntries.firstOrNull { it.locale.startsWith("en", ignoreCase = true) }
            ?: validEntries.first()
    }
    val payload = selected.data ?: return null
    return ApkLibRuleDetail(
        uuid = uuid,
        ruleId = ruleId,
        label = payload.label,
        devTeam = payload.dev_team,
        description = payload.description,
        sourceLink = payload.source_link,
        contributors = payload.rule_contributors,
        locale = selected.locale,
    )
}
