package org.tool.kit.migration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmApkBuildWorkspaces
import org.tool.kit.domain.apk.BuildApkRequest
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ApkBuildWorkspacesTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun request() = BuildApkRequest(temporary.root.path, "", "org.fixture", "30", "21", "1", "1.0", "same")

    @Test fun separateServicesSerializeTheSameOutputAndCleanOnlyOwnedDirectories() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val root = temporary.newFolder("cache")
        val foreign = File(root, "not-owned").apply { mkdir(); resolve("keep").writeText("keep") }
        val output = temporary.root.resolve("same.apk").apply { writeText("existing output") }
        val first = JvmApkBuildWorkspaces(root, dispatcher)
        val second = JvmApkBuildWorkspaces(root, dispatcher)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val directories = mutableListOf<String>()
        val a = launch { first.use(request()) { workspace ->
            directories += workspace.directory
            assertEquals(output.path, workspace.outputPath)
            File(workspace.directory).mkdirs()
            File(workspace.directory, "partial").writeText("partial")
            entered.complete(Unit); release.await()
            assertTrue(File(workspace.directory).isDirectory)
        } }
        entered.await()
        val b = launch { second.use(request()) { directories += it.directory } }
        runCurrent()
        assertEquals(1, directories.size)
        assertEquals(2, root.listFiles()!!.size)
        release.complete(Unit); joinAll(a, b)
        assertEquals(2, directories.distinct().size)
        assertTrue(directories.none { File(it).exists() })
        assertEquals(listOf(foreign), root.listFiles()!!.toList())
        assertEquals("keep", foreign.resolve("keep").readText())
        assertEquals("existing output", output.readText())
    }

    @Test fun failureCancellationAndCancelledQueueLeaveNoOwnedCache() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val root = temporary.newFolder("cache")
        val service = JvmApkBuildWorkspaces(root, dispatcher)
        val failed = async { assertFailsWith<IllegalStateException> { service.use(request()) {
            File(it.directory).mkdirs(); error("failure")
        } } }
        failed.await()
        assertTrue(root.listFiles()!!.isEmpty())
        val entered = CompletableDeferred<Unit>()
        val running = launch { service.use(request()) { File(it.directory).mkdirs(); entered.complete(Unit); awaitCancellation() } }
        entered.await()
        val queued = launch { service.use(request()) { fail("Cancelled queued operation must not run") } }
        runCurrent(); queued.cancelAndJoin()
        assertEquals(1, root.listFiles()!!.size)
        running.cancelAndJoin()
        assertTrue(root.listFiles()!!.isEmpty())
    }
}
