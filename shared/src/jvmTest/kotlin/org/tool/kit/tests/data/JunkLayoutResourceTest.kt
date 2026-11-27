package org.tool.kit.tests.data

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.random.Random
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.generator.JunkDrawableUse
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkGenerationProfile
import org.tool.kit.data.generator.JunkLayoutComposer
import org.tool.kit.data.generator.JunkResourceBudget
import org.tool.kit.data.generator.JunkResourcePool
import org.w3c.dom.Element

class JunkLayoutResourceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun concurrentRequestsRespectBothBudgetsAndPublishOnlyCompleteCompatibleResources() {
        val work = temporary.newFolder()
        val pool = JunkResourcePool(work, "parallel_", JunkResourceBudget(total = 11, drawable = 4, mipmap = 3, anim = 2, string = 3, assets = 2, newResourceProbability = 1.0))
        val types = listOf("drawable", "mipmap", "anim", "string", "assets")
        val executor = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0 until 100).map { index -> Callable {
                val random = Random(index)
                repeat(20) {
                    val type = types.random(random)
                    pool.request(type, random)?.let { name ->
                        val file = resourceFile(work, type, name)
                        assertTrue(file.isFile && file.length() > 0)
                        if (type != "assets") parse(file.readText())
                    }
                }
            } }
            executor.invokeAll(jobs).forEach { it.get(20, TimeUnit.SECONDS) }
        } finally { executor.shutdownNow() }
        val stats = pool.snapshot()
        assertEquals(11, stats.generated.values.sum())
        assertTrue(stats.generated.getValue("drawable") <= 4)
        assertTrue(stats.generated.getValue("mipmap") <= 3)
        assertTrue(stats.generated.getValue("anim") <= 2)
        assertTrue(stats.generated.getValue("string") <= 3)
        assertTrue(stats.generated.getValue("assets") <= 2)
        assertEquals(2000, stats.requests.values.sum())
        assertTrue(stats.reused.values.sum() > 100)
        assertFalse("assets" in pool.symbols())
        assertEquals(11, work.walkTopDown().count { it.isFile })
        pool.symbols().forEach { (type, names) -> assertEquals(names.size, names.toSet().size); names.forEach { assertTrue(resourceFile(work, type, it).isFile) } }
    }

    @Test fun exhaustedPoolReusesMultipleExistingNamesAndEmptyPoolOmitsOptionalReferences() {
        val work = temporary.newFolder()
        val pool = JunkResourcePool(work, "reuse_", JunkResourceBudget(total = 3, drawable = 3, newResourceProbability = 1.0))
        val random = Random(42)
        val created = List(3) { assertNotNull(pool.request("drawable", random)) }.toSet()
        val reused = List(100) { assertNotNull(pool.request("drawable", random)) }.toSet()
        assertEquals(created, reused)
        assertNull(pool.request("mipmap", random))
        assertEquals(3, pool.snapshot().generated.values.sum())
        val empty = JunkResourcePool(temporary.newFolder(), "empty_", JunkResourceBudget(total = 0))
        val result = JunkLayoutComposer.compose("empty_screen", Random(2), empty, emptyList(), JunkGenerationPolicy(seed = 2))
        assertFalse(Regex("@(drawable|mipmap|string|anim)/").containsMatchIn(result.xml))
        assertTrue(result.nodes > 1)
        assertEquals(0, empty.snapshot().generated.values.sum())
    }

    @Test fun writeFailuresPropagateWithoutPublishingOrConsumingBudget() {
        val work = temporary.newFolder()
        val blocked = work.resolve("res").apply { writeText("not a directory") }
        val pool = JunkResourcePool(work, "failed_", JunkResourceBudget(total = 1, newResourceProbability = 1.0),
            reuseIdenticalContent = true)
        assertFails { pool.request("drawable", Random(1)) }
        assertEquals(0, pool.snapshot().generated.values.sum())
        assertTrue(pool.symbols().getValue("drawable").isEmpty())
        assertTrue(blocked.delete())
        assertNotNull(pool.request("drawable", Random(1)))
        assertEquals(1, pool.snapshot().generated.values.sum())
    }

    @Test fun identicalResourcesReuseThePublishedNameWithinTheirType() {
        val work = temporary.newFolder()
        val pool = JunkResourcePool(work, "content_", JunkResourceBudget(total = 3, newResourceProbability = 1.0),
            reuseIdenticalContent = true)
        val drawable = assertNotNull(pool.request("drawable", Random(42)))
        assertEquals(drawable, pool.request("drawable", Random(42)))
        val mipmap = assertNotNull(pool.request("mipmap", Random(42)))
        assertNotEquals(drawable, mipmap)
        assertEquals(resourceFile(work, "drawable", drawable).readText(), resourceFile(work, "mipmap", mipmap).readText())
        assertEquals(listOf(drawable), pool.symbols().getValue("drawable"))
        assertEquals(1, pool.snapshot().reused.getValue("drawable"))
        assertEquals(2, pool.snapshot().generated.values.sum())
    }

    @Test fun drawablePalettesLimitBackgroundsAndKeepImageReferencesCompatible() {
        val work = temporary.newFolder()
        val budget = JunkResourceBudget(total = 100, string = 0, newResourceProbability = 1.0)
        val policy = JunkGenerationPolicy(seed = 19, resources = budget)
        val pool = JunkResourcePool(work, "palette_", budget, useDrawablePalettes = true)
        val referencedTypes = mutableSetOf<Pair<String, String>>()
        val roots = mutableMapOf<String, String>()
        repeat(120) { index ->
            val layout = JunkLayoutComposer.compose("palette_$index", Random(policy.seedFor(index)), pool, emptyList(), policy)
            for (node in elements(parse(layout.xml))) {
                for ((attribute, expectedTag) in listOf("background" to "shape", "src" to "vector")) {
                    val resource = node.getAttributeNS(ANDROID, attribute)
                    if (!resource.startsWith("@drawable/") && !resource.startsWith("@mipmap/")) continue
                    val type = resource.substringAfter('@').substringBefore('/')
                    val tag = roots.getOrPut(resource) {
                        parse(resourceFile(work, type, resource.substringAfter('/')).readText()).tagName
                    }
                    assertEquals(expectedTag, tag, "$attribute: $resource")
                    referencedTypes += attribute to type
                }
            }
        }
        assertEquals(setOf("background" to "drawable", "src" to "drawable", "src" to "mipmap"), referencedTypes)
        val shapes = pool.symbols().getValue("drawable").map { name ->
            parse(resourceFile(work, "drawable", name).readText())
        }.filter { it.tagName == "shape" }
        assertTrue(shapes.size in 2..12)
        assertEquals(setOf(listOf("solid", "corners"), listOf("solid", "corners", "stroke")),
            shapes.map { element -> elements(element).drop(1).map { it.tagName } }.toSet())
        val stats = pool.snapshot()
        assertTrue(stats.reused.getValue("drawable") > stats.generated.getValue("drawable"))
    }

    @Test fun sharedVectorIconsReuseGeometryAndTintOnlyResolvedImages() {
        val tintColor = Regex("#[0-9A-F]{6}")
        val cap = 64
        for ((drawable, mipmap) in listOf(cap to cap, 0 to cap, 0 to 0)) {
            val work = temporary.newFolder()
            val budget = JunkResourceBudget(total = 100, drawable = drawable, mipmap = mipmap, string = 0,
                newResourceProbability = 1.0)
            val policy = JunkGenerationPolicy(seed = 20, resources = budget, profile = JunkGenerationProfile(0, 20))
            val pool = JunkResourcePool(work, "icons_", budget, useDrawablePalettes = true, shareVectorIcons = true)
            val images = (0 until 64).flatMap { index ->
                val result = JunkLayoutComposer.compose("icons_$index", Random(policy.seedFor(index)), pool, emptyList(), policy)
                elements(parse(result.xml)).filter { it.tagName == "ImageView" }
            }
            assertTrue(images.isNotEmpty())
            val tintedImages = images.mapNotNull { image ->
                val source = image.getAttributeNS(ANDROID, "src")
                val tint = image.getAttributeNS(ANDROID, "tint")
                if (source.isEmpty()) {
                    assertTrue(tint.isEmpty())
                    null
                } else {
                    assertTrue(tintColor.matches(tint))
                    source to tint
                }
            }.groupBy({ it.first }, { it.second })
            if (drawable == 0 && mipmap == 0) {
                assertTrue(tintedImages.isEmpty())
                assertEquals(0, pool.snapshot().generated.values.sum())
                continue
            }
            val type = if (drawable > 0) "drawable" else "mipmap"
            assertEquals(3, tintedImages.size)
            assertTrue(tintedImages.values.any { it.distinct().size > 1 })
            val paths = tintedImages.keys.map { source ->
                assertTrue(source.startsWith("@$type/"))
                val vector = parse(resourceFile(work, type, source.substringAfter('/')).readText())
                assertEquals("vector", vector.tagName)
                val path = elements(vector).last()
                assertEquals("path", path.tagName)
                path.getAttributeNS(ANDROID, "pathData")
            }
            assertEquals(3, paths.distinct().size)
            assertTrue(pool.symbols().getValue(if (type == "drawable") "mipmap" else "drawable").isEmpty())
        }
    }

    @Test fun sharedResourcePresetsReuseTwoBackgroundsAndThreeStandaloneAnimations() {
        val work = temporary.newFolder()
        val pool = JunkResourcePool(work, "preset_", JunkResourceBudget(newResourceProbability = 1.0),
            useDrawablePalettes = true, shareResourcePresets = true)
        val random = Random(21)
        fun backgrounds(use: JunkDrawableUse): Set<String> = List(64) {
            assertNotNull(pool.request("drawable", random, use))
        }.toSet()
        val surface = parse(resourceFile(work, "drawable", backgrounds(JunkDrawableUse.SURFACE).single()).readText())
        val control = parse(resourceFile(work, "drawable", backgrounds(JunkDrawableUse.CONTROL).single()).readText())
        assertEquals(listOf("shape", "solid", "corners"), elements(surface).map { it.tagName })
        assertEquals(listOf("shape", "solid", "corners", "stroke"), elements(control).map { it.tagName })

        val animations = List(64) { assertNotNull(pool.request("anim", random)) }.distinct().map {
            val root = parse(resourceFile(work, "anim", it).readText())
            assertEquals(listOf(root), elements(root))
            root.tagName to root.getAttributeNS(ANDROID, "duration")
        }
        assertEquals(3, animations.size)
        assertEquals(mapOf("alpha" to "180", "translate" to "220", "scale" to "160"), animations.toMap())
        val generated = pool.snapshot().generated
        assertEquals(2, generated.getValue("drawable"))
        assertEquals(3, generated.getValue("anim"))
    }

    @Test fun purposeSpecificLayoutsUseProfileContainersAndControlsWithinBudgets() {
        val budget = JunkResourceBudget(total = 0)
        val pool = JunkResourcePool(temporary.newFolder(), "purpose_", budget)
        val examples = listOf(
            Triple(14, "GridLayout", "ProgressBar"), Triple(62, "RelativeLayout", "TextView"),
            Triple(81, "RelativeLayout", "EditText"), Triple(88, "RelativeLayout", "SeekBar"),
            Triple(187, "ScrollView", "ImageView"), Triple(142, "ScrollView", "CheckBox"),
            Triple(241, "ScrollView", "Switch"), Triple(231, "ScrollView", "EditText"),
        )
        for ((id, rootType, requiredControl) in examples) {
            val policy = JunkGenerationPolicy(seed = 22, resources = budget, profile = JunkGenerationProfile(id, 22))
            val layouts = (0 until 48).map { index ->
                val layout = JunkLayoutComposer.compose("purpose_$index", Random(policy.seedFor(index)), pool, emptyList(), policy)
                val root = parse(layout.xml)
                val views = elements(root)
                assertEquals(rootType, root.tagName)
                assertTrue(views.any { it.tagName == requiredControl })
                assertEquals(layout.nodes, views.size)
                assertTrue(layout.nodes <= policy.maxLayoutNodes && layout.depth <= policy.maxLayoutDepth)
                if (rootType == "ScrollView") {
                    val body = (0 until root.childNodes.length).mapNotNull { root.childNodes.item(it) as? Element }.single()
                    assertEquals("LinearLayout", body.tagName)
                    assertEquals("vertical", body.getAttributeNS(ANDROID, "orientation"))
                }
                val editors = views.filter { it.tagName == "EditText" }
                if (id == 81 || id == 88) {
                    assertTrue(views.any { it.tagName == "Button" && it.getAttributeNS(ANDROID, "text") == "Apply" })
                    assertTrue(editors.all { it.getAttributeNS(ANDROID, "inputType").startsWith("number") })
                }
                if (id == 231) assertTrue(editors.all { it.getAttributeNS(ANDROID, "inputType").startsWith("text") })
                layout
            }
            assertEquals(layouts.size, layouts.map { it.fingerprint }.distinct().size)
        }
    }

    @Test fun arithmeticLayoutsSeparatePairedBoundsFromLabeledSliderSettings() {
        val budget = JunkResourceBudget(total = 0)
        val pool = JunkResourcePool(temporary.newFolder(), "adjustment_", budget)
        for (maxDepth in listOf(2, 5)) {
            val formPolicy = JunkGenerationPolicy(seed = 23, resources = budget, maxLayoutDepth = maxDepth,
                profile = JunkGenerationProfile(88, 23))
            val settingsPolicy = formPolicy.copy(profile = JunkGenerationProfile(228, 23))
            var modeGroups = 0
            repeat(40) { index ->
                val form = JunkLayoutComposer.compose("bounds_$index", Random(formPolicy.seedFor(index)), pool, emptyList(), formPolicy)
                val formViews = elements(parse(form.xml))
                val fields = formViews.filter { it.tagName == "EditText" }
                assertTrue(fields.size >= 2 && fields.size % 2 == 0)
                assertEquals(listOf("Lower bound", "Upper bound"), fields.take(2).map { it.getAttributeNS(ANDROID, "hint") })
                assertTrue(fields.all { it.getAttributeNS(ANDROID, "inputType").startsWith("number") })
                assertTrue(formViews.any { it.tagName == "Button" && it.getAttributeNS(ANDROID, "text") == "Apply" })
                assertTrue(formViews.none { it.tagName == "SeekBar" })
                assertTrue(form.depth <= maxDepth && form.nodes <= formPolicy.maxLayoutNodes)
                assertEquals(form.nodes, formViews.size)
                val groups = formViews.filter { it.tagName == "RadioGroup" }
                modeGroups += groups.size
                groups.forEach { group ->
                    assertTrue(elements(group).drop(1).map { it.tagName }.let { it.size in 2..3 && it.all { tag -> tag == "RadioButton" } })
                }

                val settings = JunkLayoutComposer.compose("setting_$index", Random(settingsPolicy.seedFor(index)), pool, emptyList(), settingsPolicy)
                val settingViews = elements(parse(settings.xml))
                val sliders = settingViews.filter { it.tagName == "SeekBar" }
                assertTrue(sliders.isNotEmpty())
                assertTrue(settingViews.any { it.tagName == "Switch" && it.getAttributeNS(ANDROID, "text") == "Enable adjustments" })
                for (slider in sliders) {
                    val label = slider.getAttributeNS(ANDROID, "contentDescription")
                    assertTrue(label.isNotEmpty())
                    assertTrue(settingViews.any {
                        it.tagName == "TextView" && it.parentNode == slider.parentNode && it.getAttributeNS(ANDROID, "text") == label
                    })
                }
                assertTrue(settings.depth <= maxDepth && settings.nodes <= settingsPolicy.maxLayoutNodes)
                assertEquals(settings.nodes, settingViews.size)
            }
            if (maxDepth == 2) assertEquals(0, modeGroups) else assertTrue(modeGroups > 0)
        }
    }

    @Test fun profiledListFitsShallowLayoutLimits() {
        val budget = JunkResourceBudget(total = 0)
        val policy = JunkGenerationPolicy(seed = 22, resources = budget, maxLayoutDepth = 2, maxLayoutNodes = 8,
            maxLayoutBytes = 4096, profile = JunkGenerationProfile(187, 22))
        val pool = JunkResourcePool(temporary.newFolder(), "shallow_", budget)
        repeat(48) { index ->
            val layout = JunkLayoutComposer.compose("shallow_$index", Random(policy.seedFor(index)), pool, emptyList(), policy)
            val root = parse(layout.xml)
            assertEquals("LinearLayout", root.tagName)
            assertTrue(elements(root).any { it.tagName == "ImageView" })
            assertTrue(layout.depth <= 2 && layout.nodes <= 8 && layout.xml.toByteArray().size <= 4096)
        }
    }

    @Test fun exhaustedDrawablePaletteOmitsAnImageWhenOnlyABackgroundExists() {
        val work = temporary.newFolder()
        val pool = JunkResourcePool(work, "palette_", JunkResourceBudget(total = 1, newResourceProbability = 1.0),
            useDrawablePalettes = true)
        val random = Random(19)
        val background = assertNotNull(pool.request("drawable", random, JunkDrawableUse.SURFACE))
        assertNull(pool.request("drawable", random, JunkDrawableUse.ICON))
        assertEquals(background, pool.request("drawable", random, JunkDrawableUse.SURFACE))
        assertEquals(listOf(background), pool.symbols().getValue("drawable"))
    }

    @Test fun composedTreesHaveBoundedDiverseStructureAndResolvableTypedReferences() {
        val work = temporary.newFolder()
        val policy = JunkGenerationPolicy(seed = 31, resources = JunkResourceBudget(total = 9, newResourceProbability = 0.4))
        val pool = JunkResourcePool(work, "tree_", policy.resources)
        val results = (0 until 120).map { index ->
            val result = JunkLayoutComposer.compose("tree_screen_$index", Random(policy.seedFor(index)), pool, listOf("com.fixture.views.PreviewView"), policy)
            val document = parse(result.xml)
            val all = elements(document)
            assertEquals(result.nodes, all.size)
            assertTrue(result.nodes <= policy.maxLayoutNodes)
            assertTrue(result.depth <= policy.maxLayoutDepth)
            assertTrue(result.xml.toByteArray().size <= policy.maxLayoutBytes)
            val ids = all.mapNotNull { it.getAttributeNS(ANDROID, "id").takeIf(String::isNotEmpty)?.substringAfter('/') }
            assertEquals(ids.size, ids.distinct().size)
            assertEquals(ids, result.ids)
            all.forEach { node ->
                if (node.tagName == "ScrollView") assertEquals(1, (0 until node.childNodes.length).count { node.childNodes.item(it) is Element })
                for (i in 0 until node.attributes.length) {
                    val attribute = node.attributes.item(i)
                    val value = attribute.nodeValue
                    when {
                        value.startsWith("@id/") -> {
                            assertTrue(value.substringAfter('/') in ids)
                            assertEquals("RelativeLayout", (node.parentNode as Element).tagName)
                            val siblings = elements(node.parentNode as Element).drop(1).map { it.getAttributeNS(ANDROID, "id").substringAfter('/') }
                            assertTrue(value.substringAfter('/') in siblings)
                        }
                        Regex("@(drawable|mipmap|string|anim)/.*").matches(value) -> {
                            val type = value.substringAfter('@').substringBefore('/')
                            assertTrue(value.substringAfter('/') in pool.symbols().getValue(type))
                            assertTrue(resourceFile(work, type, value.substringAfter('/')).isFile)
                        }
                    }
                    if (attribute.localName == "layout_weight") assertEquals("LinearLayout", (node.parentNode as Element).tagName)
                    if (attribute.localName == "layout_columnWeight") assertEquals("GridLayout", (node.parentNode as Element).tagName)
                }
            }
            result
        }
        assertEquals(setOf("simple", "medium", "complex"), results.map { it.complexity }.toSet())
        assertTrue(results.map { it.fingerprint }.distinct().size >= 115)
        assertTrue(results.map { it.nodes }.distinct().size >= 20)
        assertTrue(results.map { it.depth }.distinct().size >= 3)
        assertTrue(results.flatMap { it.types }.toSet().containsAll(listOf("LinearLayout", "RelativeLayout", "GridLayout", "FrameLayout", "ScrollView", "EditText", "com.fixture.views.PreviewView")))
        assertTrue(pool.snapshot().generated.values.sum() <= 9)
    }

    @Test fun sameSeedsReproduceBytesWhileLowByteLimitsStillProduceValidRelations() {
        val policy = JunkGenerationPolicy(seed = 100, maxLayoutBytes = 4096)
        val workOne = temporary.newFolder(); val workTwo = temporary.newFolder()
        val poolOne = JunkResourcePool(workOne, "same_", policy.resources)
        val poolTwo = JunkResourcePool(workTwo, "same_", policy.resources)
        repeat(40) { index ->
            val one = JunkLayoutComposer.compose("same_$index", Random(policy.seedFor(index)), poolOne, emptyList(), policy)
            val two = JunkLayoutComposer.compose("same_$index", Random(policy.seedFor(index)), poolTwo, emptyList(), policy)
            assertEquals(one, two)
            assertTrue(one.xml.toByteArray().size <= 4096)
            parse(one.xml)
            Regex("@id/([a-z0-9_]+)").findAll(one.xml).forEach { assertTrue(it.groupValues[1] in one.ids) }
        }
        val paths = workOne.walkTopDown().filter { it.isFile }.map { it.relativeTo(workOne).path }.toList()
        paths.forEach { assertContentEquals(workOne.resolve(it).readBytes(), workTwo.resolve(it).readBytes()) }
    }

    private fun parse(xml: String): Element = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(xml.byteInputStream()).documentElement

    private fun elements(root: Element): List<Element> = buildList {
        add(root)
        for (i in 0 until root.childNodes.length) (root.childNodes.item(i) as? Element)?.let { addAll(elements(it)) }
    }

    private fun resourceFile(work: File, type: String, name: String): File = when (type) {
        "string" -> work.resolve("res/values/$name.xml")
        "assets" -> work.resolve("assets/$name.json")
        else -> work.resolve("res/$type/$name.xml")
    }

    companion object { private const val ANDROID = "http://schemas.android.com/apk/res/android" }
}
