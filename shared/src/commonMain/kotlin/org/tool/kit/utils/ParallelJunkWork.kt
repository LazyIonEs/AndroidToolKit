package org.tool.kit.utils

import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream

/** Keep the same common-pool parallelism, but join every worker before the owner may clean its workspace. */
internal fun parallelJunkWork(count: Int, action: (Int) -> Unit) {
    val failure = AtomicReference<Throwable?>()
    IntStream.range(0, count).parallel().forEach { index ->
        try { action(index) } catch (error: Throwable) { failure.compareAndSet(null, error) }
    }
    failure.get()?.let { throw it }
}
