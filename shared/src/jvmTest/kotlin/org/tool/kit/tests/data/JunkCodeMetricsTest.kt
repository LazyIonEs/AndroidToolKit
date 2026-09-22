package org.tool.kit.tests.data

import org.junit.Test
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.JunkCodeMetrics
import kotlin.test.assertEquals

class JunkCodeMetricsTest {
    @Test fun generatedNamesNormalizeEquallyForComOrgAndPlatformLikePrefixes() {
        val metrics = JunkCodeMetrics()
        val prefixes = listOf("com/example", "org/example", "androidtools/demo", "androidxdemo/app")
        prefixes.forEachIndexed { index, prefix ->
            metrics.add(fixture("$prefix/Generated$index", "calculate$index"), "utility")
        }
        val result = metrics.snapshot()
        assertEquals(4L, result["algorithmMethods"])
        assertEquals(1, result["uniqueNormalizedMethods"])
        assertEquals(0.75, result["normalizedMethodDuplicateRate"])
    }

    @Test fun exactPlatformNamespacesStillPreserveApiMethodIdentity() {
        for (prefix in listOf("java/example", "android/example", "androidx/example")) {
            val metrics = JunkCodeMetrics()
            metrics.add(fixture("$prefix/PlatformType", "firstApi"), "fixture")
            metrics.add(fixture("$prefix/PlatformType", "secondApi"), "fixture")
            assertEquals(2, metrics.snapshot()["uniqueNormalizedMethods"], prefix)
        }
    }

    private fun fixture(owner: String, methodName: String): ByteArray {
        val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
        writer.visit(V1_8, ACC_PUBLIC or ACC_SUPER, owner, null, "java/lang/Object", null)
        writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitCode()
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        // The generated owner appears in a type instruction, member owner and descriptor.
        writer.visitMethod(ACC_PUBLIC, "process", "(I)I", null, null).apply {
            visitCode()
            visitTypeInsn(NEW, owner)
            visitInsn(DUP)
            visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false)
            visitVarInsn(ASTORE, 2)
            repeat(3) {
                visitVarInsn(ILOAD, 1)
                visitInsn(ICONST_1)
                visitInsn(IADD)
                visitVarInsn(ISTORE, 1)
            }
            visitVarInsn(ALOAD, 2)
            visitVarInsn(ALOAD, 2)
            visitVarInsn(ILOAD, 1)
            visitMethodInsn(INVOKEVIRTUAL, owner, methodName, "(L$owner;I)I", false)
            visitInsn(IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitMethod(ACC_PUBLIC, methodName, "(L$owner;I)I", null, null).apply {
            visitCode()
            visitVarInsn(ILOAD, 2)
            visitInsn(IRETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
        writer.visitEnd()
        return writer.toByteArray()
    }
}
