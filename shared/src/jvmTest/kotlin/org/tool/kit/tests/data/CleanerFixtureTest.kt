package org.tool.kit.tests.data

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.utils.getFileLength

class CleanerFixtureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun originalSizeRuleIncludesDirectoryMetadataAndNestedFiles() {
        val root = temporary.newFolder("build.foo")
        val nested = root.resolve("nested").apply { mkdir() }
        val file = nested.resolve("fixture.bin").apply { writeBytes(ByteArray(123)) }
        assertEquals(root.length() + nested.length() + file.length(), root.getFileLength())
        assertEquals(123, file.getFileLength())
    }

    @Test fun fixtureTreeCoversScanDepthAndDirectoryNameBoundaries() {
        val root = temporary.root
        fun buildAtDepth(depth: Int): File {
            var parent = root.resolve("depth-$depth")
            repeat(depth - 2) { parent = parent.resolve("level-$it") }
            return parent.resolve("build").apply { mkdirs(); resolve("data").writeText("fixture") }
        }
        val depth9 = buildAtDepth(9)
        val depth10 = buildAtDepth(10)
        val depth11 = buildAtDepth(11)
        val buildFoo = root.resolve("build.foo").apply { mkdirs() }
        val nested = buildFoo.resolve("inner/build").apply { mkdirs() }
        // Characterizes the exact old traversal until ScanBuildCachesUseCase owns it.
        val selected = root.walk().maxDepth(10)
            .onEnter { it.parentFile?.nameWithoutExtension != "build" }
            .filter { it.isDirectory && it.nameWithoutExtension == "build" }.toList()
        assertEquals(setOf(depth9, depth10, buildFoo), selected.toSet())
        assertTrue(depth11.exists())
        assertTrue(nested.exists())
    }
}
