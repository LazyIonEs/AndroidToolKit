package org.tool.kit.tests.data

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicVerifier
import org.tool.kit.data.generator.*
import java.io.File
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

class JunkArchiveSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun generate(out: File, policy: JunkGenerationPolicy, count: Int = 12): File = AndroidJunkGenerator(
        temporary.root.path, out.path, "com.host.sameprefix", count, 1, "unit_", policy).startGenerate()

    @Test fun deterministicAcrossParallelismAndEveryActivityHasItsOwnLayoutAndPreciseKeep() {
        val policy = JunkGenerationPolicy(seed = 17, resources = JunkResourceBudget(total = 3, newResourceProbability = 1.0))
        val first = generate(temporary.newFolder("a"), policy.copy(maxParallelism = 1))
        val second = generate(temporary.newFolder("b"), policy.copy(maxParallelism = 4))
        assertContentEquals(first.readBytes(), second.readBytes())
        for (file in listOf(first, second)) assertEquals(listOf(file.name), file.parentFile.list()!!.toList())
        ZipFile(first).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(12, entries.count { it.startsWith("res/layout/") })
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).reader().readText()
            assertEquals(12, Regex("<activity ").findAll(manifest).count())
            assertTrue(manifest.contains("com.host.sameprefix.toolkitres"))
            val rules = zip.getInputStream(zip.getEntry("proguard.txt")).reader().readText()
            assertFalse(rules.contains(".**"))
            assertFalse(entries.any { it.endsWith("/R.class") || it.contains("/R$") })
            assertNull(zip.getEntry("consumer-rules.pro"))
            val optional = entries.count { it.startsWith("res/") && !it.startsWith("res/layout/") && !it.startsWith("res/raw/") } + entries.count { it.startsWith("assets/") }
            assertTrue(optional <= 3)
            JarInputStream(zip.getInputStream(zip.getEntry("classes.jar"))).use { jar ->
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    val node = ClassNode(); ClassReader(jar.readBytes()).accept(node, 0)
                    assertEquals(node.name + ".class", entry.name)
                    assertEquals(0, node.access and Opcodes.ACC_MODULE)
                    if (node.access and Opcodes.ACC_INTERFACE == 0) assertTrue(node.methods.any { it.name == "<init>" })
                    for (method in node.methods) {
                        if (method.access and Opcodes.ACC_ABSTRACT == 0) Analyzer(BasicVerifier()).analyze(node.name, method)
                    }
                    assertTrue(rules.contains("-keep class ${node.name.replace('/', '.')} { *; }"))
                }
            }
        }
    }

    @Test fun zeroBudgetStillGeneratesRootAndChildLayoutsAndFailurePreservesPreviousOutput() {
        val out = temporary.newFolder("output")
        val policy = JunkGenerationPolicy(seed = 3, resources = JunkResourceBudget(total = 0), enableFragments = false)
        val first = generate(out, policy)
        val original = first.readBytes()
        ZipFile(first).use { zip ->
            assertEquals(12, zip.entries().asSequence().count { it.name.startsWith("res/layout/") })
            assertTrue(zip.entries().asSequence().none { it.name.startsWith("res/drawable/") || it.name.startsWith("res/mipmap/") || it.name.startsWith("assets/") || it.name.startsWith("res/values/") })
        }
        var checks = 0
        assertFailsWith<IllegalStateException> {
            AndroidJunkGenerator(temporary.root.path, out.path, "com.host.sameprefix", 12, 1, "unit_", policy,
                { if (++checks >= 5) error("cancel simulation") }).startGenerate()
        }
        assertContentEquals(original, first.readBytes())
        assertTrue(out.listFiles()!!.none { it.name.endsWith(".part") })
        assertTrue(temporary.root.listFiles()!!.none { it.isDirectory && it.name.startsWith("junk-") })
        val root = AndroidJunkGenerator(temporary.root.path, out.path, "com.root.only", 0, 8, "root_", policy).startGenerate()
        ZipFile(root).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml")).reader().readText()
            assertEquals(Regex("<activity ").findAll(manifest).count(), zip.entries().asSequence().count { it.name.startsWith("res/layout/") })
        }
    }

    @Test fun existingReportDirectoryDoesNotBlockPublicationOrGetDeleted() {
        val out = temporary.newFolder("publication")
        val previous = File(out, "junk_com_host_sameprefix_TT3.0.0.aar").apply { writeText("previous complete archive") }
        val existingReport = File(out, "${previous.name}.report.json").apply { mkdirs(); resolve("sentinel").writeText("keep") }
        val existingBatchReport = File(out, "batch-report.json").apply { writeText("user-owned historical report") }
        val generated = generate(out, JunkGenerationPolicy(seed = 71), count = 2)
        assertEquals(previous, generated)
        ZipFile(generated).use { zip -> assertEquals(2, zip.entries().asSequence().count { it.name.startsWith("res/layout/") }) }
        assertEquals("keep", existingReport.resolve("sentinel").readText())
        assertEquals("user-owned historical report", existingBatchReport.readText())
        assertEquals(setOf(generated.name, existingReport.name, existingBatchReport.name), out.list()!!.toSet())
    }

    @Test fun archiveTargetDirectoryBlocksPublicationWithoutRemovingExistingFiles() {
        val out = temporary.newFolder("blocked-publication")
        val target = File(out, "junk_com_host_sameprefix_TT3.0.0.aar").apply {
            mkdirs(); resolve("sentinel").writeText("keep existing directory")
        }
        val previous = File(out, "previous.aar").apply { writeText("previous complete archive") }
        assertFailsWith<IllegalArgumentException> { generate(out, JunkGenerationPolicy(seed = 71), count = 2) }
        assertEquals("keep existing directory", target.resolve("sentinel").readText())
        assertEquals("previous complete archive", previous.readText())
        assertEquals(setOf(target.name, previous.name), out.list()!!.toSet())
        assertTrue(out.listFiles()!!.none { it.name.endsWith(".part") })
        assertTrue(temporary.root.listFiles()!!.none { it.isDirectory && it.name.startsWith("junk-") })
    }

    @Test fun emptyPrefixAndBatchDefaultRandomUseTheRecordedSeed() = runBlocking {
        val out = temporary.newFolder("empty")
        val policy = JunkGenerationPolicy(seed = 0, resourceNamespace = "com.custom.resources")
        val file = AndroidJunkGenerator(temporary.root.path, out.path, "com.empty.prefix", 2, 1, "", policy).startGenerate()
        assertTrue(file.isFile)
        val a = MultiAarGenerator.generate(temporary.root.path, out.path, "a", 2, 1, 2, 1, 1, policy = policy)
        val b = MultiAarGenerator.generate(temporary.root.path, out.path, "b", 2, 1, 2, 1, 1, policy = policy)
        a.zip(b).forEach { (first, second) -> assertContentEquals(first.readBytes(), second.readBytes()) }
        val namespaces = a.map { aar -> ZipFile(aar).use { z ->
            Regex("package=\"([^\"]+)\"").find(z.getInputStream(z.getEntry("AndroidManifest.xml")).reader().readText())!!.groupValues[1]
        } }
        assertEquals(2, namespaces.distinct().size)
    }

    @Test fun batchResourceBudgetAndNamespacesAreIsolatedAndBatchFailureIsTransactional() = runBlocking {
        val out = temporary.newFolder("out")
        val policy = JunkGenerationPolicy(seed = 71, batchResourceTotal = 5, resources = JunkResourceBudget(newResourceProbability = 1.0))
        val files = MultiAarGenerator.generate(temporary.root.path, out.path, "batch", 3, 4, 4, 1, 1, Random(71), policy)
        val allNames = mutableSetOf<String>(); var extras = 0
        files.forEach { file -> ZipFile(file).use { zip ->
            zip.entries().asSequence().filter { it.name.startsWith("res/") }.forEach { assertTrue(allNames.add(it.name)) }
            extras += zip.entries().asSequence().count { it.name.startsWith("res/") && !it.name.startsWith("res/layout/") && !it.name.startsWith("res/raw/") || it.name.startsWith("assets/") }
        } }
        assertTrue(extras <= 5)
        assertEquals(files.map { it.name }.toSet(), File(out, "batch").list()!!.toSet())
        val original = files.associate { it.name to it.readBytes() }
        var failureReported = false
        assertFailsWith<IllegalStateException> {
            MultiAarGenerator.generate(temporary.root.path, out.path, "batch", 3, 1, 1, 1, 1, Random(71), policy,
                onReport = { failureReported = true }) { _, _ -> error("expected failure") }
        }
        assertFalse(failureReported, "A failed batch must not publish a summary")
        original.forEach { (name, bytes) -> assertContentEquals(bytes, File(out, "batch/$name").readBytes()) }
        assertTrue(out.listFiles()!!.none { it.name.startsWith(".junk-") })
    }
}
