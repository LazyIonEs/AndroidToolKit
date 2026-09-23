package org.tool.kit.data.generator

import java.security.MessageDigest
import kotlin.random.Random

internal data class JunkLayoutResult(
    val xml: String,
    val ids: List<String>,
    val nodes: Int,
    val depth: Int,
    val containers: Int,
    val fingerprint: String,
    val types: Set<String>,
    val complexity: String,
    val attributes: Int,
)

/** Composes bounded trees from regions; no complete page is a fixed template. */
internal object JunkLayoutComposer {
    fun compose(
        layoutName: String,
        random: Random,
        pool: JunkResourcePool,
        customViews: List<String>,
        policy: JunkGenerationPolicy,
    ): JunkLayoutResult = Composition(layoutName, random, pool, customViews, policy).build()

    private enum class Region { TITLE, CONTENT, CARD, FORM, ACTION, PLACEHOLDER }

    private class Node(val type: String) {
        val attributes = linkedMapOf<String, String>()
        val children = mutableListOf<Node>()
        fun attribute(name: String, value: String) { attributes[name] = value }
    }

    private class Composition(
        private val layoutName: String,
        private val random: Random,
        private val pool: JunkResourcePool,
        customViews: List<String>,
        private val policy: JunkGenerationPolicy,
    ) {
        private val customViews = customViews.filter { it.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)+")) }
        private val complexity = listOf("simple", "medium", "complex").random(random)
        private val depthLimit = minOf(policy.maxLayoutDepth, when (complexity) {
            "simple" -> random.nextInt(2, 4)
            "medium" -> random.nextInt(3, 5)
            else -> random.nextInt(3, 6)
        })
        private val target = minOf(policy.maxLayoutNodes, when (complexity) {
            "simple" -> random.nextInt(6, 15)
            "medium" -> random.nextInt(15, 34)
            else -> random.nextInt(34, 69)
        })
        private var count = 0
        private var nextId = 0

        fun build(): JunkLayoutResult {
            require(policy.maxLayoutNodes >= 1 && policy.maxLayoutDepth >= 1 && policy.maxLayoutBytes >= 256) {
                "Layout limits must allow at least one node, one depth level, and 256 bytes"
            }
            val rootTypes = if (depthLimit < 3) listOf("LinearLayout", "RelativeLayout", "GridLayout") else containers
            val rootType = rootTypes.random(random)
            val root = container(rootType).apply {
                attribute("layout_width", "match_parent")
                attribute("layout_height", "match_parent")
                attribute("padding", "${random.nextInt(8, 25)}dp")
                if (random.nextBoolean()) attribute("background", backgrounds.random(random))
            }
            count++
            fill(root, 1, Region.entries.random(random), target - 1)
            cleanIds(root)
            var xml = render(root)
            while (xml.toByteArray(Charsets.UTF_8).size > policy.maxLayoutBytes && root.children.isNotEmpty()) {
                removeLastLeaf(root)
                cleanIds(root)
                xml = render(root)
            }
            require(xml.toByteArray(Charsets.UTF_8).size <= policy.maxLayoutBytes) { "maxLayoutBytes is too small for a legal root layout" }
            val all = flatten(root)
            val canonical = fingerprintTree(root)
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(it.toInt() and 255) }
            return JunkLayoutResult(
                xml, all.mapNotNull { it.attributes["id"]?.substringAfter('/') }, all.size,
                depth(root), all.count { it.type in containers }, digest,
                all.map { it.type }.toSet(), complexity, all.sumOf { it.attributes.size },
            )
        }

        private fun fill(parent: Node, parentDepth: Int, region: Region, allowance: Int) {
            if (parentDepth >= depthLimit || allowance <= 0) return
            val startCount = count
            val oneChild = parent.type == "FrameLayout" || parent.type == "ScrollView"
            while (count < target && count - startCount < allowance && (!oneChild || parent.children.isEmpty())) {
                val available = minOf(allowance - (count - startCount), target - count)
                val canNest = parentDepth + 1 < depthLimit && available >= 3
                val nested = canNest && (oneChild || random.nextInt(100) < 48)
                if (nested) {
                    val childRegion = Region.entries.random(random)
                    // A single-child wrapper needs a body which can actually hold the region.
                    val childType = if (oneChild) listOf("LinearLayout", "RelativeLayout", "GridLayout").random(random)
                        else containerFor(childRegion, parentDepth)
                    val child = container(childType)
                    attach(parent, child)
                    count++
                    val portion = if (oneChild) available - 1 else random.nextInt(2, minOf(available, 14))
                    fill(child, parentDepth + 1, childRegion, portion)
                } else {
                    val child = widget(region)
                    attach(parent, child)
                    count++
                }
            }
        }

        private fun containerFor(region: Region, parentDepth: Int): String = when (region) {
            Region.TITLE -> listOf("LinearLayout", "RelativeLayout").random(random)
            Region.CONTENT -> listOf("LinearLayout", "GridLayout", "RelativeLayout").random(random)
            Region.CARD -> if (parentDepth + 2 < depthLimit && random.nextBoolean()) "FrameLayout" else "LinearLayout"
            Region.FORM -> "LinearLayout"
            Region.ACTION -> listOf("LinearLayout", "GridLayout").random(random)
            Region.PLACEHOLDER -> listOf("FrameLayout", "RelativeLayout").random(random)
        }

        private fun container(type: String): Node = Node(type).apply {
            when (type) {
                "LinearLayout" -> {
                    attribute("orientation", if (random.nextInt(4) == 0) "horizontal" else "vertical")
                    if (random.nextBoolean()) attribute("gravity", listOf("start", "center_vertical", "center_horizontal").random(random))
                    attribute("baselineAligned", "false")
                }
                "GridLayout" -> {
                    attribute("columnCount", random.nextInt(1, 4).toString())
                    attribute("orientation", "horizontal")
                    attribute("alignmentMode", "alignMargins")
                }
                "ScrollView" -> attribute("fillViewport", "true")
            }
            if (random.nextInt(3) == 0) attribute("padding", "${random.nextInt(4, 13)}dp")
            if (random.nextInt(8) == 0) optionalResource(this, "background", "drawable")
        }

        /** Parent layout parameters are selected together with the actual parent type. */
        private fun attach(parent: Node, child: Node) {
            child.attribute("layout_height", "wrap_content")
            child.attribute("layout_width", "match_parent")
            if (parent.type == "LinearLayout" && parent.attributes["orientation"] == "horizontal") {
                child.attribute("layout_width", "0dp")
                child.attribute("layout_weight", random.nextInt(1, 4).toString())
                child.attribute("layout_gravity", "center_vertical")
            } else when (parent.type) {
                "FrameLayout" -> child.attribute("layout_gravity", listOf("center", "top|start", "top|center_horizontal").random(random))
                "GridLayout" -> {
                    child.attribute("layout_width", "0dp")
                    child.attribute("layout_columnWeight", "1")
                    child.attribute("layout_gravity", "fill_horizontal")
                }
                "RelativeLayout" -> {
                    child.attribute("layout_alignParentStart", "true")
                    parent.children.lastOrNull()?.let { previous ->
                        val id = previous.attributes["id"]?.substringAfter('/') ?: "${layoutName}_node_${nextId++}".also {
                            previous.attribute("id", "@+id/$it")
                        }
                        child.attribute("layout_below", "@id/$id")
                        if (random.nextBoolean()) child.attribute("layout_alignEnd", "@id/$id")
                    }
                }
            }
            if (random.nextBoolean()) child.attribute("layout_marginTop", "${random.nextInt(2, 9)}dp")
            if (random.nextInt(3) == 0) {
                child.attribute("layout_marginStart", "${random.nextInt(2, 9)}dp")
                child.attribute("layout_marginEnd", "${random.nextInt(2, 9)}dp")
            }
            parent.children += child
        }

        private fun widget(region: Region): Node {
            val type = if (customViews.isNotEmpty() && random.nextInt(12) == 0) customViews.random(random) else when (region) {
                Region.TITLE -> listOf("TextView", "TextView", "View").random(random)
                Region.CONTENT -> listOf("TextView", "TextView", "ImageView", "ProgressBar", "View").random(random)
                Region.CARD -> listOf("TextView", "ImageView", "TextView", "Button").random(random)
                Region.FORM -> listOf("TextView", "EditText", "CheckBox", "Switch", "SeekBar").random(random)
                Region.ACTION -> listOf("Button", "ToggleButton", "CheckBox").random(random)
                Region.PLACEHOLDER -> listOf("TextView", "ProgressBar", "View").random(random)
            }
            return Node(type).apply {
                when (type) {
                    "TextView", "Button", "CheckBox", "Switch", "ToggleButton" -> {
                        if (type == "ToggleButton") {
                            attribute("textOn", "Enabled")
                            attribute("textOff", "Disabled")
                        } else attribute("text", label())
                        attribute("textSize", "${if (region == Region.TITLE) random.nextInt(18, 27) else random.nextInt(12, 18)}sp")
                        if (type == "TextView") {
                            attribute("maxLines", random.nextInt(1, 5).toString())
                            attribute("ellipsize", "end")
                            attribute("textColor", foregrounds.random(random))
                            if (random.nextBoolean()) attribute("gravity", listOf("start", "center", "end").random(random))
                            if (region == Region.TITLE && random.nextBoolean()) attribute("textStyle", "bold")
                        }
                        if (type == "CheckBox" || type == "Switch" || type == "ToggleButton") attribute("checked", random.nextBoolean().toString())
                        if (type == "Button") attribute("minHeight", "48dp")
                    }
                    "EditText" -> {
                        attribute("hint", label())
                        attribute("inputType", listOf("text", "textCapSentences", "number", "textEmailAddress").random(random))
                        attribute("maxLength", random.nextInt(16, 65).toString())
                        attribute("singleLine", "true")
                        attribute("imeOptions", "actionDone")
                        attribute("minHeight", "48dp")
                    }
                    "ImageView" -> {
                        optionalResource(this, "src", if (random.nextInt(4) == 0) "mipmap" else "drawable")
                        attribute("contentDescription", "Preview")
                        attribute("adjustViewBounds", "true")
                        attribute("scaleType", listOf("centerInside", "fitCenter", "centerCrop").random(random))
                        attribute("minHeight", "${random.nextInt(32, 97)}dp")
                        attribute("maxHeight", "128dp")
                    }
                    "ProgressBar", "SeekBar" -> {
                        if (type == "ProgressBar") attribute("style", "@android:style/Widget.ProgressBar.Horizontal")
                        attribute("indeterminate", "false")
                        attribute("max", "100")
                        attribute("progress", random.nextInt(101).toString())
                    }
                    else -> {
                        attribute("minHeight", if (type == "View") "${random.nextInt(2, 13)}dp" else "${random.nextInt(24, 65)}dp")
                        attribute("background", backgrounds.random(random))
                    }
                }
                if (random.nextInt(4) == 0) attribute("padding", "${random.nextInt(2, 13)}dp")
                if (random.nextInt(10) == 0) optionalResource(this, "background", "drawable")
                if (random.nextInt(20) == 0 && type != "EditText") attribute("visibility", "invisible")
            }
        }

        private fun label(): String {
            if (random.nextInt(4) == 0) pool.request("string", random)?.let { return "@string/$it" }
            return labels.random(random) + if (random.nextBoolean()) " ${qualifiers.random(random)}" else ""
        }

        private fun optionalResource(node: Node, attribute: String, type: String) {
            pool.request(type, random)?.let { node.attribute(attribute, "@$type/$it") }
        }

        private fun cleanIds(root: Node) {
            val all = flatten(root)
            val declared = all.mapNotNull { it.attributes["id"]?.substringAfter('/') }.toSet()
            all.forEach { node -> node.attributes.entries.removeAll { it.value.startsWith("@id/") && it.value.substringAfter('/') !in declared } }
            val referenced = all.flatMap { it.attributes.values }.filter { it.startsWith("@id/") }.map { it.substringAfter('/') }.toSet()
            all.forEach { node -> if (node.attributes["id"]?.substringAfter('/') !in referenced) node.attributes.remove("id") }
        }

        private fun removeLastLeaf(root: Node) {
            val last = root.children.last()
            if (last.children.isEmpty()) root.children.removeAt(root.children.lastIndex) else removeLastLeaf(last)
        }

        private fun flatten(root: Node): List<Node> = buildList { add(root); root.children.forEach { addAll(flatten(it)) } }
        private fun depth(root: Node): Int = 1 + (root.children.maxOfOrNull { depth(it) } ?: 0)

        private fun render(root: Node): String = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            fun appendNode(node: Node, level: Int) {
                val indent = "    ".repeat(level)
                append(indent).append('<').append(node.type)
                if (level == 0) append(" xmlns:android=\"http://schemas.android.com/apk/res/android\"")
                node.attributes.forEach { (name, value) ->
                    append('\n').append(indent).append("    ")
                    if (name != "style") append("android:")
                    append(name).append("=\"").append(escape(value)).append('"')
                }
                if (node.children.isEmpty()) append(" />\n") else {
                    append(">\n")
                    node.children.forEach { appendNode(it, level + 1) }
                    append(indent).append("</").append(node.type).append(">\n")
                }
            }
            appendNode(root, 0)
        }

        private fun fingerprintTree(root: Node): String = buildString {
            append(if (root.type.contains('.')) "CustomView" else root.type).append('[')
            root.attributes.entries.sortedBy { it.key }.forEach { (key, value) ->
                append(key).append('=')
                append(when {
                    value.startsWith("@") -> value.substringBefore('/')
                    key in structuralAttributes -> value
                    else -> "_"
                }).append(';')
            }
            append(']')
            root.children.forEach { append('(').append(fingerprintTree(it)).append(')') }
        }

        private fun escape(value: String): String = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private val containers = listOf("LinearLayout", "FrameLayout", "RelativeLayout", "GridLayout", "ScrollView")
    private val labels = listOf("Overview", "Details", "Available items", "Review", "Continue", "Summary", "Options", "Recent entries", "Preferences", "Selection")
    private val qualifiers = listOf("today", "saved", "pending", "ready", "optional", "local")
    private val backgrounds = listOf("#F2F5F8", "#E7EFEA", "#F5EEDF", "#E9E7F0", "#DDE8ED")
    private val foregrounds = listOf("#233747", "#394E44", "#51432D", "#3E3655")
    private val structuralAttributes = setOf("orientation", "gravity", "layout_gravity", "inputType", "ellipsize", "visibility", "textStyle", "scaleType", "layout_width", "layout_height", "columnCount")
}
