package org.tool.kit.data.generator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import kotlin.random.Random

/** A unit is deterministic independently of the order in which workers execute it. */
internal data class JunkCodeUnit(
    val index: Int,
    val activityName: String,
    val layoutName: String,
    val seed: Long,
    val sharedHelpers: List<String>
)

internal data class JunkComposedUnit(
    val classes: Map<String, ByteArray>,
    val roles: Map<String, String>,
    val customViews: List<String>,
    val fragmentCount: Int
)

/**
 * Builds small typed dependency graphs directly in JVM bytecode. Generated classes deliberately
 * use only Java/Android APIs available at minSdk 21, plus AndroidX Fragment when enabled. Methods
 * are composed from bounded operations instead of copying complete page/class templates.
 *
 * All internal names use JVM slashes; [JunkComposedUnit.customViews] uses XML's dotted spelling.
 * Resource field owners use the resource namespace, which need not equal applicationId or the
 * generated code prefix. R classes are supplied by the consuming Android build, never by this jar.
 */
internal object JunkCodeComposer {
    private enum class Role(val label: String) {
        UTILITY("utility"), DTO("dto"), CONVERTER("converter"), VALIDATOR("validator"),
        COLLECTION("collection"), CONFIG("config"), VIEW("customView"),
        INTERFACE("interface"), IMPLEMENTATION("implementation"), FRAGMENT("fragment")
    }

    private data class Node(val name: String, val role: Role, val contract: String? = null)

    fun compose(unit: JunkCodeUnit, resourceNamespace: String, policy: JunkGenerationPolicy): JunkComposedUnit {
        val random = Random(unit.seed)
        val associatedCount = random.nextInt(policy.minAssociatedClasses, policy.maxAssociatedClasses + 1)
        val nodes = mutableListOf<Node>()
        val base = unit.activityName.substringBeforeLast('/')
        val unitIdentity = unit.activityName.substringAfterLast('/')
        fun name(role: Role): String {
            val depth = random.nextInt(3)
            val path = (0 until depth).joinToString("") { "/p${random.nextInt(17)}" }
            return "$base$path/${role.name.lowercase().replaceFirstChar(Char::uppercase)}${unitIdentity}_${nodes.size}"
        }
        // One ordinary object ensures that every Activity has a real, typed helper call.
        if (associatedCount > 0) nodes += Node(name(Role.UTILITY), Role.UTILITY)
        val fragmentCount = if (policy.enableFragments) {
            random.nextInt(0, minOf(policy.maxFragmentsPerActivity, (associatedCount - nodes.size).coerceAtLeast(0)) + 1)
        } else 0
        repeat(fragmentCount) { nodes += Node(name(Role.FRAGMENT), Role.FRAGMENT) }
        val candidates = listOf(Role.UTILITY, Role.DTO, Role.CONVERTER, Role.VALIDATOR,
            Role.COLLECTION, Role.CONFIG, Role.VIEW, Role.INTERFACE)
        val totalWeight = candidates.sumOf { policy.associatedRoleWeights[it.label] ?: 0 }
        while (nodes.size < associatedCount) {
            // Keep one draw and the original candidate order: the all-one defaults consume the
            // exact same random stream as the original candidates.random(random) selection.
            var remainingWeight = random.nextInt(totalWeight)
            val role = candidates.first {
                remainingWeight -= policy.associatedRoleWeights[it.label] ?: 0
                remainingWeight < 0
            }
            if (role == Role.INTERFACE && nodes.size + 2 <= associatedCount) {
                val contract = name(role)
                nodes += Node(contract, role)
                nodes += Node(name(Role.IMPLEMENTATION), Role.IMPLEMENTATION, contract)
            } else {
                val resolved = if (role == Role.INTERFACE) Role.DTO else role
                nodes += Node(name(resolved), resolved)
            }
        }
        val classes = linkedMapOf<String, ByteArray>()
        val roles = linkedMapOf<String, String>()
        fun publish(name: String, role: String, bytes: ByteArray) {
            require(bytes.size <= policy.maxClassBytes) { "Generated class $name exceeds ${policy.maxClassBytes} bytes" }
            check(classes.put(name, bytes) == null) { "Duplicate class $name" }
            roles[name] = role
        }
        nodes.forEach { node ->
            publish(node.name, node.role.label, when (node.role) {
                Role.INTERFACE -> contract(node.name)
                Role.VIEW -> customView(node, random, policy, unit.sharedHelpers.randomOrNull(random))
                Role.FRAGMENT -> fragment(node, nodes, random, policy, unit.sharedHelpers.randomOrNull(random))
                else -> ordinary(node, nodes, random, policy, unit.sharedHelpers.randomOrNull(random))
            })
        }
        publish(unit.activityName, "activity", activity(unit, resourceNamespace, nodes, random, policy))
        return JunkComposedUnit(classes, roles, nodes.filter { it.role == Role.VIEW }.map { it.name.replace('/', '.') }, fragmentCount)
    }

    fun sharedHelper(name: String, seed: Long): ByteArray {
        val random = Random(seed)
        val writer = newClass(name, "java/lang/Object")
        constructor(writer, "java/lang/Object")
        writer.method(ACC_PUBLIC or ACC_STATIC, "mix", "(I)I") {
            AndroidJunkBytecodeInject.composeInt(this, random, 0, 1, 2, 8)
            visitVarInsn(ILOAD, 1)
            visitInsn(IRETURN)
        }
        return finish(writer)
    }

    private fun contract(name: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(V1_8, ACC_PUBLIC or ACC_ABSTRACT or ACC_INTERFACE, name, null, "java/lang/Object", null)
        writer.visitMethod(ACC_PUBLIC or ACC_ABSTRACT, "calculate", "(I)I", null, null).visitEnd()
        writer.visitMethod(ACC_PUBLIC or ACC_ABSTRACT, "describe", "(Ljava/lang/String;)Ljava/lang/String;", null, null).visitEnd()
        return finish(writer)
    }

    private fun ordinary(node: Node, nodes: List<Node>, random: Random, policy: JunkGenerationPolicy, shared: String?): ByteArray {
        val writer = newClass(node.name, "java/lang/Object", node.contract)
        val fields = stateFields(writer, random)
        stateConstructor(writer, node.name, "java/lang/Object", fields, random)
        calculate(writer, node.name, random, policy, shared)
        textMethod(writer, "describe", random, policy)
        var mandatory = 3
        when (node.role) {
            Role.DTO -> { dto(writer, node.name); mandatory += 7 }
            Role.CONVERTER -> {
                longMethod(writer, "widen", random)
                nodes.firstOrNull { it.role == Role.DTO }?.let { dto ->
                    writer.method(ACC_PUBLIC, "convert", "(L${dto.name};)Ljava/lang/String;") {
                        val present = Label()
                        visitVarInsn(ALOAD, 1)
                        visitJumpInsn(IFNONNULL, present)
                        visitLdcInsn("")
                        visitInsn(ARETURN)
                        visitLabel(present)
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, dto.name, "getValue", "()I", false)
                        visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false)
                        visitInsn(ARETURN)
                    }
                    mandatory++
                }
                mandatory++
            }
            Role.VALIDATOR -> { booleanMethod(writer, "accepts", random); mandatory++ }
            Role.COLLECTION -> {
                collectionMethod(writer, "summarize", random, policy)
                selectStrings(writer, random)
                mandatory += 2
            }
            Role.CONFIG -> {
                writer.visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "LIMIT", "I", null, random.nextInt(4, 33)).visitEnd()
                booleanMethod(writer, "enabledFor", random)
                mandatory++
            }
            else -> Unit
        }
        val peers = nodes.filter { it.name != node.name && it.role !in setOf(Role.VIEW, Role.FRAGMENT, Role.INTERFACE) }
        peers.randomOrNull(random)?.let { peer ->
            writer.method(ACC_PUBLIC, "combine", "(L${peer.name};I)I") {
                val absent = Label()
                visitVarInsn(ALOAD, 1)
                visitJumpInsn(IFNULL, absent)
                visitVarInsn(ALOAD, 1)
                visitVarInsn(ILOAD, 2)
                visitMethodInsn(INVOKEVIRTUAL, peer.name, "calculate", "(I)I", false)
                visitVarInsn(ISTORE, 2)
                visitLabel(absent)
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ILOAD, 2)
                visitMethodInsn(INVOKEVIRTUAL, node.name, "calculate", "(I)I", false)
                visitInsn(IRETURN)
            }
            mandatory++
        }
        extraMethods(writer, node.name, random, policy, shared, mandatory)
        return finish(writer)
    }

    private fun activity(unit: JunkCodeUnit, namespace: String, nodes: List<Node>, random: Random, policy: JunkGenerationPolicy): ByteArray {
        val parent = if (policy.enableFragments) "androidx/fragment/app/FragmentActivity" else "android/app/Activity"
        val writer = newClass(unit.activityName, parent)
        val fields = stateFields(writer, random)
        stateConstructor(writer, unit.activityName, parent, fields, random)
        val helpers = nodes.filter { it.role !in setOf(Role.INTERFACE, Role.FRAGMENT, Role.VIEW) }.shuffled(random)
        val fragments = nodes.filter { it.role == Role.FRAGMENT }
        calculate(writer, unit.activityName, random, policy, unit.sharedHelpers.randomOrNull(random))
        writer.method(ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKESPECIAL, parent, "onCreate", "(Landroid/os/Bundle;)V", false)
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETSTATIC, namespace.replace('.', '/') + "/R\$layout", unit.layoutName, "I")
            visitMethodInsn(INVOKEVIRTUAL, unit.activityName, "setContentView", "(I)V", false)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 0)
            visitLdcInsn(random.nextInt(1, 128))
            visitMethodInsn(INVOKEVIRTUAL, unit.activityName, "calculate", "(I)I", false)
            visitFieldInsn(PUTFIELD, unit.activityName, "state", "I")
            helpers.take(if (helpers.isEmpty()) 0 else random.nextInt(1, minOf(helpers.size, 3) + 1)).forEach { helper ->
                visitVarInsn(ALOAD, 0)
                newObject(helper.name)
                visitVarInsn(ALOAD, 0)
                visitFieldInsn(GETFIELD, unit.activityName, "state", "I")
                visitMethodInsn(if (helper.contract == null) INVOKEVIRTUAL else INVOKEINTERFACE,
                    helper.contract ?: helper.name, "calculate", "(I)I", helper.contract != null)
                visitFieldInsn(PUTFIELD, unit.activityName, "state", "I")
            }
            if (fragments.isNotEmpty()) {
                val restored = Label()
                visitVarInsn(ALOAD, 1)
                visitJumpInsn(IFNONNULL, restored)
                fragments.shuffled(random).forEach { fragment ->
                    visitVarInsn(ALOAD, 0)
                    visitMethodInsn(INVOKEVIRTUAL, unit.activityName, "getSupportFragmentManager", "()Landroidx/fragment/app/FragmentManager;", false)
                    visitMethodInsn(INVOKEVIRTUAL, "androidx/fragment/app/FragmentManager", "beginTransaction", "()Landroidx/fragment/app/FragmentTransaction;", false)
                    newObject(fragment.name)
                    visitLdcInsn(fragment.name.replace('/', '.'))
                    visitMethodInsn(INVOKEVIRTUAL, "androidx/fragment/app/FragmentTransaction", "add", "(Landroidx/fragment/app/Fragment;Ljava/lang/String;)Landroidx/fragment/app/FragmentTransaction;", false)
                    visitMethodInsn(INVOKEVIRTUAL, "androidx/fragment/app/FragmentTransaction", "commit", "()I", false)
                    visitInsn(POP)
                }
                visitLabel(restored)
            }
            visitInsn(RETURN)
        }
        var mandatory = 3
        if (random.nextBoolean()) {
            writer.method(ACC_PROTECTED, "onResume", "()V") {
                visitVarInsn(ALOAD, 0)
                visitMethodInsn(INVOKESPECIAL, parent, "onResume", "()V", false)
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ALOAD, 0)
                visitFieldInsn(GETFIELD, unit.activityName, "state", "I")
                visitLdcInsn(0x7fff)
                visitInsn(IAND)
                visitFieldInsn(PUTFIELD, unit.activityName, "state", "I")
                visitInsn(RETURN)
            }
            mandatory++
        }
        extraMethods(writer, unit.activityName, random, policy, unit.sharedHelpers.randomOrNull(random), mandatory)
        return finish(writer)
    }

    private fun fragment(node: Node, nodes: List<Node>, random: Random, policy: JunkGenerationPolicy, shared: String?): ByteArray {
        val parent = "androidx/fragment/app/Fragment"
        val writer = newClass(node.name, parent)
        val fields = stateFields(writer, random)
        stateConstructor(writer, node.name, parent, fields, random)
        calculate(writer, node.name, random, policy, shared)
        val helper = nodes.filter { it.role !in setOf(Role.FRAGMENT, Role.INTERFACE, Role.VIEW) }.randomOrNull(random)
        writer.method(ACC_PUBLIC, "onCreate", "(Landroid/os/Bundle;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKESPECIAL, parent, "onCreate", "(Landroid/os/Bundle;)V", false)
            visitVarInsn(ALOAD, 0)
            if (helper != null) {
                newObject(helper.name)
                visitLdcInsn(random.nextInt(128))
                visitMethodInsn(INVOKEVIRTUAL, helper.name, "calculate", "(I)I", false)
            } else {
                visitVarInsn(ALOAD, 0)
                visitLdcInsn(random.nextInt(128))
                visitMethodInsn(INVOKEVIRTUAL, node.name, "calculate", "(I)I", false)
            }
            visitFieldInsn(PUTFIELD, node.name, "state", "I")
            visitInsn(RETURN)
        }
        extraMethods(writer, node.name, random, policy, shared, 3)
        return finish(writer)
    }

    private fun customView(node: Node, random: Random, policy: JunkGenerationPolicy, shared: String?): ByteArray {
        val parent = "android/view/View"
        val writer = newClass(node.name, parent)
        stateFields(writer, random)
        listOf("(Landroid/content/Context;)V", "(Landroid/content/Context;Landroid/util/AttributeSet;)V").forEachIndexed { index, descriptor ->
            writer.method(ACC_PUBLIC, "<init>", descriptor) {
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ALOAD, 1)
                if (index == 1) visitVarInsn(ALOAD, 2)
                visitMethodInsn(INVOKESPECIAL, parent, "<init>", descriptor, false)
                visitVarInsn(ALOAD, 0)
                visitLdcInsn(random.nextInt(128))
                visitFieldInsn(PUTFIELD, node.name, "state", "I")
                visitInsn(RETURN)
            }
        }
        calculate(writer, node.name, random, policy, shared)
        val desiredWidth = random.nextInt(48, 193)
        val desiredHeight = random.nextInt(24, 97)
        writer.method(ACC_PROTECTED, "onMeasure", "(II)V") {
            visitVarInsn(ALOAD, 0)
            visitLdcInsn(desiredWidth)
            visitVarInsn(ILOAD, 1)
            visitMethodInsn(INVOKESTATIC, parent, "resolveSize", "(II)I", false)
            visitLdcInsn(desiredHeight)
            visitVarInsn(ILOAD, 2)
            visitMethodInsn(INVOKESTATIC, parent, "resolveSize", "(II)I", false)
            visitMethodInsn(INVOKEVIRTUAL, node.name, "setMeasuredDimension", "(II)V", false)
            visitInsn(RETURN)
        }
        writer.method(ACC_PROTECTED, "onDraw", "(Landroid/graphics/Canvas;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKESPECIAL, parent, "onDraw", "(Landroid/graphics/Canvas;)V", false)
            // Drawing is bounded and local to this View; no allocations or generated methods run per frame.
            val skip = Label()
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, skip)
            visitVarInsn(ALOAD, 1)
            visitLdcInsn(0xff000000.toInt() or random.nextInt(0x1000000))
            visitMethodInsn(INVOKEVIRTUAL, "android/graphics/Canvas", "drawColor", "(I)V", false)
            visitLabel(skip)
            visitInsn(RETURN)
        }
        extraMethods(writer, node.name, random, policy, shared, 5)
        return finish(writer)
    }

    private fun stateFields(writer: ClassWriter, random: Random): List<Pair<String, String>> {
        writer.visitField(ACC_PRIVATE, "state", "I", null, null).visitEnd()
        writer.visitField(ACC_PRIVATE, "label", "Ljava/lang/String;", null, null).visitEnd()
        val fields = mutableListOf<Pair<String, String>>()
        repeat(random.nextInt(1, 5)) { index ->
            val descriptor = listOf("I", "J", "D", "Z", "Ljava/lang/String;").random(random)
            fields += "value$index" to descriptor
            writer.visitField(ACC_PRIVATE, "value$index", descriptor, null, null).visitEnd()
        }
        return fields
    }

    private fun stateConstructor(writer: ClassWriter, name: String, parent: String, fields: List<Pair<String, String>>, random: Random) {
        writer.method(ACC_PUBLIC, "<init>", "()V") {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false)
            visitVarInsn(ALOAD, 0)
            visitLdcInsn(random.nextInt(1, 256))
            visitFieldInsn(PUTFIELD, name, "state", "I")
            visitVarInsn(ALOAD, 0)
            visitLdcInsn("item${random.nextInt(1000)}")
            visitFieldInsn(PUTFIELD, name, "label", "Ljava/lang/String;")
            fields.shuffled(random).forEach { (field, descriptor) ->
                visitVarInsn(ALOAD, 0)
                when (descriptor) {
                    "J" -> visitLdcInsn(random.nextLong(1, 1024))
                    "D" -> visitLdcInsn(random.nextDouble())
                    "Z" -> visitInsn(if (random.nextBoolean()) ICONST_1 else ICONST_0)
                    "I" -> visitLdcInsn(random.nextInt(1024))
                    else -> visitLdcInsn("v${random.nextInt(256)}")
                }
                visitFieldInsn(PUTFIELD, name, field, descriptor)
            }
            visitInsn(RETURN)
        }
    }

    private fun calculate(writer: ClassWriter, owner: String, random: Random, policy: JunkGenerationPolicy, shared: String?) {
        writer.method(ACC_PUBLIC, "calculate", "(I)I") {
            AndroidJunkBytecodeInject.composeInt(this, random, 1, 2, 3, policy.maxMethodOperations, shared)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 2)
            visitFieldInsn(PUTFIELD, owner, "state", "I")
            visitVarInsn(ILOAD, 2)
            visitInsn(IRETURN)
        }
    }

    private fun dto(writer: ClassWriter, owner: String) {
        writer.method(ACC_PUBLIC, "<init>", "(ILjava/lang/String;)V") {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 1)
            visitFieldInsn(PUTFIELD, owner, "state", "I")
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 2)
            visitFieldInsn(PUTFIELD, owner, "label", "Ljava/lang/String;")
            visitInsn(RETURN)
        }
        writer.method(ACC_PUBLIC, "getValue", "()I") {
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "state", "I")
            visitInsn(IRETURN)
        }
        writer.method(ACC_PUBLIC, "setValue", "(I)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 1)
            visitFieldInsn(PUTFIELD, owner, "state", "I")
            visitInsn(RETURN)
        }
        writer.method(ACC_PUBLIC, "getLabel", "()Ljava/lang/String;") {
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "label", "Ljava/lang/String;")
            visitInsn(ARETURN)
        }
        writer.method(ACC_PUBLIC, "setLabel", "(Ljava/lang/String;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitFieldInsn(PUTFIELD, owner, "label", "Ljava/lang/String;")
            visitInsn(RETURN)
        }
        writer.method(ACC_PUBLIC, "copy", "()L$owner;") {
            visitTypeInsn(NEW, owner)
            visitInsn(DUP)
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "state", "I")
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "label", "Ljava/lang/String;")
            visitMethodInsn(INVOKESPECIAL, owner, "<init>", "(ILjava/lang/String;)V", false)
            visitInsn(ARETURN)
        }
        writer.method(ACC_PUBLIC, "compareValue", "(L$owner;)I") {
            val present = Label()
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNONNULL, present)
            visitInsn(ICONST_1)
            visitInsn(IRETURN)
            visitLabel(present)
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "state", "I")
            visitVarInsn(ALOAD, 1)
            visitFieldInsn(GETFIELD, owner, "state", "I")
            visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "compare", "(II)I", false)
            visitInsn(IRETURN)
        }
    }

    private fun extraMethods(writer: ClassWriter, owner: String, random: Random, policy: JunkGenerationPolicy, shared: String?, mandatory: Int) {
        val target = random.nextInt(policy.minMethodsPerClass, policy.maxMethodsPerClass + 1)
        repeat((target - mandatory).coerceAtLeast(0)) { index ->
            val name = "step${index}_${random.nextInt(0x1000).toString(16)}"
            when (random.nextInt(7)) {
                0 -> writer.method(ACC_PUBLIC, name, "(I)I") {
                    AndroidJunkBytecodeInject.composeInt(this, random, 1, 2, 3, policy.maxMethodOperations, shared)
                    visitVarInsn(ILOAD, 2)
                    visitInsn(IRETURN)
                }
                1 -> writer.method(ACC_PUBLIC, name, "(II)I") {
                    visitVarInsn(ILOAD, 1)
                    visitVarInsn(ILOAD, 2)
                    visitInsn(listOf(IADD, IXOR, ISUB).random(random))
                    visitVarInsn(ISTORE, 3)
                    AndroidJunkBytecodeInject.composeInt(this, random, 3, 4, 5, policy.maxMethodOperations, shared)
                    visitVarInsn(ILOAD, 4)
                    visitInsn(IRETURN)
                }
                2 -> textMethod(writer, name, random, policy)
                3 -> booleanMethod(writer, name, random)
                4 -> collectionMethod(writer, name, random, policy)
                5 -> longMethod(writer, name, random)
                else -> writer.method(ACC_PUBLIC, name, "(Z)I") {
                    val unchanged = Label()
                    visitVarInsn(ALOAD, 0)
                    visitFieldInsn(GETFIELD, owner, "state", "I")
                    visitVarInsn(ISTORE, 2)
                    visitVarInsn(ILOAD, 1)
                    visitJumpInsn(IFEQ, unchanged)
                    AndroidJunkBytecodeInject.composeInt(this, random, 2, 2, 3, policy.maxMethodOperations, shared)
                    visitLabel(unchanged)
                    visitVarInsn(ILOAD, 2)
                    visitInsn(IRETURN)
                }
            }
        }
    }

    private fun textMethod(writer: ClassWriter, name: String, random: Random, policy: JunkGenerationPolicy) {
        writer.method(ACC_PUBLIC, name, "(Ljava/lang/String;)Ljava/lang/String;") {
            val present = Label()
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNONNULL, present)
            visitLdcInsn("")
            visitVarInsn(ASTORE, 1)
            visitLabel(present)
            // Bound work even when the host passes a very large string.
            visitVarInsn(ALOAD, 1)
            visitInsn(ICONST_0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            visitLdcInsn(random.nextInt(32, 193))
            visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
            visitVarInsn(ASTORE, 1)
            val operationBudget = minOf(policy.maxMethodOperations, 10).coerceAtLeast(1)
            repeat(random.nextInt(minOf(3, operationBudget), operationBudget + 1)) {
                when (random.nextInt(9)) {
                    0 -> {
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    1 -> {
                        visitTypeInsn(NEW, "java/lang/StringBuilder")
                        visitInsn(DUP)
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "reverse", "()Ljava/lang/StringBuilder;", false)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    2 -> {
                        visitVarInsn(ALOAD, 1)
                        visitLdcInsn(' '.code)
                        visitLdcInsn('_'.code)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "replace", "(CC)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    3 -> {
                        visitVarInsn(ALOAD, 1)
                        visitLdcInsn("-${random.nextInt(10)}")
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    4 -> {
                        val short = Label()
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                        visitLdcInsn(random.nextInt(2, 33))
                        visitJumpInsn(IF_ICMPLE, short)
                        visitVarInsn(ALOAD, 1)
                        visitInsn(ICONST_1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                        visitLabel(short)
                    }
                    5 -> {
                        val unchanged = Label()
                        visitVarInsn(ALOAD, 1)
                        visitLdcInsn("_")
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false)
                        visitJumpInsn(IFEQ, unchanged)
                        visitVarInsn(ALOAD, 1)
                        visitInsn(ICONST_0)
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                        visitInsn(ICONST_1)
                        visitInsn(ISUB)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                        visitLabel(unchanged)
                    }
                    6 -> {
                        visitTypeInsn(NEW, "java/lang/StringBuilder")
                        visitInsn(DUP)
                        visitLdcInsn(listOf("a", "b", "_").random(random))
                        visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false)
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    7 -> {
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
                        visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                    else -> {
                        visitVarInsn(ALOAD, 1)
                        visitInsn(ICONST_0)
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                        visitLdcInsn(random.nextInt(16, 97))
                        visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
                        visitVarInsn(ASTORE, 1)
                    }
                }
            }
            visitVarInsn(ALOAD, 1)
            visitInsn(ARETURN)
        }
    }

    private fun booleanMethod(writer: ClassWriter, name: String, random: Random) {
        writer.method(ACC_PUBLIC, name, "(Ljava/lang/String;)Z") {
            val accepted = Label()
            val rejected = Label()
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, rejected)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            visitVarInsn(ISTORE, 2)
            visitVarInsn(ILOAD, 2)
            visitJumpInsn(IFEQ, rejected)
            visitVarInsn(ILOAD, 2)
            visitLdcInsn(256)
            visitJumpInsn(IF_ICMPGT, rejected)
            // Composed predicates use short-circuit conjunction or disjunction. Every index is
            // valid after the nonempty guard, and all scans are bounded to 256 characters.
            val allRequired = random.nextBoolean()
            repeat(random.nextInt(3, 11)) {
                when (random.nextInt(9)) {
                    0 -> {
                        visitVarInsn(ILOAD, 2)
                        visitLdcInsn(random.nextInt(1, 65))
                        val pass = Label()
                        val ready = Label()
                        visitJumpInsn(if (random.nextBoolean()) IF_ICMPGE else IF_ICMPLE, pass)
                        visitInsn(ICONST_0)
                        visitJumpInsn(GOTO, ready)
                        visitLabel(pass)
                        visitInsn(ICONST_1)
                        visitLabel(ready)
                    }
                    1, 2 -> {
                        visitVarInsn(ALOAD, 1)
                        if (random.nextBoolean()) visitInsn(ICONST_0) else {
                            visitVarInsn(ILOAD, 2)
                            visitInsn(ICONST_1)
                            visitInsn(ISUB)
                        }
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
                        visitMethodInsn(INVOKESTATIC, "java/lang/Character",
                            listOf("isLetter", "isDigit", "isLetterOrDigit", "isWhitespace", "isUpperCase", "isLowerCase").random(random), "(C)Z", false)
                    }
                    3 -> {
                        visitVarInsn(ALOAD, 1)
                        visitLdcInsn(listOf('a', 'e', '0', '_', ' ').random(random).code)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
                        val pass = Label()
                        val ready = Label()
                        visitJumpInsn(if (random.nextBoolean()) IFGE else IFLT, pass)
                        visitInsn(ICONST_0)
                        visitJumpInsn(GOTO, ready)
                        visitLabel(pass)
                        visitInsn(ICONST_1)
                        visitLabel(ready)
                    }
                    4, 5 -> {
                        visitVarInsn(ALOAD, 1)
                        visitLdcInsn(listOf("a", "x", "_", "0", "ab").random(random))
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", if (random.nextBoolean()) "startsWith" else "endsWith", "(Ljava/lang/String;)Z", false)
                    }
                    6 -> {
                        visitVarInsn(ILOAD, 2)
                        visitInsn(ICONST_1)
                        visitInsn(IAND)
                    }
                    7 -> {
                        visitVarInsn(ALOAD, 1)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
                        visitInsn(ICONST_1)
                        visitInsn(IXOR)
                    }
                    else -> {
                        visitVarInsn(ALOAD, 1)
                        visitVarInsn(ILOAD, 2)
                        visitInsn(ICONST_2)
                        visitInsn(IDIV)
                        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
                        visitMethodInsn(INVOKESTATIC, "java/lang/Character", "isLetterOrDigit", "(C)Z", false)
                    }
                }
                visitJumpInsn(if (allRequired) IFEQ else IFNE, if (allRequired) rejected else accepted)
            }
            visitJumpInsn(GOTO, if (allRequired) accepted else rejected)
            visitLabel(accepted)
            visitInsn(ICONST_1)
            visitInsn(IRETURN)
            visitLabel(rejected)
            visitInsn(ICONST_0)
            visitInsn(IRETURN)
        }
    }

    private fun longMethod(writer: ClassWriter, name: String, random: Random) {
        writer.method(ACC_PUBLIC, name, "(J)J") {
            repeat(random.nextInt(3, 11)) {
                when (random.nextInt(7)) {
                    0 -> {
                        visitVarInsn(LLOAD, 1)
                        visitLdcInsn(random.nextLong(1, 4096))
                        visitInsn(listOf(LADD, LXOR, LMUL, LSUB, LAND, LOR).random(random))
                        visitVarInsn(LSTORE, 1)
                    }
                    1 -> {
                        visitVarInsn(LLOAD, 1)
                        visitVarInsn(LLOAD, 1)
                        visitLdcInsn(random.nextInt(1, 32))
                        visitInsn(LUSHR)
                        visitInsn(LXOR)
                        visitVarInsn(LSTORE, 1)
                    }
                    2 -> {
                        visitVarInsn(LLOAD, 1)
                        visitLdcInsn(random.nextInt(1, 63))
                        visitMethodInsn(INVOKESTATIC, "java/lang/Long", if (random.nextBoolean()) "rotateLeft" else "rotateRight", "(JI)J", false)
                        visitVarInsn(LSTORE, 1)
                    }
                    3 -> {
                        val positive = Label()
                        val done = Label()
                        visitVarInsn(LLOAD, 1)
                        visitInsn(LCONST_0)
                        visitInsn(LCMP)
                        visitJumpInsn(IFGE, positive)
                        visitVarInsn(LLOAD, 1)
                        visitInsn(LNEG)
                        visitVarInsn(LSTORE, 1)
                        visitJumpInsn(GOTO, done)
                        visitLabel(positive)
                        visitVarInsn(LLOAD, 1)
                        visitLdcInsn(random.nextLong(1, 256))
                        visitInsn(LXOR)
                        visitVarInsn(LSTORE, 1)
                        visitLabel(done)
                    }
                    4 -> {
                        visitVarInsn(LLOAD, 1)
                        visitLdcInsn(random.nextLong(2, 4096))
                        visitInsn(LREM)
                        visitVarInsn(LSTORE, 1)
                    }
                    5 -> {
                        val loop = Label()
                        val done = Label()
                        visitInsn(ICONST_0)
                        visitVarInsn(ISTORE, 3)
                        visitLabel(loop)
                        visitVarInsn(ILOAD, 3)
                        visitLdcInsn(random.nextInt(2, 7))
                        visitJumpInsn(IF_ICMPGE, done)
                        visitVarInsn(LLOAD, 1)
                        visitVarInsn(ILOAD, 3)
                        visitInsn(I2L)
                        visitInsn(if (random.nextBoolean()) LADD else LXOR)
                        visitVarInsn(LSTORE, 1)
                        visitIincInsn(3, 1)
                        visitJumpInsn(GOTO, loop)
                        visitLabel(done)
                    }
                    else -> {
                        visitVarInsn(LLOAD, 1)
                        visitMethodInsn(INVOKESTATIC, "java/lang/Long", if (random.nextBoolean()) "bitCount" else "numberOfLeadingZeros", "(J)I", false)
                        visitInsn(I2L)
                        visitVarInsn(LLOAD, 1)
                        visitInsn(LADD)
                        visitVarInsn(LSTORE, 1)
                    }
                }
            }
            visitVarInsn(LLOAD, 1)
            visitInsn(LRETURN)
        }
    }

    private fun collectionMethod(writer: ClassWriter, name: String, random: Random, policy: JunkGenerationPolicy) {
        writer.method(ACC_PUBLIC, name, "(Ljava/util/List;)I") {
            val end = Label()
            val loop = Label()
            val next = Label()
            visitInsn(ICONST_0)
            visitVarInsn(ISTORE, 2)
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, end)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true)
            visitLdcInsn(random.nextInt(4, 17))
            visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
            visitVarInsn(ISTORE, 3)
            visitInsn(ICONST_0)
            visitVarInsn(ISTORE, 4)
            visitLabel(loop)
            visitVarInsn(ILOAD, 4)
            visitVarInsn(ILOAD, 3)
            visitJumpInsn(IF_ICMPGE, end)
            visitVarInsn(ALOAD, 1)
            visitVarInsn(ILOAD, 4)
            visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true)
            visitVarInsn(ASTORE, 5)
            visitVarInsn(ALOAD, 5)
            visitJumpInsn(IFNULL, next)
            // Count or measure only supported values; never call arbitrary host hashCode/toString.
            if (random.nextBoolean()) {
                visitVarInsn(ALOAD, 5)
                visitTypeInsn(INSTANCEOF, "java/lang/String")
                visitJumpInsn(IFEQ, next)
                visitVarInsn(ILOAD, 2)
                visitVarInsn(ALOAD, 5)
                visitTypeInsn(CHECKCAST, "java/lang/String")
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitInsn(IADD)
                visitVarInsn(ISTORE, 2)
            } else {
                visitIincInsn(2, 1)
            }
            visitLabel(next)
            visitIincInsn(4, 1)
            visitJumpInsn(GOTO, loop)
            visitLabel(end)
            AndroidJunkBytecodeInject.composeInt(this, random, 2, 2, 6, minOf(policy.maxMethodOperations, 6))
            visitVarInsn(ILOAD, 2)
            visitInsn(IRETURN)
        }
    }

    /** Return a bounded filtered copy; the input list and its elements are never mutated. */
    private fun selectStrings(writer: ClassWriter, random: Random) {
        writer.method(ACC_PUBLIC, "selectStrings", "(Ljava/util/List;)Ljava/util/List;") {
            val end = Label()
            val loop = Label()
            val next = Label()
            visitTypeInsn(NEW, "java/util/ArrayList")
            visitInsn(DUP)
            visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            visitVarInsn(ASTORE, 2)
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, end)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true)
            visitLdcInsn(random.nextInt(4, 17))
            visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
            visitVarInsn(ISTORE, 3)
            visitInsn(ICONST_0)
            visitVarInsn(ISTORE, 4)
            visitLabel(loop)
            visitVarInsn(ILOAD, 4)
            visitVarInsn(ILOAD, 3)
            visitJumpInsn(IF_ICMPGE, end)
            visitVarInsn(ALOAD, 1)
            visitVarInsn(ILOAD, 4)
            visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true)
            visitVarInsn(ASTORE, 5)
            visitVarInsn(ALOAD, 5)
            visitTypeInsn(INSTANCEOF, "java/lang/String")
            visitJumpInsn(IFEQ, next)
            visitVarInsn(ALOAD, 5)
            visitTypeInsn(CHECKCAST, "java/lang/String")
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            visitLdcInsn(random.nextInt(1, 9))
            visitJumpInsn(IF_ICMPLT, next)
            visitVarInsn(ALOAD, 2)
            visitVarInsn(ALOAD, 5)
            visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
            visitInsn(POP)
            visitLabel(next)
            visitIincInsn(4, 1)
            visitJumpInsn(GOTO, loop)
            visitLabel(end)
            visitVarInsn(ALOAD, 2)
            visitInsn(ARETURN)
        }
    }

    private fun newClass(name: String, parent: String, contract: String? = null): ClassWriter {
        // Frame joins contain primitives or identical declared types. Avoid loading Android classes
        // into this desktop process if ASM asks about a reference merge.
        val writer = object : ClassWriter(COMPUTE_FRAMES or COMPUTE_MAXS) {
            override fun getCommonSuperClass(type1: String, type2: String): String =
                if (type1 == type2) type1 else "java/lang/Object"
        }
        writer.visit(V1_8, ACC_PUBLIC or ACC_SUPER, name, null, parent, contract?.let { arrayOf(it) })
        return writer
    }

    private fun constructor(writer: ClassWriter, parent: String) {
        writer.method(ACC_PUBLIC, "<init>", "()V") {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false)
            visitInsn(RETURN)
        }
    }

    private inline fun ClassWriter.method(access: Int, name: String, descriptor: String, body: MethodVisitor.() -> Unit) {
        val mv = visitMethod(access, name, descriptor, null, null)
        mv.visitCode()
        mv.body()
        mv.visitMaxs(0, 0)
        mv.visitEnd()
    }

    private fun MethodVisitor.newObject(name: String) {
        visitTypeInsn(NEW, name)
        visitInsn(DUP)
        visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false)
    }

    private fun finish(writer: ClassWriter): ByteArray {
        writer.visitEnd()
        return writer.toByteArray()
    }

    private fun <T> List<T>.randomOrNull(random: Random): T? = if (isEmpty()) null else this[random.nextInt(size)]
}
