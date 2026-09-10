package org.tool.kit.core.process

/**
 * 外部工具的执行参数；参数逐项传入进程，不经过 shell 解析。
 *
 * @property timeoutMillis 进程等待的超时时间，单位为毫秒。
 * @property outputLimitBytes stdout、stderr 各自保留的字节上限，超出部分仍需排空以免阻塞进程。
 */
data class ProcessRequest(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: String? = null,
    val stdin: ByteArray = byteArrayOf(),
    val timeoutMillis: Long = 60_000,
    val outputLimitBytes: Int = 8 * 1024 * 1024,
)
/** 外部进程的退出状态和 UTF-8 输出；超时和输出截断分别标记，不等同于普通非零退出。 */
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val outputTruncated: Boolean = false,
)
/** 执行外部工具并收集输出；调用方取消时，实现负责终止进程并回收读写任务。 */
fun interface ProcessRunner { suspend fun run(request: ProcessRequest): ProcessResult }
