package org.tool.kit.core.process

/** Arguments are passed directly to ProcessBuilder, never through a shell. */
data class ProcessRequest(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: String? = null,
    val stdin: ByteArray = byteArrayOf(),
    val timeoutMillis: Long = 60_000,
    val outputLimitBytes: Int = 8 * 1024 * 1024,
)
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val outputTruncated: Boolean = false,
)
fun interface ProcessRunner { suspend fun run(request: ProcessRequest): ProcessResult }
