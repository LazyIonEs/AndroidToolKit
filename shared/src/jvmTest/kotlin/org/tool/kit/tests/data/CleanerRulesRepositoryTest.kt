package org.tool.kit.tests.data

import com.russhwolf.settings.*
import com.russhwolf.settings.coroutines.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.data.repository.DefaultCleanerRulesRepository
import org.tool.kit.domain.cleaner.*

@OptIn(ExperimentalSettingsApi::class, ExperimentalCoroutinesApi::class)
class CleanerRulesRepositoryTest {
    @Test fun firstLoadThenWholeConfigRoundTripPreservesIdsAndRevision() = runTest {
        val settings = MapSettings().toFlowSettings(Dispatchers.Unconfined)
        val repo = DefaultCleanerRulesRepository(settings)
        assertEquals(CleanerRuleConfig(), repo.awaitReady().config)
        val custom = CleanerRuleGroup("stable-id", "Logs", target = CleanerTarget.FILE, conditions = listOf(
            CleanerCondition.Text(CleanerTextField.RELATIVE_PATH, TextOperator.STARTS_WITH, "app\\logs"),
            CleanerCondition.FileSizeGreaterThan(1536, "1.5", SizeUnit.KB)))
        repo.save(CleanerRuleConfig(maxDepth = 24, includeHidden = false, rules = listOf(defaultBuildRule(), custom)))
        val restored = DefaultCleanerRulesRepository(settings).awaitReady()
        assertEquals(repo.state.value, restored)
        assertEquals(1, restored.config.revision)
        assertEquals("stable-id", restored.config.rules.last().id)
        assertEquals("app/logs", (restored.config.rules.last().conditions.first() as CleanerCondition.Text).value)
        assertFailsWith<IllegalStateException> { repo.save(CleanerRuleConfig()) }
    }

    @Test fun invalidStructuresAndEnumsRecoverWithoutWritingAndFutureSchemaCannotBeOverwritten() = runTest {
        for (raw in listOf("{broken", """{"schemaVersion":1,"rules":[]}""", """{"schemaVersion":1,"rules":[{"target":"FUTURE"}]}""", """{"schemaVersion":99,"rules":[]}""")) {
            val physical = MapSettings(DefaultCleanerRulesRepository.KEY to raw)
            val repo = DefaultCleanerRulesRepository(physical.toFlowSettings(Dispatchers.Unconfined))
            val state = repo.awaitReady()
            assertTrue(state.recovered); assertEquals(CleanerRuleConfig(), state.config)
            assertEquals(raw, physical.getString(DefaultCleanerRulesRepository.KEY, ""))
            if (state.unsupportedVersion) {
                assertFailsWith<IllegalStateException> { repo.save(CleanerRuleConfig()) }
                assertEquals(raw, physical.getString(DefaultCleanerRulesRepository.KEY, ""))
            }
        }
    }

    @Test fun failedWritesDoNotPublishAndDeletionAndSaveMutuallyExcludeEachOther() = runTest {
        val base = MapSettings().toFlowSettings(Dispatchers.Unconfined)
        val fail = DefaultCleanerRulesRepository(object : FlowSettings by base {
            override suspend fun putString(key: String, value: String) { error("Disk full") }
        })
        fail.awaitReady()
        assertFailsWith<IllegalStateException> { fail.save(CleanerRuleConfig()) }
        assertEquals(0, fail.state.value.config.revision)
        assertTrue(fail.beginDeletion()); fail.endDeletion()
        val gate = CompletableDeferred<Unit>()
        val repo = DefaultCleanerRulesRepository(object : FlowSettings by base {
            override suspend fun putString(key: String, value: String) { gate.await(); base.putString(key, value) }
        })
        repo.awaitReady()
        assertTrue(repo.beginDeletion())
        assertFailsWith<IllegalStateException> { repo.save(CleanerRuleConfig()) }
        repo.endDeletion()
        val saving = launch { repo.save(CleanerRuleConfig()) }; runCurrent()
        assertFalse(repo.beginDeletion()); assertEquals(0, repo.state.value.config.revision)
        gate.complete(Unit); saving.join()
        assertEquals(1, repo.state.value.config.revision)
        assertTrue(repo.beginDeletion()); repo.endDeletion()
    }
}
