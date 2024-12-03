package utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Executes a command with ability to pass `stdin` and capture `stdout` and `stderr`.
 */
class ExternalCommand(private val executable: String) {

    @Throws(IOException::class)
    fun execute(
        args: List<String>,
        stdin: InputStream,
        stdout: OutputStream,
        stderr: OutputStream,
        timeout: Long,
        unit: TimeUnit
    ): Int {
        val command: MutableList<String> = ArrayList()
        val exe = File(executable)
        command.add(exe.absolutePath)
        command.addAll(args)
        val pb = ProcessBuilder(command)
        val process = pb.start()
        val inToProcess = PipeConnector(stdin, process.outputStream)
        val processToOut = PipeConnector(process.inputStream, stdout)
        val processToErr = PipeConnector(process.errorStream, stderr)
        inToProcess.start()
        processToOut.start()
        processToErr.start()
        var code = 255
        try {
            val finished = process.waitFor(timeout, unit)
            if (!finished) {
                process.destroyForcibly()
            } else {
                code = process.exitValue()
            }
            processToOut.join()
            stdin.close()
            inToProcess.join()
        } catch (e: InterruptedException) {
            println(e)
        }
        return code
    }

    private class PipeConnector(private val input: InputStream, private val output: OutputStream) : Thread() {
        override fun run() {
            try {
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } > 0) {
                    output.write(buffer, 0, read)
                    output.flush()
                }
            } catch (e: IOException) {
                // Ignore and exit the thread
            }
        }
    }
}