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
import org.tool.kit.data.generator.JunkGenerationPolicy
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
        val pool = JunkResourcePool(work, "failed_", JunkResourceBudget(total = 1, newResourceProbability = 1.0))
        assertFails { pool.request("drawable", Random(1)) }
        assertEquals(0, pool.snapshot().generated.values.sum())
        assertTrue(pool.symbols().getValue("drawable").isEmpty())
        assertTrue(blocked.delete())
        assertNotNull(pool.request("drawable", Random(1)))
        assertEquals(1, pool.snapshot().generated.values.sum())
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
