package org.tool.kit.tests.data

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmBuildCachesDataSource
import org.tool.kit.domain.cleaner.*

class CleanerSafetyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val repo = JvmBuildCachesDataSource(Dispatchers.IO)
    private val logs = CleanerRuleGroup("logs", "Logs", target = CleanerTarget.FILE, conditions = listOf(CleanerCondition.Text(operator = TextOperator.ENDS_WITH, value = ".log")))

    @Test fun hiddenDepthDedupAndParentPruningApplyToBothTargetTypes() = runBlocking {
        val root = temporary.root
        root.resolve(".hidden/build").mkdirs()
        root.resolve("build/nested/build").mkdirs()
        root.resolve("build/nested/a.log").writeText("nested")
        root.resolve(".hidden.log").writeText("hidden")
        root.resolve("a.log").writeText("log")
        root.resolve("outer/build").mkdirs()
        val request = CleanerScanRequest.from(root.path, CleanerRuleConfig(maxDepth = 1, includeHidden = false, rules = listOf(defaultBuildRule(), logs, logs.copy(id = "other"))))
        val result = repo.scan(request).toList()
        assertEquals(setOf("build", "a.log"), result.map { it.displayPath }.toSet())
        val file = result.single { !it.isDirectory }
        assertEquals(setOf("logs", "other"), file.matchedRuleIds)
        assertFalse(file.defaultSelected); assertTrue(result.single { it.isDirectory }.defaultSelected)
        val hidden = repo.scan(request.copy(includeHidden = true, maxDepth = 2)).toList()
        assertEquals(setOf("build", "a.log", ".hidden.log", ".hidden/build", "outer/build"), hidden.map { it.displayPath }.toSet())
        assertEquals(hidden.size, hidden.map { it.path }.distinct().size)
    }

    @Test fun changingToLinksFilesMissingAndOutsideAncestorsFailsSafely() = runBlocking {
        val root = temporary.newFolder("scan")
        val outside = temporary.newFolder("outside").apply { resolve("keep").writeText("keep") }
        val link = root.resolve("link/build").apply { mkdirs() }
        val changed = root.resolve("changed/build").apply { mkdirs() }
        val gone = root.resolve("gone/build").apply { mkdirs() }
        val ancestor = root.resolve("ancestor/build").apply { mkdirs() }
        val items = repo.scan(root.path).toList()
        link.delete(); Files.createSymbolicLink(link.toPath(), outside.toPath())
        changed.delete(); changed.writeText("replacement")
        gone.delete()
        ancestor.delete(); ancestor.parentFile.delete(); Files.createSymbolicLink(ancestor.parentFile.toPath(), outside.toPath())
        items.forEach { assertTrue(repo.delete(it).safetyFailure, it.path) }
        assertEquals("keep", outside.resolve("keep").readText())
        assertTrue(changed.isFile)
    }

    @Test fun directoryDeletionUnlinksNestedSymlinksWithoutTouchingTargets() = runBlocking {
        val root = temporary.newFolder("scan")
        val outside = temporary.newFolder("outside").apply { resolve("keep").writeBytes(ByteArray(4000)) }
        val cache = root.resolve("build").apply { mkdirs(); resolve("own").writeText("own") }
        Files.createSymbolicLink(cache.resolve("external").toPath(), outside.toPath())
        val item = repo.scan(root.path).single()
        assertEquals(cache.length() + cache.resolve("own").length(), item.bytes)
        assertTrue(repo.delete(item).deleted)
        assertFalse(cache.exists()); assertEquals(4000, outside.resolve("keep").length())
    }

    @Test fun changedFileSizeIsReevaluatedFromTheScannedRules() = runBlocking {
        val file = temporary.root.resolve("a.log").apply { writeBytes(ByteArray(1025)) }
        val rule = logs.copy(conditions = logs.conditions + CleanerCondition.FileSizeGreaterThan(1024, "1", SizeUnit.KB))
        val item = repo.scan(CleanerScanRequest.from(temporary.root.path, CleanerRuleConfig(rules = listOf(rule)))).single()
        file.writeBytes(ByteArray(1024))
        assertTrue(repo.delete(item).safetyFailure); assertTrue(file.exists())
        assertTrue(repo.delete(item.copy(path = temporary.root.path)).safetyFailure)
        assertFalse(repo.delete(item.copy(request = null)).deleted)
    }

    @Test fun inaccessibleEntriesAreNonFatalAndOnlyMatchedDirectoriesAreMeasured() = runBlocking {
        val root = temporary.root
        val blocked = root.resolve("blocked").apply { mkdirs(); resolve("inner").writeText("secret") }
        val good = root.resolve("build").apply { mkdirs() }
        val old = Files.getPosixFilePermissions(blocked.toPath())
        try {
            Files.setPosixFilePermissions(blocked.toPath(), emptySet())
            val issues = mutableListOf<String>()
            val depthOne = CleanerScanRequest.from(root.path, CleanerRuleConfig(maxDepth = 1))
            assertEquals(listOf(good.canonicalPath), repo.scan(depthOne, issues::add).toList().map { it.path })
            assertTrue(issues.isEmpty(), "Unmatched directories at the depth limit must not be measured")
            repo.scan(depthOne.copy(maxDepth = 2), issues::add).toList()
            assertTrue(issues.contains(blocked.canonicalPath))
            issues.clear()
            val matchBlocked = defaultBuildRule().copy(conditions = listOf(CleanerCondition.Text(value = "blocked")))
            assertEquals(listOf(blocked.canonicalPath), repo.scan(depthOne.copy(rules = listOf(matchBlocked)), issues::add).toList().map { it.path })
            assertTrue(issues.contains(blocked.canonicalPath), "Matched directory measurement reports access failures")
        } finally { Files.setPosixFilePermissions(blocked.toPath(), old) }
    }

    @Test fun fileRulesDoNotMeasureDirectoriesAndCancelledScanEmitsNoLateResults() = runBlocking {
        val root = temporary.root
        repeat(40) { root.resolve("$it.log").writeBytes(ByteArray(1025)) }
        root.resolve("folder.log").mkdirs()
        val request = CleanerScanRequest.from(root.path, CleanerRuleConfig(rules = listOf(logs.copy(conditions = logs.conditions + CleanerCondition.FileSizeGreaterThan(1024, "1", SizeUnit.KB)))))
        val results = mutableListOf<BuildDirectory>()
        val job = launch { repo.scan(request).collect { results += it; cancel() } }
        job.join(); delay(30)
        assertEquals(1, results.size); assertFalse(results.single().isDirectory)
        assertEquals(40, repo.scan(request).toList().size)
    }

    @Test fun rootMustExistAndBeARealAccessibleDirectory() = runBlocking {
        assertFailsWith<IllegalArgumentException> { repo.scan(temporary.root.resolve("missing").path).toList() }
        val file = temporary.newFile("file")
        assertFailsWith<IllegalArgumentException> { repo.scan(file.path).toList() }
        val link = temporary.root.resolve("alias")
        Files.createSymbolicLink(link.toPath(), temporary.root.toPath())
        assertFailsWith<IllegalArgumentException> { repo.scan(link.path).toList() }
        Unit
    }
}
