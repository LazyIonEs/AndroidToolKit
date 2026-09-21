package org.tool.kit.domain.apk

enum class ApkComponentType { Activity, ActivityAlias, Service, Receiver, Provider }
enum class ApkExportedDeclaration { Enabled, Disabled, Unspecified, Unknown }

data class ApkComponent(
    val name: String,
    val type: ApkComponentType,
    val exported: ApkExportedDeclaration,
    val process: String,
    val targetActivity: String? = null,
)

enum class ApkFileCategory { Dex, Native, Resources, Assets, Metadata, Other }
data class ApkArchiveFile(val path: String, val size: Long, val compressedSize: Long, val category: ApkFileCategory)
enum class ApkAlignment { Aligned, Unaligned, NotApplicable, Unknown }
data class ApkNativeLibrary(
    val path: String,
    val abi: String,
    val size: Long,
    val compressedSize: Long,
    val compressed: Boolean,
    val elfAlignment: ApkAlignment,
    val zipAlignment: ApkAlignment,
)

data class ApkArchiveInformation(
    val files: List<ApkArchiveFile>,
    val nativeLibraries: List<ApkNativeLibrary>,
    /** ZIP headers, directory records and signing blocks are outside compressed entry payloads. */
    val overheadBytes: Long,
)
