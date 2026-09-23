package org.tool.kit.core.coroutine

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 应用统一注入的调度器：io 用于阻塞读写，default 用于计算，main 用于状态发布。
 * 测试可替换为同一测试调度器，以控制任务推进顺序。
 */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
