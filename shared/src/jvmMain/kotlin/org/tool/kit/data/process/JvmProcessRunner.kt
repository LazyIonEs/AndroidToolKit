package org.tool.kit.data.process

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import org.tool.kit.core.process.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Owns the child process and all three pipes until execution or cancellation has been reaped. */
class JvmProcessRunner(private val io: CoroutineDispatcher) : ProcessRunner {
    /**
     * 在可中断的 IO 任务中执行进程，分别排空标准输出和错误输出，并处理超时及取消。
     * 输出超过上限时将退出码标为 255，避免调用方把截断的数据当成完整成功结果。
     */
    override suspend fun run(request: ProcessRequest): ProcessResult = runInterruptible(io) {
        require(request.timeoutMillis > 0)
        require(request.outputLimitBytes > 0)
        val process = ProcessBuilder(listOf(request.executable) + request.arguments)
            .directory(request.workingDirectory?.let(::File)).start()
        val descendants = linkedSetOf<ProcessHandle>()
        val stdout = BoundedOutput(request.outputLimitBytes)
        val stderr = BoundedOutput(request.outputLimitBytes)
        val id = sequence.incrementAndGet()
        // 三条管道独立处理，避免等待退出的线程与子进程相互等待读写。
        val readers = listOf(
            thread(name = "toolkit-process-$id-stdout", isDaemon = true) { drain(process.inputStream, stdout) },
            thread(name = "toolkit-process-$id-stderr", isDaemon = true) { drain(process.errorStream, stderr) },
            thread(name = "toolkit-process-$id-stdin", isDaemon = true) {
                try { process.outputStream.use { it.write(request.stdin) } } catch (_: java.io.IOException) { }
            },
        )
        var code = 255
        var timedOut = false
        try {
            // 使用单调时钟计算超时，系统时间调整不会延长或缩短等待窗口。
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(request.timeoutMillis)
            while (process.isAlive) {
                process.descendants().use { stream -> stream.forEach { descendants += it } }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) { timedOut = true; break }
                process.waitFor(minOf(50, TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1)), TimeUnit.MILLISECONDS)
            }
            if (!timedOut) code = process.exitValue()
            // Normal completion drains both outputs. A child inheriting a pipe cannot hold us forever.
            if (!timedOut) readers.forEach { it.join(1_000) }
        } finally {
            // Cancellation interrupts waitFor/join. Cleanup still closes every owned resource.
            // 清除中断标记后执行回收，避免 join/waitFor 立即再次被中断。
            Thread.interrupted()
            process.descendants().use { stream -> stream.forEach { descendants += it } }
            descendants.toList().asReversed().forEach { if (it.isAlive) it.destroyForcibly() }
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.waitFor(2_000, TimeUnit.MILLISECONDS) }
            listOf(process.outputStream, process.inputStream, process.errorStream).forEach { runCatching { it.close() } }
            readers.forEach { reader ->
                runCatching { reader.join(2_000) }
                check(!reader.isAlive) { "Process pipe did not close" }
            }
        }
        val truncated = stdout.truncated || stderr.truncated
        ProcessResult(if (truncated) 255 else code, stdout.text(), stderr.text(), timedOut, truncated)
    }

    /** 持续读取到 EOF；达到保留上限后仍排空管道，防止子进程因写满缓冲区而挂起。 */
    private fun drain(input: InputStream, output: BoundedOutput) {
        try { input.use {
            val buffer = ByteArray(8192)
            while (true) {
                val size = it.read(buffer)
                if (size < 0) break
                output.write(buffer, size)
            }
        } } catch (_: java.io.IOException) { }
    }

    private class BoundedOutput(private val limit: Int) {
        private val bytes = ByteArrayOutputStream()
        var truncated = false
            private set
        fun write(buffer: ByteArray, size: Int) {
            val accepted = minOf(size, limit - bytes.size())
            bytes.write(buffer, 0, accepted)
            if (accepted < size) truncated = true
        }
        fun text(): String = bytes.toString(Charsets.UTF_8)
    }
    private companion object { val sequence = AtomicLong() }
}
