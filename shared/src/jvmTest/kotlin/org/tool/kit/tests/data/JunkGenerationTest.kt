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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import org.tool.kit.data.generator.JunkGenerationPolicy
import org.tool.kit.data.generator.JunkGenerationProfile
import org.tool.kit.data.generator.MultiAarGenerator
import org.tool.kit.data.generator.parallelJunkWork
import org.tool.kit.data.source.JvmJunkCodeDataSource
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.usecase.GenerateJunkCodeUseCase
import kotlin.time.Duration.Companion.milliseconds

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
        assertEquals("junk_com_fixture_junk_part_TT3.1.0.aar", File(single.outputPath).name)
        assertArchive(File(single.outputPath), "com.fixture.junk.part", "fixture_")
        assertEquals(File(single.outputPath).length(), single.totalBytes)
        val sibling = output.resolve("unrelated.txt").apply { writeText("keep") }
        val stale = output.resolve(" batch 中文 /stale.txt").apply { parentFile.mkdirs(); writeText("stale") }
        val batch = source.generate(GenerateJunkCodeRequest(output.path, JunkConfiguration.Multi(" batch 中文 ", 2, 1, 1, 1, 1)))
        assertEquals(File(output, " batch 中文 ").path, batch.outputPath)
        assertEquals(2, batch.archivePaths.size); assertEquals(2, batch.archivePaths.distinct().size)
        batch.archivePaths.forEach { assertArchive(File(it), null, null) }
        assertEquals(batch.archivePaths.sumOf { File(it).length() }, batch.totalBytes)
        assertFalse(stale.exists()); assertEquals("keep", sibling.readText()); assertEquals("keep", sentinel.resolve("keep").readText())
        assertEquals(listOf(sentinel.name), work.list()!!.toList())
        // Zero activities cause generation to fail; cleanup must stay within its workspace.
        val failure = GenerateJunkCodeUseCase(source)(GenerateJunkCodeRequest(output.path, JunkConfiguration.Single("com.fixture.fail", 1, 0, "fixture_")))
        assertIs<GenerateJunkCodeOutcome.Failure>(failure)
        assertEquals(listOf(sentinel.name), work.list()!!.toList()); assertTrue(File(single.outputPath).isFile)
    }

    @Test fun explicitRequestSeedReproducesTheWholeDataSourceBatch() = runBlocking {
        val work = temporary.newFolder("seed-work")
        val firstOutput = temporary.newFolder("seed-a")
        val secondOutput = temporary.newFolder("seed-b")
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO, Random(17))
        val config = JunkConfiguration.Multi("batch", 2, 1, 2, 1, 2)
        val a = source.generate(GenerateJunkCodeRequest(firstOutput.path, config, seed = 731))
        val b = source.generate(GenerateJunkCodeRequest(secondOutput.path, config, seed = 731))
        assertEquals(a.archivePaths.map { File(it).name }, b.archivePaths.map { File(it).name })
        a.archivePaths.zip(b.archivePaths).forEach { (first, second) -> assertContentEquals(File(first).readBytes(), File(second).readBytes()) }
        assertTrue(work.listFiles()!!.isEmpty())
    }

    @Test fun sizeRangeAppliesToEachRealArchiveInSingleAndBatchGeneration() = runBlocking {
        val work = temporary.newFolder("sized-work")
        val output = temporary.newFolder("sized-output")
        val size = JunkAarSizeRange(17_500_000, 22_500_000)
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO)
        val single = source.generate(GenerateJunkCodeRequest(output.path,
            JunkConfiguration.SingleBySize("com.fixture.sized", size, "fixture_"), seed = 731))
        val batch = source.generate(GenerateJunkCodeRequest(output.path, JunkConfiguration.MultiBySize("batch", 2, size), seed = 731))
        assertEquals(1, single.archivePaths.size)
        assertEquals(2, batch.archivePaths.size)
        for (result in listOf(single, batch)) {
            val files = result.archivePaths.map(::File)
            files.forEach { file ->
                assertTrue(file.length() in size, "${file.name} has ${file.length()} bytes")
                assertArchive(file, null, null)
            }
            assertEquals(files.sumOf { it.length() }, result.totalBytes)
        }
        assertTrue(work.listFiles()!!.isEmpty())
    }

    @Test fun uiStrategyReachesSizedArchivesAndAssignsDifferentProfilesInABatch() = runBlocking {
        val work = temporary.newFolder("profile-work")
        val output = temporary.newFolder("profile-output")
        val policy = JunkGenerationPolicy(seed = 24, enableFragments = false, minAssociatedClasses = 2,
            maxAssociatedClasses = 2, associatedRoleWeights = mapOf("converter" to 1),
            includeDtoStringAdapters = true, includeDtoComparison = true)
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO, policy = policy)
        val seed = (0L..255L).first {
            Random(it).nextInt(JunkGenerationProfile.CAPACITY) < JunkGenerationProfile.CAPACITY / 4
        }
        val size = JunkAarSizeRange(120_000, 260_000)
        val strategy = JunkGenerationStrategy()
        val one = GenerateJunkCodeRequest(output.path,
            JunkConfiguration.SingleBySize("com.fixture.profile", size, "profile_"), seed, strategy)
        assertProfiledArchive(File(source.generate(one).archivePaths.single()), size, strategy)

        val request = one.copy(configuration = JunkConfiguration.MultiBySize("batch", 3, size))
        val batch = source.generate(request)
        val repeated = source.generate(request.copy(outputPath = temporary.newFolder("profile-replay").path))
        assertEquals(3, batch.archivePaths.size)
        val profiles = batch.archivePaths.zip(repeated.archivePaths).map { (first, second) ->
            assertContentEquals(File(first).readBytes(), File(second).readBytes())
            assertProfiledArchive(File(first), size, strategy)
        }
        assertEquals(3, profiles.distinct().size)
        assertTrue(work.listFiles()!!.isEmpty())
    }

    @Test fun sizeSearchUsesMeasuredFeedbackAndKeepsThePreviousArchiveWhenNoSizeFits() = runBlocking {
        val work = temporary.newFolder("retry-work")
        val output = temporary.newFolder("retry-output")
        val attempts = mutableListOf<Int>()
        val source = JvmJunkCodeDataSource(work, Dispatchers.IO, generateArchive = { _, destination, config ->
            assertEquals(1, config.activityCount)
            attempts += config.packageCount
            File(destination, "${config.appPackageName}.aar").apply { writeBytes(ByteArray(config.packageCount * 5_000)) }
        })
        val config = JunkConfiguration.SingleBySize("com.fixture.retry", JunkAarSizeRange(99_999, 100_001), "f_")
        val generated = source.generate(GenerateJunkCodeRequest(output.path, config, seed = 13))
        assertTrue(attempts.size > 1)
        assertEquals(20, attempts.last())
        assertEquals(100_000L, generated.totalBytes)
        val failed = GenerateJunkCodeUseCase(source)(GenerateJunkCodeRequest(output.path,
            config.copy(size = JunkAarSizeRange(1, 2)), seed = 13))
        assertIs<GenerateJunkCodeOutcome.Failure>(failed)
        assertEquals(100_000L, File(generated.outputPath).length())
        assertEquals(listOf(File(generated.outputPath).name), output.list()!!.toList())
        assertTrue(work.listFiles()!!.isEmpty())
    }

    @Test fun separateServicesSerializeOutputsAndCancellationWaitsForSynchronousGenerator() = runBlocking {
        val work = temporary.newFolder("work"); val output = temporary.newFolder("output")
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val starts = AtomicInteger()
        var activeWork: File? = null
        val generate: (String, String, JunkConfiguration.Single) -> File = { path, out, config ->
            starts.incrementAndGet(); activeWork = File(path).resolve("still-writing").apply { writeText("active") }
            entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
            assertTrue(activeWork.exists())
            File(out, "${config.appPackageName}.aar").apply { writeText("completed output") }
        }
        val one = JvmJunkCodeDataSource(work, Dispatchers.IO, generateArchive = generate)
        val two = JvmJunkCodeDataSource(work, Dispatchers.IO, generateArchive = generate)
        val request = GenerateJunkCodeRequest(output.path, JunkConfiguration.Single("com.fixture", 1, 1, "f_"))
        val first = launch(Dispatchers.Default) { one.generate(request) }
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS)); first.cancel()
            val second = launch(start = CoroutineStart.UNDISPATCHED) { two.generate(request) }
            delay(100.milliseconds); assertEquals(1, starts.get()); second.cancelAndJoin()
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
            assertTrue(entered.await(10, TimeUnit.SECONDS)); delay(100.milliseconds)
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

    private fun assertProfiledArchive(file: File, size: JunkAarSizeRange, strategy: JunkGenerationStrategy): Int {
        assertTrue(file.name.endsWith("_TT3.1.0.aar"), file.name)
        assertTrue(file.length() in size)
        return ZipFile(file).use { zip ->
            val metadata = zip.getInputStream(zip.getEntry("toolkit-generation.json")).bufferedReader().use { it.readText() }
            val profile = Json.parseToJsonElement(metadata).jsonObject.getValue("profile").jsonObject
            assertEquals(strategy.generatorVersion, profile.getValue("generatorVersion").jsonPrimitive.int)
            JarInputStream(zip.getInputStream(zip.getEntry("classes.jar"))).use { jar ->
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    val simpleName = entry.name.substringAfterLast('/')
                    if (!entry.name.endsWith(".class") || !(simpleName.startsWith("Converter") || simpleName.startsWith("Dto"))) continue
                    ClassReader(jar.readBytes()).accept(object : ClassVisitor(ASM9) {
                        override fun visitMethod(access: Int, name: String, descriptor: String,
                            signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                            assertFalse(name == "convert" || name == "compareValue", "${entry.name} contains $name")
                            return null
                        }
                    }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                }
            }
            profile.getValue("id").jsonPrimitive.int
        }
    }

    private fun assertArchive(file: File, expectedPackage: String?, prefix: String?) {
        ZipFile(file).use { zip ->
            val manifest = zip.getInputStream(assertNotNull(zip.getEntry("AndroidManifest.xml"))).reader().readText()
            expectedPackage?.let { assertTrue(manifest.contains("package=\"$it.toolkitres"), manifest) }
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
