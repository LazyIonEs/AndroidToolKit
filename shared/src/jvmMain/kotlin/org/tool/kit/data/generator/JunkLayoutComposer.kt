package org.tool.kit.data.generator

import java.security.MessageDigest
import kotlin.random.Random
import org.tool.kit.data.generator.JunkDrawableUse.CONTROL
import org.tool.kit.data.generator.JunkDrawableUse.ICON
import org.tool.kit.data.generator.JunkDrawableUse.SURFACE
import org.tool.kit.data.generator.JunkGenerationProfile.Input
import org.tool.kit.data.generator.JunkGenerationProfile.Program
import org.tool.kit.data.generator.JunkGenerationProfile.Roles

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

    private enum class PagePurpose(
        val region: Region,
        val minimumDepth: Int,
        val nestingChance: Int,
        val sectionTypes: List<String>,
    ) {
        DETAIL(Region.CONTENT, 3, 52, listOf("GridLayout", "RelativeLayout", "LinearLayout")),
        FORM(Region.FORM, 3, 62, listOf("LinearLayout", "RelativeLayout", "GridLayout")),
        LIST(Region.CARD, 4, 82, listOf("LinearLayout", "LinearLayout", "RelativeLayout")),
        SETTINGS(Region.FORM, 4, 68, listOf("LinearLayout", "LinearLayout", "GridLayout")),
    }

    private enum class AdjustmentPage { RANGE_FORM, VALUE_SETTINGS }

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
        private val layoutProfile = policy.profile?.takeIf { it.usesPurposeSpecificLayouts }
        private val purpose = layoutProfile?.let { profile ->
            when (profile.roles) {
                Roles.MODELS -> PagePurpose.DETAIL
                Roles.RULES -> PagePurpose.FORM
                Roles.COLLECTIONS -> PagePurpose.LIST
                Roles.CONFIGURATION -> PagePurpose.SETTINGS
            }
        }
        private val adjustmentPage = layoutProfile
            ?.takeIf { it.usesAdjustmentPageLayouts && it.program == Program.ARITHMETIC }
            ?.let { profile ->
                when (purpose) {
                    PagePurpose.FORM -> AdjustmentPage.RANGE_FORM.takeIf { profile.input == Input.PAIR }
                    PagePurpose.SETTINGS -> AdjustmentPage.VALUE_SETTINGS
                    else -> null
                }
            }
        private val contentWidgets = layoutProfile?.let(::widgetsForProfile).orEmpty()
        private val complexity = listOf("simple", "medium", "complex").random(random)
        private val sampledDepth = when (complexity) {
            "simple" -> random.nextInt(2, 4)
            "medium" -> random.nextInt(3, 5)
            else -> random.nextInt(3, 6)
        }
        private val depthLimit = minOf(policy.maxLayoutDepth, maxOf(sampledDepth, purpose?.minimumDepth ?: 1))
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
            val rootType = when (purpose) {
                PagePurpose.DETAIL -> if (layoutProfile?.input == Input.TEXT) "RelativeLayout" else "GridLayout"
                PagePurpose.FORM -> if (layoutProfile?.input == Input.PAIR) "RelativeLayout" else "LinearLayout"
                PagePurpose.LIST, PagePurpose.SETTINGS -> if (depthLimit >= 4) "ScrollView" else "LinearLayout"
                null -> {
                    val rootTypes = if (depthLimit < 3) listOf("LinearLayout", "RelativeLayout", "GridLayout") else containers
                    rootTypes.random(random)
                }
            }
            val root = container(rootType).apply {
                attribute("layout_width", "match_parent")
                attribute("layout_height", "match_parent")
                attribute("padding", "${random.nextInt(8, 25)}dp")
                if (random.nextBoolean()) attribute("background", backgrounds.random(random))
            }
            count++
            if (purpose == null) fill(root, 1, Region.entries.random(random), target - 1)
            else fillPage(root, purpose)
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
                depth(root), all.count { it.type in containers || it.type == "RadioGroup" }, digest,
                all.map { it.type }.toSet(), complexity, all.sumOf { it.attributes.size },
            )
        }

        private fun fillPage(root: Node, page: PagePurpose) {
            val scroll = root.type == "ScrollView"
            val body = if (scroll) container("LinearLayout").also { attach(root, it); count++ } else root
            val bodyDepth = if (scroll) 2 else 1
            if (body.type == "LinearLayout") body.attribute("orientation", "vertical")
            val heading = widget(Region.TITLE, "TextView")
            if (body.type == "GridLayout") {
                body.attribute("columnCount", "2")
                heading.attribute("layout_columnSpan", "2")
            }
            attach(body, heading)
            count++

            when (adjustmentPage) {
                AdjustmentPage.RANGE_FORM -> {
                    fillRangeForm(body, bodyDepth)
                    return
                }
                AdjustmentPage.VALUE_SETTINGS -> {
                    fillValueSettings(body, bodyDepth)
                    return
                }
                null -> Unit
            }

            // Give the first record its own row when the depth budget permits a container and its contents.
            if (page == PagePurpose.LIST && bodyDepth + 2 <= depthLimit) {
                val row = pageSection("LinearLayout", page, page.region)
                attach(body, row)
                attach(row, widget(page.region, contentWidgets.first()))
                attach(row, widget(page.region, "TextView"))
                count += 3
            } else {
                attach(body, widget(page.region, contentWidgets.first()))
                count++
            }
            val hasSubmit = page == PagePurpose.FORM
            fill(body, bodyDepth, page.region, target - count - if (hasSubmit) 1 else 0)
            if (hasSubmit) {
                attach(body, widget(Region.ACTION, "Button"))
                count++
            }
        }

        private fun fillRangeForm(body: Node, bodyDepth: Int) {
            val canGroup = bodyDepth + 2 <= depthLimit
            var rangeIndex = 0
            // Reserve room for the submit action while adding complete pairs of bound fields.
            while (target - count >= 3) {
                rangeIndex++
                val prefix = if (rangeIndex == 1) "" else "Range $rangeIndex "
                val fields = if (canGroup && target - count >= 4) {
                    container("LinearLayout").apply { attribute("orientation", "horizontal") }
                        .also { addPageNode(body, it) }
                } else body
                addPageNode(fields, widget(Region.FORM, "EditText", "${prefix}Lower bound"))
                addPageNode(fields, widget(Region.FORM, "EditText", "${prefix}Upper bound"))
                if (canGroup && target - count >= 4) {
                    val mode = container("RadioGroup").apply {
                        attribute("orientation", "vertical")
                        attribute("contentDescription", "${prefix}Range behavior")
                    }
                    addPageNode(body, mode)
                    addPageNode(mode, widget(Region.FORM, "RadioButton", "Clamp"))
                    addPageNode(mode, widget(Region.FORM, "RadioButton", "Rescale"))
                    if (target - count >= 2 && random.nextBoolean()) {
                        addPageNode(mode, widget(Region.FORM, "RadioButton", "Wrap"))
                    }
                }
            }
            if (target - count == 2) addPageNode(body, widget(Region.CONTENT, "ProgressBar", "Range preview"))
            addPageNode(body, widget(Region.ACTION, "Button"))
        }

        private fun fillValueSettings(body: Node, bodyDepth: Int) {
            addPageNode(body, widget(Region.FORM, "Switch", "Enable adjustments"))
            addSliderSetting(body, bodyDepth)
            while (count < target) {
                if (target - count >= 2 && random.nextInt(4) != 0) {
                    addSliderSetting(body, bodyDepth)
                } else {
                    addPageNode(body, widget(Region.FORM, "Switch", switchSettingLabels.random(random)))
                }
            }
        }

        private fun addSliderSetting(body: Node, bodyDepth: Int) {
            val title = sliderSettingLabels.random(random)
            val row = if (bodyDepth + 2 <= depthLimit && target - count >= 3) {
                container("LinearLayout").apply {
                    attribute("orientation", if (random.nextBoolean()) "horizontal" else "vertical")
                }.also { addPageNode(body, it) }
            } else body
            addPageNode(row, widget(Region.FORM, "TextView", title))
            addPageNode(row, widget(Region.FORM, "SeekBar", title))
        }

        private fun addPageNode(parent: Node, child: Node) {
            attach(parent, child)
            count++
        }

        private fun fill(parent: Node, parentDepth: Int, region: Region, allowance: Int) {
            if (parentDepth >= depthLimit || allowance <= 0) return
            val startCount = count
            val oneChild = parent.type == "FrameLayout" || parent.type == "ScrollView"
            while (count < target && count - startCount < allowance && (!oneChild || parent.children.isEmpty())) {
                val available = minOf(allowance - (count - startCount), target - count)
                val canNest = parentDepth + 1 < depthLimit && available >= 3
                val nested = canNest && (oneChild || random.nextInt(100) < (purpose?.nestingChance ?: 48))
                if (nested) {
                    val childRegion = if (purpose == null) Region.entries.random(random)
                        else if (random.nextInt(6) == 0) Region.TITLE else purpose.region
                    // A single-child wrapper needs a body which can actually hold the region.
                    val child = if (purpose != null) pageSection(purpose.sectionTypes.random(random), purpose, childRegion)
                        else {
                            val childType = if (oneChild) listOf("LinearLayout", "RelativeLayout", "GridLayout").random(random)
                                else containerFor(childRegion, parentDepth)
                            container(childType)
                        }
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

        private fun pageSection(type: String, page: PagePurpose, region: Region): Node = container(type).apply {
            if (type == "LinearLayout") {
                val horizontal = region != Region.TITLE && when (page) {
                    PagePurpose.LIST -> true
                    PagePurpose.FORM -> layoutProfile?.input == Input.PAIR
                    PagePurpose.SETTINGS -> layoutProfile?.program != Program.TEXT
                    PagePurpose.DETAIL -> false
                }
                attribute("orientation", if (horizontal) "horizontal" else "vertical")
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

        private fun widget(region: Region, requiredType: String? = null, labelText: String? = null): Node {
            val type = requiredType ?: when {
                customViews.isNotEmpty() && random.nextInt(12) == 0 -> customViews.random(random)
                purpose != null -> if (region == Region.TITLE) "TextView" else contentWidgets.random(random)
                else -> when (region) {
                    Region.TITLE -> listOf("TextView", "TextView", "View").random(random)
                    Region.CONTENT -> listOf("TextView", "TextView", "ImageView", "ProgressBar", "View").random(random)
                    Region.CARD -> listOf("TextView", "ImageView", "TextView", "Button").random(random)
                    Region.FORM -> listOf("TextView", "EditText", "CheckBox", "Switch", "SeekBar").random(random)
                    Region.ACTION -> listOf("Button", "ToggleButton", "CheckBox").random(random)
                    Region.PLACEHOLDER -> listOf("TextView", "ProgressBar", "View").random(random)
                }
            }
            return Node(type).apply {
                when (type) {
                    "TextView", "Button", "CheckBox", "Switch", "ToggleButton", "RadioButton" -> {
                        if (type == "ToggleButton") {
                            attribute("textOn", "Enabled")
                            attribute("textOff", "Disabled")
                        } else attribute("text", labelText ?: if (requiredType == "Button" && region == Region.ACTION) "Apply" else label())
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
                        attribute("hint", labelText ?: label())
                        val inputTypes = when {
                            layoutProfile == null -> listOf("text", "textCapSentences", "number", "textEmailAddress")
                            layoutProfile.input == Input.TEXT || (purpose == PagePurpose.SETTINGS && layoutProfile.program == Program.TEXT) ->
                                listOf("text", "textCapSentences", "textEmailAddress")
                            else -> listOf("number", "number|numberSigned", "number|numberDecimal")
                        }
                        attribute("inputType", inputTypes.random(random))
                        attribute("maxLength", random.nextInt(16, 65).toString())
                        attribute("singleLine", "true")
                        attribute("imeOptions", "actionDone")
                        attribute("minHeight", "48dp")
                    }
                    "ImageView" -> {
                        val sharedIcons = policy.profile?.sharesVectorIcons == true
                        val iconType = if (sharedIcons) {
                            if (policy.resources.drawable > 0) "drawable" else "mipmap"
                        } else if (random.nextInt(4) == 0) "mipmap" else "drawable"
                        if (optionalResource(this, "src", iconType, ICON) && sharedIcons) {
                            attribute("tint", foregrounds.random(random))
                        }
                        attribute("contentDescription", "Preview")
                        attribute("adjustViewBounds", "true")
                        attribute("scaleType", listOf("centerInside", "fitCenter", "centerCrop").random(random))
                        attribute("minHeight", "${random.nextInt(32, 97)}dp")
                        attribute("maxHeight", "128dp")
                    }
                    "ProgressBar", "SeekBar" -> {
                        labelText?.let { attribute("contentDescription", it) }
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
                if (random.nextInt(10) == 0) {
                    val use = if (type in controls) CONTROL else SURFACE
                    optionalResource(this, "background", "drawable", use)
                }
                if (requiredType == null && random.nextInt(20) == 0 && type != "EditText") attribute("visibility", "invisible")
            }
        }

        private fun label(): String {
            if (random.nextInt(4) == 0) pool.request("string", random)?.let { return "@string/$it" }
            return labels.random(random) + if (random.nextBoolean()) " ${qualifiers.random(random)}" else ""
        }

        private fun optionalResource(node: Node, attribute: String, type: String, use: JunkDrawableUse = SURFACE): Boolean {
            val name = pool.request(type, random, use) ?: return false
            node.attribute(attribute, "@$type/$name")
            return true
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

    // The first entry is the required field, value, or preview for the page.
    private fun widgetsForProfile(profile: JunkGenerationProfile): List<String> = when (profile.roles) {
        Roles.MODELS -> when (profile.input) {
            Input.TEXT -> listOf("TextView", "TextView", "TextView", "TextView", "ImageView", "View")
            Input.INTEGER -> listOf("ProgressBar", "ProgressBar", "ProgressBar", "TextView", "TextView", "View")
            Input.PAIR, Input.WIDE -> listOf("TextView", "TextView", "ProgressBar", "ImageView", "View")
        }
        Roles.RULES -> when {
            profile.input == Input.TEXT -> listOf("EditText", "EditText", "EditText", "CheckBox", "TextView")
            profile.input == Input.PAIR && profile.program == Program.ARITHMETIC ->
                listOf("SeekBar", "SeekBar", "SeekBar", "EditText", "TextView")
            profile.input == Input.PAIR -> listOf("EditText", "EditText", "EditText", "CheckBox", "TextView")
            profile.program == Program.BRANCHING ->
                listOf("CheckBox", "CheckBox", "ToggleButton", "ToggleButton", "EditText", "TextView")
            else -> listOf("EditText", "EditText", "SeekBar", "TextView", "CheckBox")
        }
        Roles.COLLECTIONS -> if (profile.input == Input.TEXT)
            listOf("ImageView", "ImageView", "ImageView", "TextView", "TextView")
            else listOf("CheckBox", "CheckBox", "ProgressBar", "TextView", "TextView")
        Roles.CONFIGURATION -> when (profile.program) {
            Program.ARITHMETIC -> listOf("SeekBar", "SeekBar", "SeekBar", "Switch", "TextView")
            Program.BRANCHING -> listOf("Switch", "Switch", "Switch", "CheckBox", "TextView")
            Program.ACCUMULATION -> listOf("SeekBar", "ProgressBar", "TextView", "Switch")
            Program.TEXT -> listOf("EditText", "EditText", "TextView", "TextView", "TextView", "View")
        }
    }

    private val containers = listOf("LinearLayout", "FrameLayout", "RelativeLayout", "GridLayout", "ScrollView")
    private val controls = setOf("Button", "CheckBox", "Switch", "ToggleButton", "EditText", "SeekBar", "RadioButton")
    private val sliderSettingLabels = listOf("Adjustment limit", "Step size", "Fine tuning", "Tolerance")
    private val switchSettingLabels = listOf("Use last value", "Restore defaults at startup", "Enable fine tuning")
    private val labels = listOf("Overview", "Details", "Available items", "Review", "Continue", "Summary", "Options", "Recent entries", "Preferences", "Selection")
    private val qualifiers = listOf("today", "saved", "pending", "ready", "optional", "local")
    private val backgrounds = listOf("#F2F5F8", "#E7EFEA", "#F5EEDF", "#E9E7F0", "#DDE8ED")
    private val foregrounds = listOf("#233747", "#394E44", "#51432D", "#3E3655")
    private val structuralAttributes = setOf("orientation", "gravity", "layout_gravity", "inputType", "ellipsize", "visibility", "textStyle", "scaleType", "layout_width", "layout_height", "columnCount")
}
