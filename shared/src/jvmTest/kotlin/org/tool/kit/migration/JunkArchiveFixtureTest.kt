package org.tool.kit.migration

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.utils.AndroidJunkGenerator
import org.tool.kit.utils.MultiAarGenerator
import java.io.File
import java.util.jar.JarInputStream
import java.util.zip.ZipFile
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JunkArchiveFixtureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun tinySingleArchiveContainsManifestClassesResourcesAndCleansItsWorkspace() {
        val workspace = temporary.newFolder("workspace")
        val output = temporary.newFolder("output")
        val result = AndroidJunkGenerator(workspace.path, output.path, "com.fixture.junk", 1, 1, "fixture_").startGenerate()
        assertArchive(result)
        assertFalse(workspace.resolve("comfixturejunk").exists())
    }

    @Test fun tinyBatchRecreatesOnlyItsOutputSubdirectoryAndProducesDistinctArchives() = runTest {
        val workspace = temporary.newFolder("workspace")
        val output = temporary.newFolder("output")
        val sibling = output.resolve("unrelated.txt").apply { writeText("keep") }
        val stale = output.resolve("batch/stale.txt").apply { parentFile.mkdirs(); writeText("old fixture") }
        val results = MultiAarGenerator.generate(workspace.path, output.path, "batch", 2, 1, 1, 1, 1)
        assertEquals(2, results.size)
        assertEquals(2, results.map { it.name }.distinct().size)
        assertFalse(stale.exists())
        assertEquals("keep", sibling.readText())
        results.forEach(::assertArchive)
    }

    private fun assertArchive(file: File) {
        assertTrue(file.length() > 0)
        ZipFile(file).use { zip ->
            assertNotNull(zip.getEntry("AndroidManifest.xml"))
            val classes = assertNotNull(zip.getEntry("classes.jar"))
            JarInputStream(zip.getInputStream(classes)).use { jar ->
                var count = 0
                while (jar.nextJarEntry != null) count++
                assertTrue(count > 0)
            }
            assertTrue(zip.entries().asSequence().any { it.name.startsWith("res/") })
        }
    }
}
