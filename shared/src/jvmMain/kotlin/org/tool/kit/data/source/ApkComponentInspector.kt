package org.tool.kit.data.source

import com.android.tools.apk.analyzer.BinaryXmlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.apk.*
import org.w3c.dom.Element
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

internal suspend fun inspectApkComponents(path: String): List<ApkComponent>? = try {
    ZipFile(path).use { zip ->
        currentCoroutineContext().ensureActive()
        val entry = zip.getEntry("AndroidManifest.xml") ?: return null
        // Resource decoding should not allocate unbounded memory for a corrupt manifest.
        require(entry.size in 0..16 * 1024 * 1024)
        val bytes = zip.getInputStream(entry).use { it.readNBytes(16 * 1024 * 1024 + 1) }
        require(bytes.size <= 16 * 1024 * 1024)
        val xml = BinaryXmlParser.decodeXml(bytes)
        currentCoroutineContext().ensureActive()
        readApkComponents(xml)
    }
} catch (cancelled: CancellationException) { throw cancelled }
catch (_: Exception) { null }

/** Parse the decoded XML tree, keeping explicit declarations distinct from Android defaults. */
internal fun readApkComponents(xml: ByteArray): List<ApkComponent> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }
    val root = xml.inputStream().use { factory.newDocumentBuilder().parse(it).documentElement }
    require(root.tagName == "manifest")
    val packageName = root.getAttribute("package")
    fun Element.children() = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    fun Element.android(name: String): String? = getAttributeNS("http://schemas.android.com/apk/res/android", name).takeIf { it.isNotBlank() }
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
        val exported = when (element.android("exported")) {
            null -> ApkExportedDeclaration.Unspecified
            "true" -> ApkExportedDeclaration.Enabled
            "false" -> ApkExportedDeclaration.Disabled
            else -> ApkExportedDeclaration.Unknown
        }
        val name = element.android("name")?.takeUnless { it.startsWith('@') || it.startsWith('?') }?.let(::className).orEmpty()
        ApkComponent(name, type, exported, process(element.android("process") ?: application.android("process")),
            element.android("targetActivity")?.let(::className))
    }
    val activities = result.filter { it.type == ApkComponentType.Activity }.associateBy { it.name }
    return result.map { if (it.type == ApkComponentType.ActivityAlias) it.copy(process = activities[it.targetActivity]?.process.orEmpty()) else it }
}
