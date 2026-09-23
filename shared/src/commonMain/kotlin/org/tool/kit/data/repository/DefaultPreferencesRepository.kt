package org.tool.kit.data.repository

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.tool.kit.core.coroutine.AppDispatchers
import org.tool.kit.data.source.PreferencesStorage
import org.tool.kit.domain.preferences.*

/**
 * 应用会话内唯一的设置写入者。主线程接收修改并立即发布状态，后台队列按顺序持久化。
 * 首次读取期间接收的修改会重放到存储快照上，避免慢读取覆盖用户刚输入的值。
 */
class DefaultPreferencesRepository(private val source: PreferencesStorage, dispatchers: AppDispatchers) : PreferencesRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private data class Write(val revision: Long, val change: PreferenceChange)
    private val writes = Channel<Write>(Channel.UNLIMITED)
    private val ready = CompletableDeferred<Unit>()
    private val beforeReady = mutableListOf<PreferenceChange>()
    private val _state = MutableStateFlow(PreferencesSnapshot())
    override val state = _state.asStateFlow()

    init {
        scope.launch {
            var stored = try { source.read() } catch (failure: Exception) {
                ready.completeExceptionally(failure)
                return@launch
            }
            val accepted = _state.value
            var loaded = stored
            // 读取期间的用户修改优先于磁盘旧值，按接收顺序重放。
            beforeReady.forEach { loaded = loaded.changed(it) }
            beforeReady.clear()
            _state.value = loaded.copy(ready = true, revision = accepted.revision,
                outputPathVersion = accepted.outputPathVersion)
            ready.complete(Unit)
            val pending = ArrayDeque<Write>()
            for (write in writes) {
                stored = stored.changed(write.change)
                pending.addLast(write)
                while (pending.isNotEmpty()) {
                    // 失败的队首保留到下次收到修改后重试，不能越过它确认后续版本。
                    val next = pending.first()
                    try {
                        source.write(next.change, stored)
                        pending.removeFirst()
                        _state.value = _state.value.copy(persistedRevision = next.revision, writeFailure = null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        _state.value = _state.value.copy(writeFailure = failure::class.simpleName)
                        // Keep the failed field ahead of later writes; acknowledgements stay contiguous.
                        break
                    }
                }
            }
        }
    }

    /**
     * 接收一次设置修改并返回立即可见的快照；返回不代表已经写入存储。
     * 须在主线程调用，写入确认通过 persistedRevision 反映。
     */
    override fun change(change: PreferenceChange): PreferencesSnapshot {
        check(scope.isActive) { "Preferences session closed" }
        val old = _state.value
        val next = old.changed(change).copy(revision = old.revision + 1,
            // 输出路径使用独立版本，供页面识别外部默认路径变化，避免覆盖自己的编辑草稿。
            outputPathVersion = old.outputPathVersion + if (change is PreferenceChange.OutputPath && change.value != old.userData.defaultOutputPath) 1 else 0)
        // Unlimited here preserves every keystroke/save event without blocking the UI.
        if (!old.ready) beforeReady += change
        _state.value = next
        check(writes.trySend(Write(next.revision, change)).isSuccess) { "Preferences writer closed" }
        return next
    }

    /** 等待首次存储读取和早期修改重放完成；初次读取失败时抛出对应异常。 */
    override suspend fun awaitReady(): PreferencesSnapshot { ready.await(); return state.value }
    /** 关闭设置会话并取消后台写入；此方法不等待尚未写入的修改落盘。 */
    fun close() { writes.close(); ready.cancel(); scope.cancel() }
}
