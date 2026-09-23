package org.tool.kit.data.generator

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Bounded private workers; all writes have exited before failure/cancellation reaches cleanup. */
internal fun parallelJunkWork(count: Int, parallelism: Int = 4, action: (Int) -> Unit) {
    if (count == 0) return
    require(count >= 0 && parallelism > 0)
    val failure = AtomicReference<Throwable?>()
    val next = AtomicInteger()
    val workers = minOf(count, parallelism)
    val executor = Executors.newFixedThreadPool(workers)
    try {
        repeat(workers) {
            executor.submit {
                while (failure.get() == null) {
                    val index = next.getAndIncrement()
                    if (index >= count) break
                    try { action(index) } catch (error: Throwable) { failure.compareAndSet(null, error) }
                }
            }
        }
    } finally {
        executor.shutdown()
        var interrupted = false
        while (!executor.isTerminated) {
            try { executor.awaitTermination(1, TimeUnit.SECONDS) }
            catch (error: InterruptedException) { interrupted = true; failure.compareAndSet(null, error) }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
    failure.get()?.let { throw it }
}
