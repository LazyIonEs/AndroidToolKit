package org.tool.kit.tests.domain

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.source.JvmBuildCachesDataSource
import org.tool.kit.domain.cleaner.*
import org.tool.kit.domain.repository.BuildCachesRepository
import org.tool.kit.domain.usecase.*
import org.tool.kit.utils.getFileLength

@OptIn(ExperimentalCoroutinesApi::class)
class ScanBuildCachesUseCaseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun exactNamesDepthBoundariesAndRootExclusion() = runTest {
        val root = temporary.root
        fun atDepth(depth: Int) = root.resolve("depth-$depth/" + (1 until depth - 1).joinToString("/") { "level-$it" } + "/build").apply { mkdirs(); resolve("data").writeBytes(ByteArray(depth)) }
        val nine = atDepth(9); val ten = atDepth(10); val eleven = atDepth(11)
        val foo = root.resolve("build.foo").apply { mkdirs() }
        val nested = foo.resolve("inner/build").apply { mkdirs() }
        root.resolve("Build").mkdirs()
        val hidden = root.resolve(".hidden/build").apply { mkdirs() }
        root.resolve("ordinary/build").apply { parentFile.mkdirs(); writeText("file") }
        val repo = JvmBuildCachesDataSource(Dispatchers.IO)
        val actual = repo.scan(root.path).toList()
        assertEquals(setOf(nine, ten, nested, hidden).map { it.canonicalPath }.toSet(), actual.map { it.path }.toSet())
        assertTrue(eleven.exists())
        actual.forEach { item ->
            val file = java.io.File(item.path)
            assertEquals(file.getFileLength(), item.bytes)
            assertEquals(file.lastModified(), item.modifiedAt)
            assertEquals(root.canonicalPath, item.scanRoot)
            assertEquals(setOf("android-build"), item.matchedRuleIds)
        }
        assertEquals(listOf(nested.canonicalPath), repo.scan(foo.path).toList().map { it.path })
        assertTrue(repo.scan(nine.path).toList().isEmpty())
    }

    @Test fun realDeletionPreservesUnselectedSiblingsAndUsesTheScannedLengthAfterChanges() = runTest {
        val root = temporary.root
        val selected = root.resolve("selected/build").apply { mkdirs(); resolve("data").writeBytes(ByteArray(99)) }
        val changed = root.resolve("changed/build").apply { mkdirs() }
        val missing = root.resolve("missing/build").apply { mkdirs() }
        val sibling = root.resolve("keep/build").apply { mkdirs(); resolve("keep").writeText("keep") }
        val repo = JvmBuildCachesDataSource(Dispatchers.IO)
        val original = repo.scan(root.path).toList().filter { it.path != sibling.canonicalPath }
        selected.resolve("later").writeBytes(ByteArray(901))
        assertTrue(changed.delete()); changed.writeText("replaced with file")
        assertTrue(missing.delete())
        val result = DeleteBuildCachesUseCase(repo)(original).toList()
        assertEquals(listOf(selected.canonicalPath), result.filter { it.deleted }.map { it.directory.path })
        assertTrue(result.filterNot { it.deleted }.all { it.safetyFailure })
        assertTrue(changed.isFile)
        assertEquals(original.sumOf { it.bytes }, result.sumOf { it.directory.bytes })
        assertEquals("keep", sibling.resolve("keep").readText())
    }

    @Test fun symbolicLinksAreNeverCandidatesOrTraversed() = runTest {
        val root = temporary.newFolder("scan")
        val target = temporary.newFolder("linked-fixture").apply { resolve("build/data").apply { parentFile.mkdirs(); writeText("fixture") } }
        Files.createSymbolicLink(root.resolve("alias").toPath(), target.toPath())
        Files.createSymbolicLink(root.resolve("build").toPath(), target.toPath())
        val actual = ScanBuildCachesUseCase(JvmBuildCachesDataSource(Dispatchers.IO))(root.path).toList()
        assertTrue(actual.isEmpty())
    }

    @Test fun realPermissionFailureReturnsTheSurvivingMetadataAndCanRetry() = runTest {
        val cache = temporary.root.resolve("build").apply { mkdirs(); resolve("protected").writeText("fixture") }
        val repo = JvmBuildCachesDataSource(Dispatchers.IO)
        val directory = repo.scan(temporary.root.path).first()
        val permissions = Files.getPosixFilePermissions(cache.toPath())
        try {
            Files.setPosixFilePermissions(cache.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE))
            val result = repo.delete(directory)
            assertFalse(result.deleted)
            assertTrue(result.exists && result.isDirectory)
            assertEquals(directory.bytes, result.directory.bytes)
        } finally { Files.setPosixFilePermissions(cache.toPath(), permissions) }
        assertTrue(repo.delete(directory).deleted)
    }

    @Test fun deleteCapturesItsInputAndCancellationDoesNotStartTheNextPath() = runTest {
        val requests = mutableListOf<String>(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val a = BuildDirectory("root", "root/a/build", "a/build", 12, 0, true, true)
        val b = a.copy(path = "root/b/build")
        val repository = object : BuildCachesRepository {
            override fun scan(root: String) = emptyFlow<BuildDirectory>()
            override suspend fun delete(directory: BuildDirectory): DeleteBuildCacheResult {
                requests += directory.path; entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                return DeleteBuildCacheResult(directory, true, false, false)
            }
        }
        val input = mutableListOf(a, b)
        val operation = DeleteBuildCachesUseCase(repository)(input)
        input.clear()
        val outcomes = mutableListOf<DeleteBuildCacheResult>()
        val job = launch { operation.toList(outcomes) }
        entered.await(); job.cancel(); release.complete(Unit); job.join()
        assertEquals(listOf(a.path), requests)
        assertTrue(outcomes.isEmpty())
    }
}
