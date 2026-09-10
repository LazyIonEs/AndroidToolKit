package org.tool.kit.migration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmIconOutputs
import org.tool.kit.domain.icon.*
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IconOutputsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun request() = GenerateIconsRequest("input.png", temporary.root.path, "res", "mipmap", "icon",
        IconProcessingOptions(3, 5, true, 70, 100, 1, 6, 85f))

    @Test fun separateServicesSerializeAndKeepOutputsAndUnrelatedFiles() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = JvmIconOutputs(dispatcher)
        val second = JvmIconOutputs(dispatcher)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var secondEntered = false
        val foreign = temporary.root.resolve("res/keep_resize.png").apply { parentFile.mkdirs(); writeText("unrelated") }
        val prior = temporary.root.resolve("res/mipmap-xxxhdpi/icon.png").apply { parentFile.mkdirs(); writeText("prior output") }
        lateinit var temp: File
        lateinit var output: File
        val a = launch { first.use(request()) { session -> session.density("mdpi", ".png") { files ->
            temp = File(files.temporaryPath).apply { writeText("native intermediate") }
            output = File(files.outputPath).apply { writeText("first output") }
            entered.complete(Unit); release.await()
            assertTrue(temp.exists())
        } } }
        entered.await()
        val b = launch { second.use(request()) { session ->
            secondEntered = true
            assertFalse(temp.exists()); assertEquals("first output", output.readText())
            session.density("mdpi", ".png") { files ->
                assertFalse(File(files.outputPath).exists(), "Original overwrite-before-processing behavior")
                File(files.outputPath).writeText("second output")
            }
        } }
        runCurrent(); assertFalse(secondEntered)
        release.complete(Unit); joinAll(a, b)
        assertEquals("second output", output.readText())
        assertEquals("unrelated", foreign.readText()); assertEquals("prior output", prior.readText())
        assertFalse(temp.exists())
    }

    @Test fun nativeFailureAndCancellationCleanOnlyCurrentTemporaryAfterNativeReturns() = runTest {
        val service = JvmIconOutputs(StandardTestDispatcher(testScheduler))
        lateinit var temp: File
        assertFailsWith<IllegalStateException> { service.use(request()) { session -> session.density("hdpi", ".png") { files ->
            temp = File(files.temporaryPath).apply { writeText("failed native intermediate") }
            error("native failure")
        } } }
        assertFalse(temp.exists())
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val running = launch { service.use(request()) { session -> session.density("mdpi", ".png") { files ->
            temp = File(files.temporaryPath).apply { writeText("native intermediate") }
            withContext(NonCancellable) { entered.complete(Unit); release.await(); assertTrue(temp.exists()) }
        } } }
        entered.await()
        val queued = launch { service.use(request()) { fail("Cancelled queued work cannot enter") } }
        runCurrent(); queued.cancelAndJoin()
        running.cancel(); runCurrent()
        assertTrue(temp.exists(), "Do not remove a path still used by native code")
        release.complete(Unit); running.join()
        assertFalse(temp.exists())
        assertTrue(temp.parentFile.isDirectory)
    }
}
