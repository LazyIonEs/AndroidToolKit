package org.tool.kit.tests.data

import java.io.File
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import kotlin.random.Random
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.tool.kit.data.generator.MultiAarGenerator
import org.tool.kit.data.generator.parallelJunkWork
import org.tool.kit.data.source.JvmJunkCodeDataSource
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.usecase.GenerateJunkCodeUseCase
import org.tool.kit.tests.support.release

class JunkGenerationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun useCaseMapsTheWholeRequestAndNullableErrorsButPropagatesCancellation() = runTest {
        val request = GenerateJunkCodeRequest(" output ", JunkConfiguration.Single("a.b.c", 1, 2, "prefix_"))
        val result = GeneratedJunkCode(" output /actual.aar", listOf(" output /actual.aar"), 123)
        assertEquals(GenerateJunkCodeOutcome.Success(result), GenerateJunkCodeUseCase { assertSame(request, it); result }(request))
        for (message in listOf(null, "", "failed")) {
            assertEquals(GenerateJunkCodeOutcome.Failure(message), GenerateJunkCodeUseCase { throw Exception(message) }(request))
        }
        assertFailsWith<CancellationException> { GenerateJunkCodeUseCase { throw CancellationException("cancelled") }(request) }
    }

    @Test fun randomBatchUsesInclusiveBoundsAndRetriesDuplicatePackagesAndPrefixes() {
        val values = mutableListOf<Int>()
        fun word(s: String) { values += s.length; values += s.map { it - 'a' } }
        word("abc"); word("defg"); word("aaa"); values += listOf(1, 2)
        word("abc"); word("defg") // duplicate package retry
        word("abcd"); word("efg"); word("aaa"); word("bbbb"); values += listOf(3, 4)
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("Unexpected random primitive")
            override fun nextInt(until: Int): Int = nextInt(0, until)
            override fun nextInt(from: Int, until: Int): Int = values.removeAt(0).also { assertTrue(it in from until until) }
        }
        val configs = MultiAarGenerator.configurations(2, 1, 3, 2, 4, random)
        assertTrue(values.isEmpty())
        assertEquals(listOf("com.abc.defg", "com.abcd.efg"), configs.map { it.packageName })
        assertEquals(1, configs.map { it.packageName.replace(".", "") }.distinct().size, "Removing package dots can collapse distinct valid names")
        assertEquals(listOf("aaa_", "bbbb_"), configs.map { it.resPrefix })
        assertEquals(listOf(1, 3), configs.map { it.packageCount })
        assertEquals(listOf(2, 4), configs.map { it.activityCount })
        val reversed = MultiAarGenerator.configurations(2, 8, 2, 9, 1, Random(3))
        assertTrue(reversed.all { it.packageCount == 8 && it.activityCount == 9 })
    }

    @Test fun realSingleAndBatchArchivesPreserveNamingStructureAndSizeRules() = runBlocking {
        val work = temporary.newFolder("work"); val output = temporary.newFolder("中文 output")
        val sentinel = work.resolve("comfixturejunk").apply { mkdirs(); resolve("keep").writeText("keep") }
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO, Random(7))
        val single = source.generate(GenerateJunkCodeRequest(output.path, JunkConfiguration.Single("com.fixture.junk.part", 1, 1, "fixture_")))
        assertEquals("junk_com_fixture_junk_part_TT2.2.0.aar", File(single.outputPath).name)
        assertArchive(File(single.outputPath), "com.fixture.junk.part", "fixture_")
        assertEquals(File(single.outputPath).length(), single.totalBytes)
        val sibling = output.resolve("unrelated.txt").apply { writeText("keep") }
        val stale = output.resolve(" batch 中文 /stale.txt").apply { parentFile.mkdirs(); writeText("stale") }
        val batch = source.generate(GenerateJunkCodeRequest(output.path, JunkConfiguration.Multi(" batch 中文 ", 2, 1, 1, 1, 1)))
        assertEquals(File(output, " batch 中文 ").path, batch.outputPath)
        assertEquals(2, batch.archivePaths.size); assertEquals(2, batch.archivePaths.distinct().size)
        batch.archivePaths.forEach { assertArchive(File(it), null, null) }
        assertEquals(File(batch.outputPath).walkBottomUp().filter { it.isFile }.sumOf { it.length() }, batch.totalBytes)
        assertFalse(stale.exists()); assertEquals("keep", sibling.readText()); assertEquals("keep", sentinel.resolve("keep").readText())
        assertEquals(listOf(sentinel.name), work.list()!!.toList())
        // Zero activities cause generation to fail; cleanup must stay within its workspace.
        val failure = GenerateJunkCodeUseCase(source)(GenerateJunkCodeRequest(output.path, JunkConfiguration.Single("com.fixture.fail", 1, 0, "fixture_")))
        assertIs<GenerateJunkCodeOutcome.Failure>(failure)
        assertEquals(listOf(sentinel.name), work.list()!!.toList()); assertTrue(File(single.outputPath).isFile)
    }

    @Test fun separateServicesSerializeOutputsAndCancellationWaitsForSynchronousGenerator() = runBlocking {
        val work = temporary.newFolder("work"); val output = temporary.newFolder("output")
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val starts = AtomicInteger()
        var activeWork: File? = null
        val generate: (String, String, JunkConfiguration.Single) -> File = { path, out, config ->
            starts.incrementAndGet(); activeWork = File(path).resolve("still-writing").apply { writeText("active") }
            entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
            assertTrue(activeWork!!.exists())
            File(out, "${config.appPackageName}.aar").apply { writeText("completed output") }
        }
        val one = JvmJunkCodeDataSource(work, Dispatchers.IO, generateArchive = generate)
        val two = JvmJunkCodeDataSource(work, Dispatchers.IO, generateArchive = generate)
        val request = GenerateJunkCodeRequest(output.path, JunkConfiguration.Single("com.fixture", 1, 1, "f_"))
        val first = launch(Dispatchers.Default) { one.generate(request) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS)); first.cancel()
            val second = launch(start = CoroutineStart.UNDISPATCHED) { two.generate(request) }
            delay(100); assertEquals(1, starts.get()); second.cancelAndJoin()
            assertTrue(activeWork!!.isFile); assertFalse(first.isCompleted)
        } finally { release.countDown(); first.join() }
        assertTrue(work.listFiles()!!.isEmpty()); assertEquals("completed output", output.resolve("com.fixture.aar").readText())
    }

    @Test fun batchWorkersUseIndependentDirectoriesAndAllFinishBeforeFailureCleanup() = runBlocking {
        val work = temporary.newFolder("work"); val output = temporary.newFolder("output")
        val entered = CountDownLatch(2); val release = CountDownLatch(1)
        val roots = ConcurrentHashMap.newKeySet<String>(); val calls = AtomicInteger()
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO, Random(1)) { path, _, _ ->
            roots.add(path); File(path, "active").writeText("active")
            val index = calls.incrementAndGet(); entered.countDown(); check(entered.await(10, TimeUnit.SECONDS))
            if (index == 1) error("batch failure")
            check(release.await(10, TimeUnit.SECONDS)); assertTrue(File(path, "active").exists())
            File(output, "partial.aar").apply { writeText("partial output") }
        }
        val task = async(Dispatchers.Default) { GenerateJunkCodeUseCase(source)(GenerateJunkCodeRequest(output.path, JunkConfiguration.Multi("batch", 2, 1, 1, 1, 1))) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS)); delay(100)
            assertFalse(task.isCompleted); assertEquals(2, roots.size); assertTrue(roots.all { File(it, "active").isFile })
        } finally { release.countDown() }
        assertEquals(GenerateJunkCodeOutcome.Failure("batch failure"), task.await())
        assertTrue(work.listFiles()!!.isEmpty()); assertEquals("partial output", output.resolve("partial.aar").readText())
    }

    @Test fun parallelFailureIsReportedOnlyAfterOtherWorkersExit() {
        val entered = CountDownLatch(2); val release = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val task = executor.submit { assertFailsWith<IllegalStateException> {
                parallelJunkWork(2) { index ->
                    entered.countDown(); check(entered.await(10, TimeUnit.SECONDS))
                    if (index == 0) error("first failure")
                    check(release.await(10, TimeUnit.SECONDS))
                }
            } }
            assertTrue(entered.await(10, TimeUnit.SECONDS)); assertFalse(task.isDone)
            release.countDown(); task.get(10, TimeUnit.SECONDS)
        } finally { release.countDown(); executor.shutdownNow() }
    }

    private fun assertArchive(file: File, expectedPackage: String?, prefix: String?) {
        ZipFile(file).use { zip ->
            val manifest = zip.getInputStream(assertNotNull(zip.getEntry("AndroidManifest.xml"))).reader().readText()
            expectedPackage?.let { assertTrue(manifest.contains("package=\"$it\""), manifest) }
            assertNotNull(zip.getEntry("R.txt")); assertTrue(zip.entries().asSequence().any { it.name.startsWith("res/layout/") })
            if (prefix != null) assertTrue(zip.entries().asSequence().filter { it.name.startsWith("res/layout/") && !it.isDirectory }.all { it.name.substringAfterLast('/').startsWith(prefix) })
            var count = 0
            JarInputStream(zip.getInputStream(assertNotNull(zip.getEntry("classes.jar")))).use { jar ->
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    if (!entry.name.endsWith(".class")) continue
                    val reader = ClassReader(jar.readBytes()); assertEquals(entry.name.removeSuffix(".class"), reader.className)
                    expectedPackage?.let { assertTrue(reader.className.startsWith(it.replace('.', '/') + "/")) }; count++
                }
            }
            assertTrue(count > 0)
        }
    }
}
