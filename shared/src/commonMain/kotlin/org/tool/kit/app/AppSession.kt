package org.tool.kit.app

import org.koin.core.context.stopKoin
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** The application entry point owns shutdown; ViewModelStore owners still clear their own VMs. */
@OptIn(ExperimentalAtomicApi::class)
class AppSession(private val closeContainer: () -> Unit) {
    private val closed = AtomicBoolean(false)

    fun shutdown() {
        if (closed.compareAndSet(false, true)) closeContainer()
    }
}

private val appSession = AppSession(::stopKoin)

fun shutdownAppSession() = appSession.shutdown()
