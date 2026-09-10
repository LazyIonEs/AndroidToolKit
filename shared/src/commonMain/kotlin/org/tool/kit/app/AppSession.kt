package org.tool.kit.app

import org.koin.core.context.stopKoin
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** The application entry point owns shutdown; ViewModelStore owners still clear their own VMs. */
@OptIn(ExperimentalAtomicApi::class)
class AppSession(private val closeContainer: () -> Unit) {
    private val closed = AtomicBoolean(false)

    /** 幂等关闭应用容器，即使窗口关闭和进程退出路径重复调用也只释放一次。 */
    fun shutdown() {
        if (closed.compareAndSet(false, true)) closeContainer()
    }
}

private val appSession = AppSession(::stopKoin)

/** 由桌面入口触发应用级资源释放；页面 ViewModel 仍由其导航条目负责回收。 */
fun shutdownAppSession() = appSession.shutdown()
