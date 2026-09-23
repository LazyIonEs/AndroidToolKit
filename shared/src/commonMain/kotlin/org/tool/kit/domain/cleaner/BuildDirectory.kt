package org.tool.kit.domain.cleaner

/** A scan snapshot. The absolute path remains the identity even when names repeat. */
data class BuildDirectory(
    val scanRoot: String,
    val path: String,
    val displayPath: String,
    val bytes: Long,
    val modifiedAt: Long,
    val isDirectory: Boolean,
    val exists: Boolean,
    val matchedRuleIds: Set<String> = emptySet(),
    val matchedRuleNames: List<String> = emptyList(),
    val defaultSelected: Boolean = true,
    val request: CleanerScanRequest? = null,
    val fileKey: String? = null,
)

data class DeleteBuildCacheResult(
    val directory: BuildDirectory,
    val deleted: Boolean,
    val isDirectory: Boolean,
    val exists: Boolean,
    val safetyFailure: Boolean = false,
)
