package org.tool.kit.tests.data

import java.security.MessageDigest
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.*
import org.tool.kit.data.generator.JunkGenerationProfile.Calls
import org.tool.kit.data.generator.JunkGenerationProfile.Input
import org.tool.kit.data.generator.JunkGenerationProfile.Program
import org.tool.kit.data.generator.JunkGenerationProfile.Roles
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
            assertUnitExecutes(index, JunkGenerationPolicy(seed = index.toLong(), enableFragments = index % 2 == 0))
        }
    }

    @Test fun projectProfilesVerifyAndExecuteAcrossDimensions() {
        for (id in 0 until JunkGenerationProfile.CAPACITY step 7) {
            assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(), enableFragments = id % 2 == 0,
                profile = JunkGenerationProfile(id)))
        }
    }

    @Test fun programSpecificStepsExecuteWithShortOperationBudgets() {
        for (id in 16..19) {
            for (budget in listOf(1, 2, 6)) {
                assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(), maxMethodOperations = budget,
                    profile = JunkGenerationProfile(id, generatorVersion = 12)))
            }
        }
    }

    @Test fun topologyIntStepsExecuteWithEvenAndOddOperationCounts() {
        for (calls in Calls.entries) {
            for (budget in 1..3) {
                val id = Input.PAIR.ordinal * 16 + calls.ordinal * 4 + Program.BRANCHING.ordinal
                assertUnitExecutes(id, JunkGenerationPolicy(seed = 31, maxMethodOperations = budget,
                    profile = JunkGenerationProfile(id, 26)))
            }
        }
    }

    @Test fun branchingTextExecutesAcrossInputsAndTopologies() {
        val samples = listOf(null, "", "Sample 4  ".repeat(128), "single", " leading", "trailing ",
            "left rightmost", "first second_x")
        for (id in 1..61 step 4) {
            assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(), maxMethodOperations = 1,
                profile = JunkGenerationProfile(id, generatorVersion = 13)), samples)
        }
    }

    @Test fun profiledLongAndPredicateMethodsExecuteAcrossRolesAndTopologies() {
        val samples = listOf(null, "", "Sample 4  ".repeat(128), "=value", " ".repeat(128),
            "first second_x", "x".repeat(256), "x".repeat(257))
        for (role in Roles.entries) {
            for (calls in Calls.entries) {
                val id = role.ordinal * 64 + Input.TEXT.ordinal * 16 + calls.ordinal * 4 + Program.BRANCHING.ordinal
                assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(),
                    profile = JunkGenerationProfile(id, generatorVersion = 14)), samples)
            }
        }
    }

    @Test fun longMethodsExecuteAcrossProgramTypesAndTopologies() {
        for (calls in Calls.entries) {
            for (program in Program.entries) {
                val id = Input.WIDE.ordinal * 16 + calls.ordinal * 4 + program.ordinal
                assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(),
                    profile = JunkGenerationProfile(id, generatorVersion = 15)))
            }
        }
    }

    @Test fun textTransformsExecuteAcrossInputsAndTopologies() {
        val samples = listOf(null, "", "Sample 4  ".repeat(128), "single", " leading", "trailing ",
            "first  second_part", " part_with spaces ", " ".repeat(192))
        for (input in Input.entries) {
            for (calls in Calls.entries) {
                val id = input.ordinal * 16 + calls.ordinal * 4 + Program.TEXT.ordinal
                assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(), maxMethodOperations = 1,
                    profile = JunkGenerationProfile(id, generatorVersion = 16)), samples)
            }
        }
    }

    @Test fun longOperandsExecuteAcrossInputsAndPrograms() {
        for (input in Input.entries) {
            for (program in Program.entries) {
                val id = Roles.RULES.ordinal * 64 + input.ordinal * 16 + Calls.TREE.ordinal * 4 + program.ordinal
                assertUnitExecutes(id, JunkGenerationPolicy(seed = id.toLong(),
                    profile = JunkGenerationProfile(id, generatorVersion = 17)))
            }
        }
    }

    @Test fun profiledListFiltersApplyProgramRulesInTraversalOrder() {
        val padded = "  abcdefghij  "
        val clean = padded.trim()
        val long = "x".repeat(129)
        val punctuated = "_abcdefghij"
        val items = listOf(padded, clean, long, punctuated, "", null, 7)
        for (calls in Calls.entries) {
            for (program in Program.entries) {
                val id = Roles.COLLECTIONS.ordinal * 64 + calls.ordinal * 4 + program.ordinal
                val policy = JunkGenerationPolicy(seed = 31, minAssociatedClasses = 1, maxAssociatedClasses = 1,
                    enableFragments = false, profile = JunkGenerationProfile(id, 18))
                val unit = JunkCodeUnit(0, "com/fixture/FilterActivity", "filters", policy.seedFor(0), emptyList())
                val composed = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
                val collectionName = composed.roles.entries.single { it.value == "collection" }.key
                val type = FixtureLoader(composed.classes, unit.layoutName).loadClass(collectionName.replace('/', '.'))
                val select = type.getMethod("selectStrings", List::class.java)
                val instance = type.getConstructor().newInstance()
                val accepted = when (program) {
                    Program.ARITHMETIC -> listOf(padded, clean, long, punctuated)
                    Program.BRANCHING -> listOf(clean, long)
                    Program.ACCUMULATION -> listOf(padded, clean, punctuated)
                    Program.TEXT -> listOf(clean, clean, punctuated)
                }
                val input = items.toMutableList()
                // INTEGER input with PAIRS topology uses backward traversal.
                assertEquals(if (calls == Calls.PAIRS) accepted.reversed() else accepted, select.invoke(instance, input))
                assertEquals(items, input)
                assertEquals(emptyList<String>(), select.invoke(instance, null))
            }
        }
    }

    @Test fun branchingTextTransformsAndPredicatesUseTheValueOrFallBackToTheKey() {
        for (calls in Calls.entries) {
            val id = Roles.RULES.ordinal * 64 + Input.TEXT.ordinal * 16 + calls.ordinal * 4 + Program.BRANCHING.ordinal
            val policy = JunkGenerationPolicy(seed = 31, maxMethodOperations = 1, minAssociatedClasses = 1,
                maxAssociatedClasses = 1, enableFragments = false, profile = JunkGenerationProfile(id, 25))
            val unit = JunkCodeUnit(0, "com/fixture/FieldActivity", "fields", policy.seedFor(0), emptyList())
            val composed = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
            val validator = composed.roles.entries.single { it.value == "validator" }.key
            val type = FixtureLoader(composed.classes, unit.layoutName).loadClass(validator.replace('/', '.'))
            val instance = type.getConstructor().newInstance()
            val samples = mapOf("longKey \t7x\t" to "7x", "longKey \t" to "longKey",
                "longKey " to "longKey", "\t7x\t" to "7x", " \t" to "")
            val inputs = if (calls == Calls.FAN_OUT) samples + mapOf("_7x" to "7x", "longKey_" to "longKey") else samples
            for (name in listOf("describe", "accepts")) {
                val method = type.getMethod(name, String::class.java)
                for ((input, selected) in inputs) {
                    assertEquals(method.invoke(instance, selected), method.invoke(instance, input), "$calls $name($input)")
                }
            }
        }
    }

    @Test fun modelCountsApplyTheProgramRuleAndCountNonStringObjects() {
        val ineligible = mapOf(Program.BRANCHING to "_bad", Program.ACCUMULATION to "x".repeat(129), Program.TEXT to "   ")
        for ((program, rejected) in ineligible) {
            val verified = (0L..31L).any { seed ->
                val policy = JunkGenerationPolicy(seed = seed, maxMethodOperations = 1,
                    minAssociatedClasses = 2, maxAssociatedClasses = 2, enableFragments = false,
                    associatedRoleWeights = mapOf("collection" to 1), profile = JunkGenerationProfile(program.ordinal, 26))
                val unit = JunkCodeUnit(0, "com/fixture/CountActivity", "counts", policy.seedFor(0), emptyList())
                val composed = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
                val collection = composed.roles.entries.single { it.value == "collection" }.key
                val type = FixtureLoader(composed.classes, unit.layoutName).loadClass(collection.replace('/', '.'))
                val instance = type.getConstructor().newInstance()
                val summarize = type.getMethod("summarize", List::class.java)
                val zero = summarize.invoke(instance, emptyList<Any>())
                val one = summarize.invoke(instance, listOf(7))
                val two = summarize.invoke(instance, listOf(7, 8))
                // Select a fixture that takes the non-string count path and distinguishes adjacent counts.
                if (zero == one || one == two) return@any false
                assertEquals(one, summarize.invoke(instance, listOf(7, null, rejected)))
                assertEquals(two, summarize.invoke(instance, listOf(7, "valid")))
                true
            }
            assertTrue(verified, "Could not find a count-sensitive fixture for $program")
        }
    }

    private fun assertUnitExecutes(
        index: Int,
        policy: JunkGenerationPolicy,
        textSamples: List<String?> = listOf(null, "", "Sample 4  ".repeat(128)),
    ) {
        val helpers = if (policy.profile?.usesTypedStages == true) {
            listOf("com/fixture/shared/FirstStage", "com/fixture/shared/SecondStage")
        } else {
            listOf("com/fixture/shared/MathOps")
        }
        val unit = JunkCodeUnit(index, "com/fixture/Activity$index", "page_$index", policy.seedFor(index), helpers)
        val output = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
        val shared = helpers.associateWith { JunkCodeComposer.sharedHelper(it, index.toLong(), policy.profile) }
        val loader = FixtureLoader(output.classes + shared, unit.layoutName)
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
            if (policy.profile?.usesRequiredFieldsOnly == true) {
                val fields = type.declaredFields.map { it.name }
                assertTrue(fields.none { it.startsWith("value") })
                if (output.roles[name] == "dto") {
                    val label = type.getMethod("getValue").invoke(instance).toString()
                    assertEquals(label, type.getMethod("getLabel").invoke(instance))
                } else {
                    assertFalse("label" in fields)
                }
            }
            methods.forEach { method ->
                method.isAccessible = true
                val parameterTypes = method.parameterTypes
                repeat(if (parameterTypes.singleOrNull() == String::class.java) textSamples.size else 3) { sample ->
                    val arguments = parameterTypes.map { argument ->
                        when (argument) {
                            Int::class.javaPrimitiveType -> listOf(Int.MIN_VALUE, 0, Int.MAX_VALUE)[sample]
                            Long::class.javaPrimitiveType -> listOf(Long.MIN_VALUE, 0L, Long.MAX_VALUE)[sample]
                            Boolean::class.javaPrimitiveType -> sample != 0
                            String::class.java -> textSamples[sample]
                            List::class.java -> listOf(null, emptyList<Any?>(), listOf(null, "abc", 4, ""))[sample]
                            else -> if (argument == type && sample > 0) instance else null
                        }
                    }.toTypedArray()
                    try {
                        method.invoke(instance, *arguments)
                    } catch (failure: ReflectiveOperationException) {
                        throw AssertionError("Profile ${policy.profile?.id}, $name.${method.name} failed for sample $sample", failure)
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

    @Test fun dtoStringAdaptersAreOptInAndRespectTheMethodMinimum() {
        val policy = JunkGenerationPolicy(seed = 24, minAssociatedClasses = 2, maxAssociatedClasses = 2,
            minMethodsPerClass = 11, maxMethodsPerClass = 11, enableFragments = false,
            associatedRoleWeights = mapOf("converter" to 1), profile = JunkGenerationProfile(14, 24))
        val unit = JunkCodeUnit(0, "com/fixture/AdapterActivity", "adapter_screen", policy.seedFor(0), emptyList())
        val legacy = JunkCodeComposer.compose(unit, "com.fixture.resources",
            policy.copy(profile = JunkGenerationProfile(14, 23)))
        val lean = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
        val enabled = JunkCodeComposer.compose(unit, "com.fixture.resources", policy.copy(includeDtoStringAdapters = true))
        val converterName = legacy.roles.entries.single { it.value == "converter" }.key
        val dtoName = legacy.roles.entries.single { it.value == "dto" }.key
        assertEquals(legacy.classes.keys, lean.classes.keys)
        assertEquals(legacy.classes.keys, enabled.classes.keys)
        legacy.classes.forEach { (name, bytes) ->
            assertContentEquals(bytes, enabled.classes.getValue(name))
            if (name != converterName) assertContentEquals(bytes, lean.classes.getValue(name))
        }
        val leanConverter = lean.classes.getValue(converterName)
        assertEquals(policy.minMethodsPerClass, methodCount(leanConverter))
        val leanLoader = FixtureLoader(lean.classes, unit.layoutName)
        assertFalse(leanLoader.loadClass(converterName.replace('/', '.')).declaredMethods.any { it.name == "convert" })

        val loader = FixtureLoader(enabled.classes, unit.layoutName)
        val converter = loader.loadClass(converterName.replace('/', '.'))
        val dto = loader.loadClass(dtoName.replace('/', '.'))
        val call = converter.getMethod("convert", dto)
        val adapter = converter.getConstructor().newInstance()
        val value = dto.getConstructor(Int::class.javaPrimitiveType, String::class.java).newInstance(-42, "fixture")
        assertEquals("-42", call.invoke(adapter, value))
        assertEquals("", call.invoke(adapter, null))
    }

    @Test fun dtoComparisonOptInKeepsTheMethodBudgetAndComparesSignedValues() {
        val policy = JunkGenerationPolicy(seed = 24, minAssociatedClasses = 1, maxAssociatedClasses = 1,
            minMethodsPerClass = 11, maxMethodsPerClass = 11, enableFragments = false,
            profile = JunkGenerationProfile(46, 26))
        val unit = JunkCodeUnit(0, "com/fixture/ComparisonActivity", "comparison_screen", policy.seedFor(0), emptyList())
        val lean = JunkCodeComposer.compose(unit, "com.fixture.resources", policy)
        val enabled = JunkCodeComposer.compose(unit, "com.fixture.resources", policy.copy(includeDtoComparison = true))
        val dtoName = lean.roles.entries.single { it.value == "dto" }.key
        assertEquals(11, methodCount(lean.classes.getValue(dtoName)))
        assertEquals(11, methodCount(enabled.classes.getValue(dtoName)))
        val leanType = FixtureLoader(lean.classes, unit.layoutName).loadClass(dtoName.replace('/', '.'))
        assertTrue(leanType.declaredMethods.none { it.name == "compareValue" })

        val dto = FixtureLoader(enabled.classes, unit.layoutName).loadClass(dtoName.replace('/', '.'))
        val comparison = dto.getMethod("compareValue", dto)
        val constructor = dto.getConstructor(Int::class.javaPrimitiveType, String::class.java)
        val small = constructor.newInstance(Int.MIN_VALUE, "small")
        val large = constructor.newInstance(Int.MAX_VALUE, "large")
        assertEquals(-1, comparison.invoke(small, large))
        assertEquals(0, comparison.invoke(small, small))
        assertEquals(1, comparison.invoke(large, small))
        assertEquals(1, comparison.invoke(small, null))
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
