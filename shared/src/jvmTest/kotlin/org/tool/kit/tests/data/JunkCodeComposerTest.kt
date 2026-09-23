package org.tool.kit.tests.data

import java.security.MessageDigest
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.*
import kotlin.test.*

/** JVM execution complements archive parsing; platform stubs do not replace device/inflate tests. */
class JunkCodeComposerTest {
    @Test fun deterministicUnitsHaveDifferentTypedGraphsAndOnlySafeMethods() {
        val policy = JunkGenerationPolicy(seed = 57)
        val seenRoles = mutableSetOf<String>()
        val seenCounts = mutableSetOf<Int>()
        val seenFragments = mutableSetOf<Int>()
        repeat(64) { index ->
            val unit = JunkCodeUnit(index, "com/fixture/code/Activity$index", "page_$index", policy.seedFor(index), listOf("com/fixture/shared/MathOps"))
            val first = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            val second = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            assertEquals(first.classes.keys, second.classes.keys)
            first.classes.forEach { (name, bytes) ->
                assertContentEquals(bytes, second.classes.getValue(name))
                assertTrue(name.startsWith("com/fixture/"))
                assertFalse(name.contains("/R$"))
                val reader = ClassReader(bytes)
                assertEquals(0, reader.access and ACC_MODULE)
                val text = bytes.toString(Charsets.ISO_8859_1)
                listOf("android/util/Log", "android/widget/Toast", "SharedPreferences", "java/net/", "java/io/File").forEach {
                    assertFalse(text.contains(it), "$name must not use $it")
                }
            }
            seenRoles += first.roles.values
            seenCounts += first.classes.size
            seenFragments += first.fragmentCount
            assertTrue(first.customViews.all { first.classes.containsKey(it.replace('.', '/')) })
        }
        assertTrue(seenCounts.size >= 5)
        assertEquals(setOf(0, 1, 2), seenFragments)
        assertTrue(seenRoles.containsAll(listOf("activity", "fragment", "utility", "dto", "converter", "validator", "collection", "config", "customView", "interface", "implementation")))
    }

    @Test fun generatedClassesVerifyAndExecuteAcrossSeedsAndBoundaryInputs() {
        repeat(32) { index ->
            val policy = JunkGenerationPolicy(seed = index.toLong(), enableFragments = index % 2 == 0)
            val helper = "com/fixture/shared/MathOps"
            val unit = JunkCodeUnit(index, "com/fixture/Activity$index", "page_$index", policy.seedFor(index), listOf(helper))
            val output = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            val loader = FixtureLoader(output.classes + (helper to JunkCodeComposer.sharedHelper(helper, index.toLong())), unit.layoutName)
            output.classes.forEach { (name, _) ->
                val type = loader.loadClass(name.replace('/', '.'))
                // Resolving all descriptors and methods triggers JVM verification of every method.
                val methods = type.declaredMethods
                if (type.isInterface) return@forEach
                val context = loader.loadClass("android.content.Context").getConstructor().newInstance()
                val instance = if (output.roles[name] == "customView") {
                    val contextClass = context.javaClass
                    val attributesClass = loader.loadClass("android.util.AttributeSet")
                    type.getConstructor(contextClass, attributesClass).newInstance(context, null)
                    type.getConstructor(contextClass).newInstance(context)
                } else type.getConstructor().newInstance()
                methods.forEach { method ->
                    method.isAccessible = true
                    repeat(3) { sample ->
                        val arguments = method.parameterTypes.map { argument ->
                            when (argument) {
                                Int::class.javaPrimitiveType -> listOf(Int.MIN_VALUE, 0, Int.MAX_VALUE)[sample]
                                Long::class.javaPrimitiveType -> listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE)[sample]
                                Boolean::class.javaPrimitiveType -> sample != 0
                                String::class.java -> listOf(null, "", "  Sample 4  ".repeat(128))[sample]
                                List::class.java -> listOf(null, emptyList<Any?>(), listOf(null, "abc", 4, ""))[sample]
                                else -> if (argument == type && sample > 0) instance else null
                            }
                        }.toTypedArray()
                        try {
                            method.invoke(instance, *arguments)
                        } catch (failure: ReflectiveOperationException) {
                            throw AssertionError("$name.${method.name} failed for sample $sample", failure)
                        }
                    }
                }
            }
        }
    }

    @Test fun fragmentFreePolicyContainsNoAndroidXReferences() {
        val unit = JunkCodeUnit(0, "com/fixture/Page", "page", 19, emptyList())
        val output = JunkCodeComposer.compose(unit, "com.fixture", JunkGenerationPolicy(seed = 19, enableFragments = false))
        assertEquals(0, output.fragmentCount)
        assertTrue(output.classes.values.none { it.toString(Charsets.ISO_8859_1).contains("androidx/") })
    }

    @Test fun modulesSharingACodePrefixKeepAssociatedClassNamesIsolated() {
        val policy = JunkGenerationPolicy(seed = 19)
        val first = JunkCodeComposer.compose(JunkCodeUnit(0, "com/fixture/Aseed1Activity", "page_1", 19, emptyList()), "com.fixture.resources1", policy)
        val second = JunkCodeComposer.compose(JunkCodeUnit(0, "com/fixture/Aseed2Activity", "page_2", 19, emptyList()), "com.fixture.resources2", policy)
        assertTrue(first.classes.keys.intersect(second.classes.keys).isEmpty())
    }

    @Test fun zeroAssociatedClassesStillCreatesAnExecutableActivity() {
        for (fragmentsEnabled in listOf(false, true)) {
            val policy = JunkGenerationPolicy(seed = 23, minAssociatedClasses = 0, maxAssociatedClasses = 0,
                minMethodsPerClass = 1, maxMethodsPerClass = 11, enableFragments = fragmentsEnabled)
            val unit = JunkCodeUnit(0, "com/fixture/StandaloneActivity", "standalone", policy.seedFor(0), emptyList())
            val output = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            assertEquals(setOf(unit.activityName), output.classes.keys)
            assertEquals(listOf("activity"), output.roles.values.toList())
            assertEquals(0, output.fragmentCount)
            assertTrue(output.customViews.isEmpty())
            val loader = FixtureLoader(output.classes, unit.layoutName)
            val type = loader.loadClass(unit.activityName.replace('/', '.'))
            val activity = type.getConstructor().newInstance()
            val onCreate = type.getDeclaredMethod("onCreate", loader.loadClass("android.os.Bundle"))
            onCreate.isAccessible = true
            onCreate.invoke(activity, null)
            assertTrue(methodCount(output.classes.getValue(unit.activityName)) in 3..11)
        }
    }

    @Test fun allRolesRespectMethodMaximumWhenMinimumIsOne() {
        val policy = JunkGenerationPolicy(seed = 611, minAssociatedClasses = 0, maxAssociatedClasses = 32,
            minMethodsPerClass = 1, maxMethodsPerClass = 11, maxMethodOperations = 128, maxFragmentsPerActivity = 4)
        val seenRoles = mutableSetOf<String>()
        val seenFragmentCounts = mutableSetOf<Int>()
        repeat(64) { index ->
            val output = JunkCodeComposer.compose(JunkCodeUnit(index, "com/fixture/Bounded$index", "bounded_$index",
                policy.seedFor(index), emptyList()), "com.fixture.resources", policy)
            assertTrue(output.classes.size in 1..33)
            seenRoles += output.roles.values
            seenFragmentCounts += output.fragmentCount
            output.classes.forEach { (name, bytes) ->
                assertTrue(methodCount(bytes) in 1..11, "$name exceeds the actual declared method limit")
                assertTrue(bytes.size <= policy.maxClassBytes)
            }
        }
        assertTrue(seenRoles.containsAll(listOf("dto", "customView", "fragment", "interface", "implementation")))
        assertTrue(seenFragmentCounts.containsAll((0..4).toList()))
    }

    @Test fun defaultMethodAndAssociatedClassRangesAreHonored() {
        val policy = JunkGenerationPolicy(seed = 919)
        repeat(32) { index ->
            val output = JunkCodeComposer.compose(JunkCodeUnit(index, "com/fixture/Default$index", "default_$index",
                policy.seedFor(index), emptyList()), "com.fixture.resources", policy)
            assertTrue(output.classes.size in (policy.minAssociatedClasses + 1)..(policy.maxAssociatedClasses + 1))
            output.classes.forEach { (name, bytes) ->
                // Contracts deliberately expose only their two typed operations; implementations
                // and concrete classes carry the configured amount of generated method bodies.
                if (output.roles[name] == "interface") assertEquals(2, methodCount(bytes))
                else assertTrue(methodCount(bytes) in policy.minMethodsPerClass..policy.maxMethodsPerClass)
            }
        }
    }

    @Test fun weightsSelectOnlyEnabledOptionalRolesAndAreReportedInSortedOrder() {
        val policy = JunkGenerationPolicy(seed = 719, minAssociatedClasses = 4, maxAssociatedClasses = 4,
            enableFragments = false, associatedRoleWeights = linkedMapOf("utility" to 0, "dto" to 1))
        repeat(8) { index ->
            val output = JunkCodeComposer.compose(JunkCodeUnit(index, "com/fixture/Weighted$index", "weighted_$index",
                policy.seedFor(index), emptyList()), "com.fixture.resources", policy)
            assertEquals(mapOf("activity" to 1, "utility" to 1, "dto" to 3), output.roles.values.groupingBy { it }.eachCount())
        }
        assertEquals(listOf("dto", "utility"), (policy.snapshot()["associatedRoleWeights"] as Map<*, *>).keys.toList())
        for (invalid in listOf(emptyMap(), mapOf("dto" to 0), mapOf("dto" to -1), mapOf("dto" to 101), mapOf("activity" to 1))) {
            assertFailsWith<IllegalArgumentException> { policy.copy(associatedRoleWeights = invalid) }
        }
    }

    @Test fun defaultWeightsPreservePreWeightClassBytesAndIgnoreMapInsertionOrder() {
        val policy = JunkGenerationPolicy(seed = 57)
        val reordered = policy.copy(associatedRoleWeights = policy.associatedRoleWeights.entries.reversed().associate { it.toPair() })
        val digest = MessageDigest.getInstance("SHA-256")
        var classes = 0
        repeat(64) { index ->
            val unit = JunkCodeUnit(index, "com/fixture/code/Activity$index", "page_$index", policy.seedFor(index), listOf("com/fixture/shared/MathOps"))
            val original = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            val fromReordered = JunkCodeComposer.compose(unit, "com.fixture.resources", reordered)
            original.classes.toSortedMap().forEach { (name, bytes) ->
                assertContentEquals(bytes, fromReordered.classes.getValue(name))
                digest.update(name.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(bytes)
                digest.update(0.toByte())
                classes++
            }
        }
        assertEquals(483, classes)
        // Captured from these exact 64 units before weighted selection was introduced.
        assertEquals("9776dea41040ca2230b7294edc97ad448d9f07e3e23340ae5bb3d94184290db7",
            digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun methodCount(bytes: ByteArray): Int {
        var count = 0
        ClassReader(bytes).accept(object : ClassVisitor(ASM9) {
            override fun visitMethod(access: Int, name: String?, descriptor: String?, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                count++
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
        return count
    }

    private class FixtureLoader(private val generated: Map<String, ByteArray>, private val layout: String) : ClassLoader(JunkCodeComposerTest::class.java.classLoader) {
        override fun findClass(name: String): Class<*> {
            val internal = name.replace('.', '/')
            val bytes = generated[internal] ?: stub(internal)
            return defineClass(name, bytes, 0, bytes.size)
        }

        private fun stub(name: String): ByteArray {
            val parent = when (name) {
                "android/app/Activity", "android/view/View" -> if (name.endsWith("Activity")) "android/content/Context" else "java/lang/Object"
                "androidx/fragment/app/FragmentActivity" -> "android/app/Activity"
                else -> "java/lang/Object"
            }
            require(name.startsWith("android/") || name.startsWith("androidx/") || name == "com/fixture/resources/R\$layout") { "Unexpected missing generated reference $name" }
            val writer = ClassWriter(ClassWriter.COMPUTE_MAXS)
            writer.visit(V1_8, ACC_PUBLIC or ACC_SUPER, name, null, parent, null)
            fun method(access: Int, methodName: String, descriptor: String, body: MethodVisitor.() -> Unit) {
                val mv = writer.visitMethod(access, methodName, descriptor, null, null)
                mv.visitCode(); mv.body(); mv.visitMaxs(0, 0); mv.visitEnd()
            }
            method(ACC_PUBLIC, "<init>", "()V") {
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false)
                visitInsn(RETURN)
            }
            fun noop(methodName: String, descriptor: String, access: Int = ACC_PUBLIC) = method(access, methodName, descriptor) { visitInsn(RETURN) }
            fun objectReturn(methodName: String, result: String, descriptor: String = "()L$result;") = method(ACC_PUBLIC, methodName, descriptor) {
                visitTypeInsn(NEW, result); visitInsn(DUP)
                visitMethodInsn(INVOKESPECIAL, result, "<init>", "()V", false)
                visitInsn(ARETURN)
            }
            when (name) {
                "com/fixture/resources/R\$layout" -> writer.visitField(ACC_PUBLIC or ACC_STATIC, layout, "I", null, null).visitEnd()
                "android/app/Activity" -> {
                    noop("onCreate", "(Landroid/os/Bundle;)V", ACC_PROTECTED)
                    noop("onResume", "()V", ACC_PROTECTED)
                    noop("setContentView", "(I)V")
                }
                "androidx/fragment/app/FragmentActivity" -> objectReturn("getSupportFragmentManager", "androidx/fragment/app/FragmentManager")
                "androidx/fragment/app/Fragment" -> noop("onCreate", "(Landroid/os/Bundle;)V")
                "androidx/fragment/app/FragmentManager" -> objectReturn("beginTransaction", "androidx/fragment/app/FragmentTransaction")
                "androidx/fragment/app/FragmentTransaction" -> {
                    method(ACC_PUBLIC, "add", "(Landroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;") { visitVarInsn(ALOAD, 0); visitInsn(ARETURN) }
                    method(ACC_PUBLIC, "commit", "()I") { visitInsn(ICONST_0); visitInsn(IRETURN) }
                }
                "android/view/View" -> {
                    listOf("(Landroid/content/Context;)V", "(Landroid/content/Context;Landroid/util/AttributeSet;)V").forEach { descriptor ->
                        method(ACC_PUBLIC, "<init>", descriptor) {
                            visitVarInsn(ALOAD, 0); visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false); visitInsn(RETURN)
                        }
                    }
                    noop("onDraw", "(Landroid/graphics/Canvas;)V", ACC_PROTECTED)
                    noop("setMeasuredDimension", "(II)V", ACC_PROTECTED)
                    method(ACC_PUBLIC or ACC_STATIC, "resolveSize", "(II)I") { visitVarInsn(ILOAD, 0); visitInsn(IRETURN) }
                }
                "android/graphics/Canvas" -> noop("drawColor", "(I)V")
            }
            writer.visitEnd()
            return writer.toByteArray()
        }
    }
}
