package org.tool.kit.tests.data

import java.io.File
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.core.process.*
import org.tool.kit.data.process.JvmProcessRunner

class JvmProcessRunnerTest {
    @get:Rule val temporary = TemporaryFolder()
    private val runner = JvmProcessRunner(Dispatchers.IO)
    private fun request(vararg args: String, timeout: Long = 60_000, limit: Int = 8 * 1024 * 1024) = ProcessRequest(
        File(System.getProperty("java.home"), "bin/java").path,
        listOf("-cp", System.getProperty("java.class.path"), ProcessFixture::class.java.name) + args,
        timeoutMillis = timeout, outputLimitBytes = limit)

    @Test fun argumentsWorkingDirectoryStdinAndBothOutputsPreserveExactValues() = runBlocking {
        val folder = temporary.newFolder("中文 folder ' quote")
        val text = "argument 中文 with spaces ' \" and \\ dollar $"
        val result = runner.run(request("echo", text).copy(workingDirectory = folder.path, stdin = "stdin\n中文".toByteArray()))
        assertEquals(0, result.exitCode)
        assertEquals("$text\n${folder.canonicalPath}\nstdin\n中文", result.stdout)
        assertEquals("stderr-end", result.stderr)
        assertFalse(result.timedOut); assertFalse(result.outputTruncated)
        assertNoPipeThreads()
    }

    @Test fun nonzeroExitAndLargeStderrAreFullyDrained() = runBlocking {
        val result = runner.run(request("flood", "1500000", "7"))
        assertEquals(7, result.exitCode)
        assertEquals("stdout-end", result.stdout)
        assertEquals("E".repeat(1_500_000) + "stderr-end", result.stderr)
        assertNoPipeThreads()
    }

    @Test fun outputBufferIsBoundedWhilePipesContinueDraining() = runBlocking {
        val result = runner.run(request("flood", "1500000", "0", limit = 1024))
        assertEquals(255, result.exitCode)
        assertTrue(result.outputTruncated)
        assertEquals(1024, result.stderr.toByteArray().size)
        assertEquals("stdout-end", result.stdout)
        assertNoPipeThreads()
    }

    @Test fun timeoutReapsTheOwnedProcessAndItsChild() = runBlocking {
        val pids = temporary.root.resolve("timeout-pids.txt")
        val result = runner.run(request("tree", pids.path, timeout = 900))
        assertEquals(255, result.exitCode); assertTrue(result.timedOut)
        assertDead(pids)
        assertNoPipeThreads()
    }

    @Test fun cancellationReapsTheOwnedProcessAndItsChildAndDoesNotBecomeAnExitResult() = runBlocking {
        val pids = temporary.root.resolve("cancel-pids.txt")
        val pending = async { runner.run(request("tree", pids.path)) }
        withTimeout(5_000) { while (!pids.exists() || pids.readLines().size < 2) delay(20) }
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertDead(pids)
        assertNoPipeThreads()
    }

    private suspend fun assertDead(file: File) {
        val ids = file.readLines().map(String::toLong)
        assertEquals(2, ids.size)
        withTimeout(5_000) { while (ids.any { ProcessHandle.of(it).map(ProcessHandle::isAlive).orElse(false) }) delay(20) }
    }
    private fun assertNoPipeThreads() = assertTrue(Thread.getAllStackTraces().keys.none { it.isAlive && it.name.startsWith("toolkit-process-") })
}

object ProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        when (args[0]) {
            "echo" -> {
                print(args[1] + "\n" + File(".").canonicalPath + "\n" + System.`in`.readBytes().toString(Charsets.UTF_8))
                System.err.print("stderr-end")
            }
            "flood" -> {
                System.err.print("E".repeat(args[1].toInt()) + "stderr-end")
                print("stdout-end")
                kotlin.system.exitProcess(args[2].toInt())
            }
            "tree" -> {
                File(args[1]).writeText("${ProcessHandle.current().pid()}\n")
                ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "-cp",
                    System.getProperty("java.class.path"), ProcessFixture::class.java.name, "child", args[1]).inheritIO().start()
                Thread.sleep(60_000)
            }
            "child" -> {
                File(args[1]).appendText("${ProcessHandle.current().pid()}\n")
                Thread.sleep(60_000)
            }
        }
    }
}
