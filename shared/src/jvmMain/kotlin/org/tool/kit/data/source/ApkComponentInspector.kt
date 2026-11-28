package org.tool.kit.data.source

import com.android.tools.apk.analyzer.BinaryXmlParser
import com.android.tools.apk.analyzer.ResourceIdResolver
import com.google.devrel.gmscore.tools.apk.arsc.ResourceFile
import com.google.devrel.gmscore.tools.apk.arsc.ResourceTableChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tool.kit.domain.apk.ApkComponent
import org.tool.kit.domain.apk.ApkComponentType
import org.tool.kit.domain.apk.ApkExportedDeclaration
import org.tool.kit.domain.apk.ApkManifestExtras
import org.w3c.dom.Element
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

private const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
private const val MAX_ARSC_BYTES = 64 * 1024 * 1024
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

private suspend fun decodeManifestXml(path: String, resolveResources: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
    ZipFile(path).use { zip ->
        currentCoroutineContext().ensureActive()
        val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
        // Resource decoding should not allocate unbounded memory for a corrupt manifest.
        require(entry.size in 0..MAX_MANIFEST_BYTES)
        val bytes = zip.getInputStream(entry).use { it.readNBytes(MAX_MANIFEST_BYTES + 1) }
        require(bytes.size <= MAX_MANIFEST_BYTES)
        val resolver = if (resolveResources) buildResourceIdResolver(zip) else ResourceIdResolver.NO_RESOLUTION
        val xml = BinaryXmlParser.decodeXml(bytes, resolver)
        currentCoroutineContext().ensureActive()
        xml
    }
}

internal fun buildResourceIdResolver(zip: ZipFile): ResourceIdResolver {
    val arscEntry = zip.getEntry("resources.arsc") ?: return ResourceIdResolver.NO_RESOLUTION
    if (arscEntry.size !in 1L..MAX_ARSC_BYTES.toLong()) return ResourceIdResolver.NO_RESOLUTION
    val idMap = runCatching {
        val arscBytes = zip.getInputStream(arscEntry).use { it.readNBytes(MAX_ARSC_BYTES + 1) }
        if (arscBytes.size > MAX_ARSC_BYTES) return ResourceIdResolver.NO_RESOLUTION
        val resourceFile = arscBytes.inputStream().use { ResourceFile.fromInputStream(it) }
        val table = resourceFile.chunks.firstOrNull() as? ResourceTableChunk ?: return ResourceIdResolver.NO_RESOLUTION
        buildMap<Int, String> {
            for (pkg in table.packages) {
                val pkgId = pkg.id and 0xFF
                for (typeChunk in pkg.typeChunks) {
                    val typeId = typeChunk.id and 0xFF
                    for ((entryId, entry) in typeChunk.entries) {
                        val resId = (pkgId shl 24) or (typeId shl 16) or (entryId and 0xFFFF)
                        putIfAbsent(resId, "@${entry.typeName()}/${entry.key()}")
                    }
                }
            }
        }
    }.getOrNull() ?: return ResourceIdResolver.NO_RESOLUTION
    return ResourceIdResolver { id -> idMap[id] ?: ResourceIdResolver.NO_RESOLUTION.resolve(id) }
}

private fun parseManifestRoot(xml: ByteArray): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    val root = xml.inputStream().use { factory.newDocumentBuilder().parse(it).documentElement }
    require(root.tagName == "manifest")
    return root
}

private fun Element.children(): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

private fun Element.androidAttribute(name: String): String? =
    getAttributeNS(ANDROID_NAMESPACE, name).takeIf { it.isNotBlank() }

internal suspend fun inspectApkComponents(path: String): List<ApkComponent>? = try {
    decodeManifestXml(path)?.let(::readApkComponents)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

internal suspend fun inspectApkManifestExtras(path: String): ApkManifestExtras? = try {
    decodeManifestXml(path)?.let(::readApkManifestExtras)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

internal suspend fun inspectDecodedManifestXml(path: String): String? = try {
    decodeManifestXml(path, resolveResources = true)?.toString(Charsets.UTF_8)
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

/** Parse the decoded XML tree, keeping explicit declarations distinct from Android defaults. */
internal fun readApkComponents(xml: ByteArray): List<ApkComponent> {
    val root = parseManifestRoot(xml)
    val packageName = root.getAttribute("package")
    fun className(value: String): String = when {
        value.startsWith('.') -> packageName + value
        '.' !in value && packageName.isNotBlank() -> "$packageName.$value"
        else -> value
    }
    fun process(value: String?): String = when {
        value == null -> packageName
        value.startsWith('@') || value.startsWith('?') -> ""
        value.startsWith(':') -> packageName + value
        else -> value
    }
    val application = root.children().firstOrNull { it.tagName == "application" } ?: return emptyList()
    val types = mapOf("activity" to ApkComponentType.Activity, "activity-alias" to ApkComponentType.ActivityAlias,
        "service" to ApkComponentType.Service, "receiver" to ApkComponentType.Receiver, "provider" to ApkComponentType.Provider)
    val result = application.children().mapNotNull { element ->
        val type = types[element.tagName] ?: return@mapNotNull null
        val exported = when (element.androidAttribute("exported")) {
            null -> ApkExportedDeclaration.Unspecified
            "true" -> ApkExportedDeclaration.Enabled
            "false" -> ApkExportedDeclaration.Disabled
            else -> ApkExportedDeclaration.Unknown
        }
        val name = element.androidAttribute("name")?.takeUnless { it.startsWith('@') || it.startsWith('?') }?.let(::className).orEmpty()
        ApkComponent(name, type, exported, process(element.androidAttribute("process") ?: application.androidAttribute("process")),
            element.androidAttribute("targetActivity")?.let(::className))
    }
    val activities = result.filter { it.type == ApkComponentType.Activity }.associateBy { it.name }
    return result.map { if (it.type == ApkComponentType.ActivityAlias) it.copy(process = activities[it.targetActivity]?.process.orEmpty()) else it }
}

/** Extract static/shared library declarations and component intent-filter actions from AndroidManifest.xml. */
internal fun readApkManifestExtras(xml: ByteArray): ApkManifestExtras {
    val root = parseManifestRoot(xml)
    val application = root.children().firstOrNull { it.tagName == "application" } ?: return ApkManifestExtras()
    val staticLibraries = linkedSetOf<String>()
    val intentActions = linkedSetOf<String>()
    val actionOwners = setOf("activity", "activity-alias", "service", "receiver")
    for (element in application.children()) {
        when (element.tagName) {
            "uses-library", "uses-static-library" -> {
                element.androidAttribute("name")
                    ?.takeUnless { it.startsWith('@') || it.startsWith('?') }
                    ?.let(staticLibraries::add)
            }
            in actionOwners -> {
                for (child in element.children()) {
                    if (child.tagName != "intent-filter") continue
                    for (filterChild in child.children()) {
                        if (filterChild.tagName != "action") continue
                        filterChild.androidAttribute("name")
                            ?.takeUnless { it.startsWith('@') || it.startsWith('?') }
                            ?.let(intentActions::add)
                    }
                }
            }
        }
    }
    return ApkManifestExtras(
        staticLibraries = staticLibraries.sorted(),
        intentActions = intentActions.sorted(),
    )
}
