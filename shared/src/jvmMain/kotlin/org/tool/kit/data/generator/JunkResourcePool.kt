package org.tool.kit.data.generator

import java.io.File
import kotlin.random.Random

/** Counts resource entries, rather than treating a values XML file as one resource. */
internal data class JunkResourceStats(
    val generated: Map<String, Int>,
    val requests: Map<String, Int>,
    val reused: Map<String, Int>,
)

internal enum class JunkDrawableUse { SURFACE, CONTROL, ICON }

/** An AAR-local pool. Each entry is written or buffered before its name is returned. */
internal class JunkResourcePool(
    private val workspace: File,
    private val prefix: String,
    private val budget: JunkResourceBudget,
    reuseIdenticalContent: Boolean = false,
    groupStringValues: Boolean = false,
    private val useDrawablePalettes: Boolean = false,
    private val shareVectorIcons: Boolean = false,
    private val shareResourcePresets: Boolean = false,
) {
    private val limits = linkedMapOf(
        "drawable" to budget.drawable, "mipmap" to budget.mipmap,
        "anim" to budget.anim, "string" to budget.string, "assets" to budget.assets,
    )
    private val names = limits.keys.associateWith { mutableListOf<String>() }
    private val requests = limits.keys.associateWith { 0 }.toMutableMap()
    private val reused = limits.keys.associateWith { 0 }.toMutableMap()
    private val namesByContent = if (reuseIdenticalContent || useDrawablePalettes || shareResourcePresets) {
        limits.keys.associateWith { mutableMapOf<String, String>() }
    } else emptyMap()
    private val namesByUse = mutableMapOf<Pair<String, JunkDrawableUse>, MutableList<String>>()
    private val groupedStrings = if (groupStringValues) mutableListOf<String>() else null
    private var total = 0

    init {
        require(budget.total >= 0 && limits.values.all { it >= 0 }) { "Resource budgets must be nonnegative" }
        require(budget.newResourceProbability in 0.0..1.0) { "Resource creation probability must be between 0 and 1" }
        require(prefix.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid resource prefix: $prefix" }
    }

    @Synchronized
    fun request(type: String, random: Random, drawableUse: JunkDrawableUse? = null): String? {
        val pool = requireNotNull(names[type]) { "Unsupported pooled resource type: $type" }
        val use = if (useDrawablePalettes) when (type) {
            "drawable" -> drawableUse ?: JunkDrawableUse.SURFACE
            "mipmap" -> JunkDrawableUse.ICON
            else -> null
        } else null
        val compatible = use?.let { namesByUse.getOrPut(type to it) { mutableListOf() } } ?: pool
        requests[type] = requests.getValue(type) + 1
        if (total < budget.total && pool.size < limits.getValue(type) && random.nextDouble() < budget.newResourceProbability) {
            val name = "${prefix}pool_${type}_${pool.size}"
            val content = render(type, name, random, use)
            val known = namesByContent[type]
            known?.get(content)?.let { existing -> return reuse(type, existing) }
            write(type, name, content)
            // Complete the write or buffer append before reserving the entry.
            known?.put(content, name)
            pool += name
            if (use != null) compatible += name
            total++
            return name
        }
        if (compatible.isEmpty()) return null
        return reuse(type, compatible[random.nextInt(compatible.size)])
    }

    private fun reuse(type: String, name: String): String {
        reused[type] = reused.getValue(type) + 1
        return name
    }

    @Synchronized
    fun symbols(): Map<String, List<String>> = names.filterKeys { it != "assets" }.mapValues { it.value.toList() }

    @Synchronized
    fun snapshot(): JunkResourceStats = JunkResourceStats(
        names.mapValues { it.value.size }, requests.toMap(), reused.toMap(),
    )

    /** Flush buffered string declarations before the workspace is archived. */
    @Synchronized
    fun finish() {
        val strings = groupedStrings?.takeIf { it.isNotEmpty() } ?: return
        writeFile(File(workspace, "res/values/${prefix}pool_strings.xml"), buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n")
            strings.forEach { append("    ").append(it).append('\n') }
            append("</resources>\n")
        })
    }

    private fun write(type: String, name: String, content: String) {
        if (type == "string" && groupedStrings != null) {
            groupedStrings += content
            return
        }
        val file = when (type) {
            "string" -> File(workspace, "res/values/$name.xml")
            "assets" -> File(workspace, "assets/$name.json")
            else -> File(workspace, "res/$type/$name.xml")
        }
        writeFile(file, content)
    }

    private fun writeFile(file: File, content: String) {
        check(file.parentFile.isDirectory || file.parentFile.mkdirs()) { "Cannot create resource directory: ${file.parent}" }
        file.writeText(content, Charsets.UTF_8)
    }

    private fun render(type: String, name: String, random: Random, use: JunkDrawableUse?): String = when (type) {
        "drawable", "mipmap" -> when (use) {
            null -> legacyDrawable(random)
            JunkDrawableUse.ICON -> {
                // Shared vectors describe only the geometry. Each ImageView supplies its own tint.
                val color = if (shareVectorIcons) "#36556F" else colors.random(random)
                vector(color, random)
            }
            JunkDrawableUse.SURFACE, JunkDrawableUse.CONTROL -> background(use, random)
        }
        "anim" -> animation(random)
        "string" -> {
            val entry = "<string name=\"$name\" translatable=\"false\">${labels.random(random)} ${random.nextInt(1, 100)}</string>"
            if (groupedStrings != null) entry else "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>$entry</resources>\n"
        }
        else -> "{\"version\":1,\"mode\":${random.nextInt(4)},\"limit\":${random.nextInt(8, 65)}}\n"
    }

    private fun legacyDrawable(random: Random): String {
        val color = colors.random(random)
        return if (random.nextBoolean()) {
            """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="$color" />
    <corners android:radius="${random.nextInt(2, 17)}dp" />
    <stroke android:width="1dp" android:color="${colors.random(random)}" />
    <padding android:left="8dp" android:top="6dp" android:right="8dp" android:bottom="6dp" />
</shape>
"""
        } else vector(color, random)
    }

    private fun vector(color: String, random: Random): String {
        val path = iconPaths.random(random)
        return """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="$color" android:pathData="$path" />
</vector>
"""
    }

    /** Use a shared fill for each background style in versions with resource presets. */
    private fun background(use: JunkDrawableUse, random: Random): String = buildString {
        val control = use == JunkDrawableUse.CONTROL
        val color = when {
            !shareResourcePresets -> surfaceColors.random(random)
            control -> surfaceColors[1]
            else -> surfaceColors[0]
        }
        val radius = if (control) 12 else 6
        appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        appendLine("""<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">""")
        appendLine("""    <solid android:color="$color" />""")
        appendLine("""    <corners android:radius="${radius}dp" />""")
        if (control) {
            appendLine("""    <stroke android:width="1dp" android:color="#36556F" />""")
        }
        appendLine("</shape>")
    }

    private fun animation(random: Random): String {
        val duration = if (shareResourcePresets) null else random.nextInt(80, 241)
        val namespace = " xmlns:android=\"http://schemas.android.com/apk/res/android\""
        val rootNamespace = if (shareResourcePresets) namespace else ""
        val body = when (random.nextInt(3)) {
            0 -> "<alpha$rootNamespace android:fromAlpha=\"0.6\" android:toAlpha=\"1.0\" android:duration=\"${duration ?: 180}\" />"
            1 -> "<translate$rootNamespace android:fromYDelta=\"4%\" android:toYDelta=\"0%\" android:duration=\"${duration ?: 220}\" />"
            else -> "<scale$rootNamespace android:fromXScale=\"0.96\" android:toXScale=\"1.0\" android:fromYScale=\"0.96\" android:toYScale=\"1.0\" android:pivotX=\"50%\" android:pivotY=\"50%\" android:duration=\"${duration ?: 160}\" />"
        }
        val root = if (shareResourcePresets) body else "<set$namespace>$body</set>"
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n$root\n"
    }

    private companion object {
        val labels = listOf("Overview", "Recent entries", "Details", "Options", "Continue", "Summary", "Review", "Available items")
        val colors = listOf("#E6EEF4", "#C7D8E8", "#DAE7DB", "#D9D1E3", "#F0E3D3", "#CEDFDF", "#36556F", "#526948")
        val surfaceColors = colors.take(6)
        val iconPaths = listOf("M4,4 L20,4 L20,20 L4,20 Z", "M12,2 L22,20 L2,20 Z",
            "M3,10 L10,10 L10,3 L14,3 L14,10 L21,10 L21,14 L14,14 L14,21 L10,21 L10,14 L3,14 Z")
    }
}
