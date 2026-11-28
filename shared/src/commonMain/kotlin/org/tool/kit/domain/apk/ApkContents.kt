package org.tool.kit.domain.apk

enum class ApkComponentType { Activity, ActivityAlias, Service, Receiver, Provider }
enum class ApkExportedDeclaration { Enabled, Disabled, Unspecified, Unknown }

data class ApkComponent(
    val name: String,
    val type: ApkComponentType,
    val exported: ApkExportedDeclaration,
    val process: String,
    val targetActivity: String? = null,
    val rule: ApkLibRuleMatch? = null,
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
    val rule: ApkLibRuleMatch? = null,
)

/** Extra manifest declarations used for Static and Action library matching. */
data class ApkManifestExtras(
    val staticLibraries: List<String> = emptyList(),
    val intentActions: List<String> = emptyList(),
)

enum class ApkArchivePreviewKind { Image, Xml }

sealed interface ApkArchiveEntryContent {
    data class Xml(val content: String) : ApkArchiveEntryContent
    data class Image(val source: ApkIconSource, val isSvg: Boolean = false) : ApkArchiveEntryContent
}

private val PREVIEW_IMAGE_EXTENSIONS = setOf("png", "webp", "jpg", "jpeg", "gif", "bmp", "svg")

/** Return the preview kind supported for this archive entry, excluding `.9.png` nine-patch bitmaps. */
fun ApkArchiveFile.previewKind(): ApkArchivePreviewKind? {
    val lower = path.lowercase()
    if (lower.endsWith(".9.png")) return null
    val extension = lower.substringAfterLast('.', "")
    return when {
        extension == "xml" -> ApkArchivePreviewKind.Xml
        extension in PREVIEW_IMAGE_EXTENSIONS -> ApkArchivePreviewKind.Image
        else -> null
    }
}

data class ApkArchiveInformation(
    val files: List<ApkArchiveFile>,
    val nativeLibraries: List<ApkNativeLibrary>,
    /** ZIP headers, directory records and signing blocks are outside compressed entry payloads. */
    val overheadBytes: Long,
)
