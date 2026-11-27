package org.tool.kit.tests.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.JunkAarComparison
import org.tool.kit.data.generator.JunkProject
import org.tool.kit.data.generator.JunkProjectArchive
import org.tool.kit.data.generator.method
import kotlin.test.assertEquals

class JunkAarComparisonTest {
    @Test fun edgeOverlapCountsRepeatedInternalInvocationsAcrossDifferentNames() {
        val root = Files.createTempDirectory("toolkit-call-edges-test-").toFile()
        try {
            val left = archive(root, "first", "compute", 3)
            val right = archive(root, "second", "adjust", 2)
            val comparison = JunkAarComparison.compare(listOf(left, right), root)
            val report = Json.parseToJsonElement(comparison.toJson()).jsonObject
            val pair = report.getValue("pairs").jsonArray.single().jsonObject
            assertEquals(0.0, pair.getValue("callPatternOverlap").jsonPrimitive.double)
            assertEquals(1.0, pair.getValue("callEdgeOverlap").jsonPrimitive.double)
            assertEquals(2, pair.getValue("matchedCallEdges").jsonPrimitive.int)
            assertEquals(2, pair.getValue("comparedCallEdges").jsonPrimitive.int)
            assertEquals(listOf(3, 2), report.getValue("archives").jsonArray.map { record ->
                record.jsonObject.getValue("callEdges").jsonPrimitive.int
            })
            val highest = report.getValue("highestPairs").jsonObject.getValue("callEdges").jsonArray.single().jsonObject
            val shared = highest.getValue("leadingCallEdges").jsonArray.single().jsonObject
            assertEquals("INVOKESTATIC", shared.getValue("opcode").jsonPrimitive.content)
            assertEquals("(I)I", shared.getValue("descriptor").jsonPrimitive.content)
            assertEquals(2, shared.getValue("matched").jsonPrimitive.int)
            assertEquals("compute", shared.getValue("leftCalleeNames").jsonArray.single().jsonPrimitive.content)
            assertEquals("adjust", shared.getValue("rightCalleeNames").jsonArray.single().jsonPrimitive.content)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun archive(root: File, projectId: String, calleeName: String, repetitions: Int): JunkProjectArchive {
        val caller = "com/fixture/$projectId/Caller"
        val target = "com/fixture/$projectId/Callee"
        val callerBytes = fixtureClass(caller) {
            method(ACC_PRIVATE or ACC_STATIC, "local", "()V") { visitInsn(RETURN) }
            method(ACC_PUBLIC or ACC_STATIC, "run", "()V") {
                repeat(repetitions) {
                    visitInsn(ICONST_1)
                    visitMethodInsn(INVOKESTATIC, target, calleeName, "(I)I", false)
                    visitInsn(POP)
                }
                visitMethodInsn(INVOKESTATIC, caller, "local", "()V", false)
                visitInsn(ICONST_M1)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(I)I", false)
                visitInsn(POP)
                visitTypeInsn(NEW, target)
                visitInsn(DUP)
                visitMethodInsn(INVOKESPECIAL, target, "<init>", "()V", false)
                visitInsn(POP)
                visitInsn(RETURN)
            }
        }
        val targetBytes = fixtureClass(target) {
            method(ACC_PUBLIC, "<init>", "()V") {
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
                visitInsn(RETURN)
            }
            method(ACC_PUBLIC or ACC_STATIC, calleeName, "(I)I") {
                visitVarInsn(ILOAD, 0)
                visitInsn(IRETURN)
            }
        }
        val jar = ByteArrayOutputStream()
        ZipOutputStream(jar).use { zip ->
            zip.writeEntry("$caller.class", callerBytes)
            zip.writeEntry("$target.class", targetBytes)
        }
        val aar = root.resolve("$projectId.aar")
        ZipOutputStream(aar.outputStream()).use { it.writeEntry("classes.jar", jar.toByteArray()) }
        return JunkProjectArchive(JunkProject(projectId, "com.fixture.$projectId", 1, 1), aar, activities = 1)
    }

    private fun fixtureClass(name: String, body: ClassWriter.() -> Unit): ByteArray = ClassWriter(ClassWriter.COMPUTE_MAXS).run {
        visit(V1_8, ACC_PUBLIC or ACC_SUPER, name, null, "java/lang/Object", null)
        body()
        visitEnd()
        toByteArray()
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }
}
