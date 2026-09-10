package org.tool.kit.data.generator

import org.tool.kit.utils.*

import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream

/**
 * 等待所有公共线程池任务结束后再抛出首个异常。
 * 若任一工作线程失败就提前返回，外层清理目录可能与仍在写入的其他线程冲突。
 */
internal fun parallelJunkWork(count: Int, action: (Int) -> Unit) {
    // 首个失败保存在共享槽位中，其余任务继续退出并汇合，随后统一报告失败。
    val failure = AtomicReference<Throwable?>()
    IntStream.range(0, count).parallel().forEach { index ->
        try { action(index) } catch (error: Throwable) { failure.compareAndSet(null, error) }
    }
    failure.get()?.let { throw it }
}
