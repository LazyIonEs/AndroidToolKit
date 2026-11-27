package org.tool.kit.data.generator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.JunkGenerationProfile.Calls
import org.tool.kit.data.generator.JunkGenerationProfile.Input
import org.tool.kit.data.generator.JunkGenerationProfile.Program
import org.tool.kit.data.generator.JunkGenerationProfile.Roles
import org.tool.kit.data.generator.JunkGenerationProfile.State
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

    private enum class ListTraversal { INDEXED, PAIRED, BACKWARD, FORWARD }

    private data class Node(val name: String, val role: Role, val contract: String? = null)
    private data class HelperGraph(val roots: List<Node>, val dependencies: Map<Node, List<Node>>)
    private data class StateFields(val hasLabel: Boolean, val extra: List<Pair<String, String>>)
    private val ordinaryRoles = setOf(Role.UTILITY, Role.DTO, Role.CONVERTER, Role.VALIDATOR,
        Role.COLLECTION, Role.CONFIG, Role.IMPLEMENTATION)

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
        val preferredRoles = when (policy.profile?.roles) {
            Roles.MODELS -> listOf(Role.DTO, Role.CONVERTER)
            Roles.RULES -> listOf(Role.VALIDATOR, Role.INTERFACE)
            Roles.COLLECTIONS -> listOf(Role.COLLECTION, Role.UTILITY)
            Roles.CONFIGURATION -> listOf(Role.CONFIG, Role.VIEW)
            null -> listOf(Role.UTILITY)
        }
        if (associatedCount > 0) {
            val first = preferredRoles.first()
            nodes += Node(name(first), first)
        }
        val fragmentCount = if (policy.enableFragments) {
            random.nextInt(0, minOf(policy.maxFragmentsPerActivity, (associatedCount - nodes.size).coerceAtLeast(0)) + 1)
        } else 0
        repeat(fragmentCount) { nodes += Node(name(Role.FRAGMENT), Role.FRAGMENT) }
        val candidates = listOf(Role.UTILITY, Role.DTO, Role.CONVERTER, Role.VALIDATOR,
            Role.COLLECTION, Role.CONFIG, Role.VIEW, Role.INTERFACE)
        val weights = candidates.associateWith { role ->
            val baseWeight = policy.associatedRoleWeights[role.label] ?: 0
            if (policy.profile != null && role in preferredRoles) baseWeight * 6 else baseWeight
        }
        val totalWeight = weights.values.sum()
        while (nodes.size < associatedCount) {
            // Keep one draw and the original candidate order: the all-one defaults consume the
            // exact same random stream as the original candidates.random(random) selection.
            var remainingWeight = random.nextInt(totalWeight)
            val role = candidates.first {
                remainingWeight -= weights.getValue(it)
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
        val graph = policy.profile?.let { helperGraph(nodes.filter { node -> node.role in ordinaryRoles }, it.calls) }
        val fragmentGraph = if (policy.profile?.usesProfiledComponentCalls == true) graph else null
        val sharedProgram = JunkSharedProgram.forUnit(unit, policy.profile)
        val legacyHelpers = if (sharedProgram == null) unit.sharedHelpers else emptyList()
        fun publish(name: String, role: String, bytes: ByteArray) {
            require(bytes.size <= policy.maxClassBytes) { "Generated class $name exceeds ${policy.maxClassBytes} bytes" }
            check(classes.put(name, bytes) == null) { "Duplicate class $name" }
            roles[name] = role
        }
        nodes.forEach { node ->
            publish(node.name, node.role.label, when (node.role) {
                Role.INTERFACE -> contract(node.name, policy.calculationDescriptor)
                Role.VIEW -> customView(node, random, policy, legacyHelpers.randomOrNull(random), sharedProgram)
                Role.FRAGMENT -> fragment(node, nodes, random, policy, legacyHelpers.randomOrNull(random), fragmentGraph, sharedProgram)
                else -> ordinary(node, nodes, random, policy, legacyHelpers.randomOrNull(random), sharedProgram,
                    graph?.dependencies?.get(node).orEmpty())
            })
        }
        publish(unit.activityName, "activity", activity(unit, resourceNamespace, nodes, random, policy, graph, sharedProgram))
        return JunkComposedUnit(classes, roles, nodes.filter { it.role == Role.VIEW }.map { it.name.replace('/', '.') }, fragmentCount)
    }

    private fun helperGraph(helpers: List<Node>, calls: Calls): HelperGraph {
        val roots = when (calls) {
            Calls.FAN_OUT -> helpers
            Calls.CHAIN, Calls.TREE -> helpers.take(1)
            Calls.PAIRS -> helpers.filterIndexed { index, _ -> index % 2 == 0 }
        }
        // All dependency edges point to a later helper, so compute calls cannot recurse.
        val dependencies = helpers.mapIndexed { index, helper ->
            val targets = when (calls) {
                Calls.FAN_OUT -> emptyList()
                Calls.CHAIN -> listOfNotNull(helpers.getOrNull(index + 1))
                Calls.PAIRS -> if (index % 2 == 0) listOfNotNull(helpers.getOrNull(index + 1)) else emptyList()
                Calls.TREE -> listOfNotNull(helpers.getOrNull(2 * index + 1), helpers.getOrNull(2 * index + 2))
            }
            helper to targets
        }.toMap()
        return HelperGraph(roots, dependencies)
    }

    private val JunkGenerationPolicy.input get() = profile?.input ?: Input.INTEGER
    private val JunkGenerationPolicy.state get() = profile?.state ?: State.SCALAR
    private val JunkGenerationPolicy.calculationResult get() =
        profile?.takeIf { it.usesProgramContracts }?.program?.resultType ?: JunkValueType.NUMBER
    private val JunkGenerationPolicy.calculationRole get() = profile?.takeIf { it.usesRoleCalculationContext }?.roles
    private val JunkGenerationPolicy.calculationTopology get() =
        profile?.takeIf { it.usesTopologyCalculationContext }?.calls?.context ?: JunkTopologyContext.NONE
    private val JunkGenerationPolicy.methodBodyProfile get() = profile?.takeIf { it.usesProfiledMethodBodies }
    private val JunkGenerationPolicy.methodRole get() = profile?.takeIf { it.usesRoleSpecificMethods }?.roles
    private val JunkGenerationPolicy.calculationDescriptor get() =
        "(${input.parameters}${calculationTopology.parameters}${calculationRole?.valueType?.descriptor.orEmpty()})${calculationResult.descriptor}"

    fun sharedHelper(name: String, seed: Long, profile: JunkGenerationProfile? = null): ByteArray {
        val random = Random(seed)
        val writer = newClass(name, "java/lang/Object")
        constructor(writer, "java/lang/Object")
        if (profile?.usesTypedStages == true) {
            JunkSharedProgram.emitMethods(writer, random, profile)
        } else {
            writer.method(ACC_PUBLIC or ACC_STATIC, "mix", "(I)I") {
                AndroidJunkBytecodeInject.composeInt(this, random, 0, 1, 2, 8, program = profile?.program)
                visitVarInsn(ILOAD, 1)
                visitInsn(IRETURN)
            }
        }
        return finish(writer)
    }

    private fun contract(name: String, calculationDescriptor: String): ByteArray {
        val writer = ClassWriter(0)
        writer.visit(V1_8, ACC_PUBLIC or ACC_ABSTRACT or ACC_INTERFACE, name, null, "java/lang/Object", null)
        writer.visitMethod(ACC_PUBLIC or ACC_ABSTRACT, "calculate", calculationDescriptor, null, null).visitEnd()
        writer.visitMethod(ACC_PUBLIC or ACC_ABSTRACT, "describe", "(Ljava/lang/String;)Ljava/lang/String;", null, null).visitEnd()
        return finish(writer)
    }

    private fun ordinary(
        node: Node,
        nodes: List<Node>,
        random: Random,
        policy: JunkGenerationPolicy,
        shared: String?,
        sharedProgram: JunkSharedProgram?,
        dependencies: List<Node>,
    ): ByteArray {
        val writer = newClass(node.name, "java/lang/Object", node.contract)
        val fields = stateFields(writer, random, policy, needsLabel = node.role == Role.DTO)
        stateConstructor(writer, node.name, "java/lang/Object", fields, random, policy)
        calculate(writer, node.name, random, policy, shared, sharedProgram, dependencies)
        textMethod(writer, "describe", random, policy)
        var mandatory = 3
        var omittedMethods = 0
        when (node.role) {
            Role.DTO -> {
                val includeComparison = policy.profile?.usesOptionalDtoComparison != true || policy.includeDtoComparison
                dto(writer, node.name, policy, includeComparison)
                mandatory += if (includeComparison) 7 else 6
                if (!includeComparison) omittedMethods++
            }
            Role.CONVERTER -> {
                longMethod(writer, "widen", random, policy)
                nodes.firstOrNull { it.role == Role.DTO }?.let { dto ->
                    if (policy.profile?.usesOptionalDtoStringAdapters != true || policy.includeDtoStringAdapters) {
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
                    } else {
                        omittedMethods++
                    }
                }
                mandatory++
            }
            Role.VALIDATOR -> { booleanMethod(writer, "accepts", random, policy); mandatory++ }
            Role.COLLECTION -> {
                collectionMethod(writer, "summarize", random, policy)
                selectStrings(writer, random, policy)
                mandatory += 2
            }
            Role.CONFIG -> {
                writer.visitField(ACC_PUBLIC or ACC_STATIC or ACC_FINAL, "LIMIT", "I", null, random.nextInt(4, 33)).visitEnd()
                booleanMethod(writer, "enabledFor", random, policy)
                mandatory++
            }
            else -> Unit
        }
        val peer = if (policy.profile == null) {
            nodes.filter { it.name != node.name && it.role in ordinaryRoles }.randomOrNull(random)
        } else null
        if (peer != null) {
            writer.method(ACC_PUBLIC, "combine", "(L${peer.name};I)I") {
                val absent = Label()
                visitVarInsn(ALOAD, 1)
                visitJumpInsn(IFNULL, absent)
                visitVarInsn(ALOAD, 1)
                visitVarInsn(ILOAD, 2)
                invokeCalculation(peer.name, policy)
                visitVarInsn(ISTORE, 2)
                visitLabel(absent)
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ILOAD, 2)
                invokeCalculation(node.name, policy)
                visitInsn(IRETURN)
            }
            mandatory++
        }
        extraMethods(writer, node.name, random, policy, shared, mandatory, omittedMethods)
        return finish(writer)
    }

    private fun activity(
        unit: JunkCodeUnit,
        namespace: String,
        nodes: List<Node>,
        random: Random,
        policy: JunkGenerationPolicy,
        graph: HelperGraph?,
        sharedProgram: JunkSharedProgram?,
    ): ByteArray {
        val parent = if (policy.enableFragments) "androidx/fragment/app/FragmentActivity" else "android/app/Activity"
        val writer = newClass(unit.activityName, parent)
        val state = policy.state
        val fields = stateFields(writer, random, policy)
        stateConstructor(writer, unit.activityName, parent, fields, random, policy)
        val helpers = graph?.roots ?: nodes.filter { it.role in ordinaryRoles }.shuffled(random)
        val fragments = nodes.filter { it.role == Role.FRAGMENT }
        val legacyHelpers = if (sharedProgram == null) unit.sharedHelpers else emptyList()
        calculate(writer, unit.activityName, random, policy, legacyHelpers.randomOrNull(random), sharedProgram)
        writer.method(ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKESPECIAL, parent, "onCreate", "(Landroid/os/Bundle;)V", false)
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETSTATIC, namespace.replace('.', '/') + "/R\$layout", unit.layoutName, "I")
            visitMethodInsn(INVOKEVIRTUAL, unit.activityName, "setContentView", "(I)V", false)
            val contextSlot = 2
            val scratchSlot = 3
            storeCalculationContext(unit.activityName, policy, contextSlot)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 0)
            visitLdcInsn(random.nextInt(1, 128))
            invokeCalculation(unit.activityName, policy, contextSlot = contextSlot, scratchSlot = scratchSlot)
            writeState(unit.activityName, state)
            val roots = if (graph != null || helpers.isEmpty()) helpers else helpers.take(random.nextInt(1, minOf(helpers.size, 3) + 1))
            roots.forEach { helper ->
                visitVarInsn(ALOAD, 0)
                newObject(helper.name)
                visitVarInsn(ALOAD, 0)
                readState(unit.activityName, state)
                invokeCalculation(helper.contract ?: helper.name, policy, helper.contract != null, contextSlot, scratchSlot)
                writeState(unit.activityName, state)
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
                readState(unit.activityName, state)
                val input = policy.methodBodyProfile?.input
                when (input) {
                    Input.PAIR -> visitInsn(I2C)
                    Input.WIDE -> {
                        visitInsn(I2L)
                        visitLdcInsn(0x7fffL)
                        visitInsn(LAND)
                        visitInsn(L2I)
                    }
                    Input.TEXT -> {
                        visitLdcInsn(0x8000)
                        visitInsn(IREM)
                    }
                    else -> {
                        visitLdcInsn(0x7fff)
                        visitInsn(IAND)
                    }
                }
                writeState(unit.activityName, state)
                visitInsn(RETURN)
            }
            mandatory++
        }
        extraMethods(writer, unit.activityName, random, policy, legacyHelpers.randomOrNull(random), mandatory)
        return finish(writer)
    }

    private fun fragment(
        node: Node,
        nodes: List<Node>,
        random: Random,
        policy: JunkGenerationPolicy,
        shared: String?,
        graph: HelperGraph?,
        sharedProgram: JunkSharedProgram?,
    ): ByteArray {
        val parent = "androidx/fragment/app/Fragment"
        val writer = newClass(node.name, parent)
        val state = policy.state
        val fields = stateFields(writer, random, policy)
        stateConstructor(writer, node.name, parent, fields, random, policy)
        calculate(writer, node.name, random, policy, shared, sharedProgram?.takeIf { graph != null })
        val helpers = nodes.filter { it.role in ordinaryRoles }
        val helperIndex = if (helpers.isEmpty()) 0 else random.nextInt(helpers.size)
        val roots = graph?.roots?.let { roots ->
            // A Fragment visits a bounded subset of the unit's roots, starting at a seeded offset.
            List(minOf(roots.size, 3)) { roots[(helperIndex + it) % roots.size] }
        }
        writer.method(ACC_PUBLIC, "onCreate", "(Landroid/os/Bundle;)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 1)
            visitMethodInsn(INVOKESPECIAL, parent, "onCreate", "(Landroid/os/Bundle;)V", false)
            val contextSlot = 3
            val scratchSlot = 4
            storeCalculationContext(node.name, policy, contextSlot)
            if (roots != null) {
                visitLdcInsn(random.nextInt(128))
                visitVarInsn(ISTORE, 2)
                roots.forEach { helper ->
                    newObject(helper.name)
                    visitVarInsn(ILOAD, 2)
                    invokeCalculation(helper.contract ?: helper.name, policy, helper.contract != null, contextSlot, scratchSlot)
                    visitVarInsn(ISTORE, 2)
                }
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ILOAD, 2)
                invokeCalculation(node.name, policy, contextSlot = contextSlot, scratchSlot = scratchSlot)
                visitInsn(POP)
            } else {
                visitVarInsn(ALOAD, 0)
                val helper = helpers.getOrNull(helperIndex)
                if (helper != null) {
                    newObject(helper.name)
                    visitLdcInsn(random.nextInt(128))
                    invokeCalculation(helper.name, policy, contextSlot = contextSlot)
                } else {
                    visitVarInsn(ALOAD, 0)
                    visitLdcInsn(random.nextInt(128))
                    invokeCalculation(node.name, policy, contextSlot = contextSlot)
                }
                writeState(node.name, state)
            }
            visitInsn(RETURN)
        }
        extraMethods(writer, node.name, random, policy, shared, 3)
        return finish(writer)
    }

    private fun customView(
        node: Node,
        random: Random,
        policy: JunkGenerationPolicy,
        shared: String?,
        sharedProgram: JunkSharedProgram?,
    ): ByteArray {
        val parent = "android/view/View"
        val writer = newClass(node.name, parent)
        stateFields(writer, random, policy)
        listOf("(Landroid/content/Context;)V", "(Landroid/content/Context;Landroid/util/AttributeSet;)V").forEachIndexed { index, descriptor ->
            writer.method(ACC_PUBLIC, "<init>", descriptor) {
                visitVarInsn(ALOAD, 0)
                visitVarInsn(ALOAD, 1)
                if (index == 1) visitVarInsn(ALOAD, 2)
                visitMethodInsn(INVOKESPECIAL, parent, "<init>", descriptor, false)
                initializeState(node.name, policy.state, random.nextInt(128))
                visitInsn(RETURN)
            }
        }
        val profile = policy.profile
        val inlinePreparation = when {
            profile?.usesProfiledComponentCalls != true -> true
            // Paired evaluation combines the prepared value with the local result. Keep the
            // preparation step in its own helper even when the two stages are split.
            profile.usesPairedViewStages -> false
            else -> sharedProgram?.hasSeparateStages == true
        }
        calculate(writer, node.name, random, policy, shared, sharedProgram, inlinePreparation = inlinePreparation)
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

    private fun stateFields(
        writer: ClassWriter, random: Random, policy: JunkGenerationPolicy, needsLabel: Boolean = false,
    ): StateFields {
        val onlyRequired = policy.profile?.usesRequiredFieldsOnly == true
        val hasLabel = needsLabel || !onlyRequired
        writer.visitField(ACC_PRIVATE, "state", policy.state.descriptor, null, null).visitEnd()
        if (hasLabel) writer.visitField(ACC_PRIVATE, "label", "Ljava/lang/String;", null, null).visitEnd()
        if (onlyRequired) return StateFields(hasLabel, emptyList())
        val fields = mutableListOf<Pair<String, String>>()
        repeat(random.nextInt(1, 5)) { index ->
            val descriptor = listOf("I", "J", "D", "Z", "Ljava/lang/String;").random(random)
            fields += "value$index" to descriptor
            writer.visitField(ACC_PRIVATE, "value$index", descriptor, null, null).visitEnd()
        }
        return StateFields(hasLabel, fields)
    }

    private fun stateConstructor(
        writer: ClassWriter,
        name: String,
        parent: String,
        fields: StateFields,
        random: Random,
        policy: JunkGenerationPolicy,
    ) {
        writer.method(ACC_PUBLIC, "<init>", "()V") {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false)
            val initial = random.nextInt(1, 256)
            initializeState(name, policy.state, initial)
            if (fields.hasLabel) {
                visitVarInsn(ALOAD, 0)
                if (policy.profile?.usesRequiredFieldsOnly == true) {
                    visitLdcInsn(initial)
                    visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false)
                } else {
                    visitLdcInsn("item${random.nextInt(1000)}")
                }
                visitFieldInsn(PUTFIELD, name, "label", "Ljava/lang/String;")
            }
            fields.extra.shuffled(random).forEach { (field, descriptor) ->
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

    private fun MethodVisitor.initializeState(owner: String, state: State, value: Int) {
        if (state == State.ARRAY) {
            visitVarInsn(ALOAD, 0)
            visitInsn(ICONST_1)
            visitIntInsn(NEWARRAY, T_INT)
            visitFieldInsn(PUTFIELD, owner, "state", state.descriptor)
        }
        visitVarInsn(ALOAD, 0)
        visitLdcInsn(value)
        writeState(owner, state)
    }

    private fun MethodVisitor.readState(owner: String, state: State) {
        visitFieldInsn(GETFIELD, owner, "state", state.descriptor)
        when (state) {
            State.SCALAR -> Unit
            State.WIDE -> visitInsn(L2I)
            State.BOXED -> visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false)
            State.ARRAY -> {
                visitInsn(ICONST_0)
                visitInsn(IALOAD)
            }
        }
    }

    // The stack holds the receiver and an int. Arrays store the same value in their first slot.
    private fun MethodVisitor.writeState(owner: String, state: State) {
        when (state) {
            State.SCALAR -> Unit
            State.WIDE -> visitInsn(I2L)
            State.BOXED -> visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false)
            State.ARRAY -> {
                visitInsn(SWAP)
                visitFieldInsn(GETFIELD, owner, "state", state.descriptor)
                visitInsn(SWAP)
                visitInsn(ICONST_0)
                visitInsn(SWAP)
                visitInsn(IASTORE)
                return
            }
        }
        visitFieldInsn(PUTFIELD, owner, "state", state.descriptor)
    }

    private fun MethodVisitor.storeCalculationContext(owner: String, policy: JunkGenerationPolicy, slot: Int) {
        if (policy.calculationRole == null) return
        visitVarInsn(ALOAD, 0)
        readState(owner, policy.state)
        visitVarInsn(ISTORE, slot)
    }

    // Callers use scalar state internally. The call exchanges typed operands and results.
    private fun MethodVisitor.invokeCalculation(
        owner: String,
        policy: JunkGenerationPolicy,
        isInterface: Boolean = false,
        contextSlot: Int? = null,
        scratchSlot: Int? = null,
        branchSumSlot: Int? = null,
        branchCount: Int = 1,
    ) {
        val topology = policy.calculationTopology
        val currentValueSlot = if (topology.usesOrderedValues) {
            val slot = checkNotNull(scratchSlot) { "Missing scratch slot for the current calculation input" }
            visitInsn(DUP)
            visitVarInsn(ISTORE, slot)
            slot
        } else null
        adaptCalculationArgument(policy.input)
        policy.calculationRole?.let { role ->
            val previousValueSlot = checkNotNull(contextSlot) { "Missing calculation role context" }
            when (topology) {
                JunkTopologyContext.NONE -> Unit
                JunkTopologyContext.CHAIN -> packOrderedValues(previousValueSlot, checkNotNull(currentValueSlot))
                JunkTopologyContext.PAIRS -> {
                    visitVarInsn(ILOAD, previousValueSlot)
                    visitVarInsn(ILOAD, checkNotNull(currentValueSlot))
                }
                JunkTopologyContext.TREE -> {
                    if (branchSumSlot == null) {
                        // A root call starts with a single value from the caller's state.
                        visitVarInsn(ILOAD, previousValueSlot)
                        visitInsn(I2L)
                        visitInsn(ICONST_1)
                    } else {
                        visitVarInsn(LLOAD, branchSumSlot)
                        visitLdcInsn(branchCount)
                    }
                }
            }
            visitVarInsn(ILOAD, previousValueSlot)
            role.valueType.pack(this)
        }
        visitMethodInsn(if (isInterface) INVOKEINTERFACE else INVOKEVIRTUAL, owner, "calculate", policy.calculationDescriptor, isInterface)
        policy.calculationResult.fold(this)
    }

    private fun MethodVisitor.loadRoleAdjustedInput(role: Roles, contextSlot: Int) {
        visitVarInsn(ILOAD, 1)
        role.valueType.loadFolded(this, contextSlot)
        when (role) {
            Roles.MODELS, Roles.COLLECTIONS -> visitInsn(IADD)
            Roles.RULES -> {
                val accepted = Label()
                visitJumpInsn(IFNE, accepted)
                visitInsn(INEG)
                visitLabel(accepted)
            }
            Roles.CONFIGURATION -> visitInsn(IXOR)
        }
    }

    private fun calculate(
        writer: ClassWriter,
        owner: String,
        random: Random,
        policy: JunkGenerationPolicy,
        shared: String?,
        sharedProgram: JunkSharedProgram?,
        dependencies: List<Node> = emptyList(),
        inlinePreparation: Boolean = false,
    ) {
        val input = policy.input
        val resultType = policy.calculationResult
        val role = policy.calculationRole
        val topology = policy.calculationTopology
        writer.method(ACC_PUBLIC, "calculate", policy.calculationDescriptor) {
            normalizeCalculationInput(input, 1)
            val programInput = if (role == null) 1 else {
                // Consume the context operands before the calculation reuses the argument slots.
                val topologySlot = 1 + input.slots
                loadRoleAdjustedInput(role, topologySlot + topology.slots)
                if (topology != JunkTopologyContext.NONE) {
                    topology.loadFolded(this, topologySlot)
                    visitInsn(ICONST_3)
                    visitInsn(IMUL)
                    visitInsn(IADD)
                }
                visitVarInsn(ISTORE, 2)
                2
            }
            AndroidJunkBytecodeInject.composeInt(this, random, programInput, 2, 3,
                policy.maxMethodOperations, shared, policy.profile?.program)
            val wideContextSlot = 4
            val scratchSlot = wideContextSlot + 2
            val aggregatesBranches = sharedProgram?.aggregatesBranches == true
            if (aggregatesBranches) {
                visitVarInsn(ILOAD, 2)
                visitInsn(I2L)
                visitVarInsn(LSTORE, wideContextSlot)
            } else if (sharedProgram?.needsLocalResult == true) {
                // The loop scratch slot is free after composing the local result.
                visitVarInsn(ILOAD, 2)
                visitVarInsn(ISTORE, 3)
            }
            dependencies.forEachIndexed { index, helper ->
                newObject(helper.name)
                visitVarInsn(ILOAD, 2)
                invokeCalculation(helper.contract ?: helper.name, policy, helper.contract != null,
                    contextSlot = 1, scratchSlot = scratchSlot,
                    branchSumSlot = wideContextSlot.takeIf { aggregatesBranches }, branchCount = index + 1)
                if (aggregatesBranches) {
                    // Siblings receive the same local input. Reduce their results in a long.
                    visitInsn(I2L)
                    visitVarInsn(LLOAD, wideContextSlot)
                    visitInsn(LADD)
                    visitVarInsn(LSTORE, wideContextSlot)
                } else {
                    visitVarInsn(ISTORE, 2)
                }
            }
            sharedProgram?.emit(this, inputSlot = 1, resultSlot = 2, localResultSlot = 3,
                wideContextSlot = wideContextSlot, dependencyCount = dependencies.size, inlinePreparation = inlinePreparation)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 2)
            writeState(owner, policy.state)
            visitVarInsn(ILOAD, 2)
            resultType.pack(this)
            visitInsn(resultType.returnOpcode)
        }
    }

    private fun dto(writer: ClassWriter, owner: String, policy: JunkGenerationPolicy, includeComparison: Boolean) {
        val state = policy.state
        writer.method(ACC_PUBLIC, "<init>", "(ILjava/lang/String;)V") {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, owner, "<init>", "()V", false)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 1)
            writeState(owner, state)
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ALOAD, 2)
            visitFieldInsn(PUTFIELD, owner, "label", "Ljava/lang/String;")
            visitInsn(RETURN)
        }
        writer.method(ACC_PUBLIC, "getValue", "()I") {
            visitVarInsn(ALOAD, 0)
            readState(owner, state)
            visitInsn(IRETURN)
        }
        writer.method(ACC_PUBLIC, "setValue", "(I)V") {
            visitVarInsn(ALOAD, 0)
            visitVarInsn(ILOAD, 1)
            writeState(owner, state)
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
            readState(owner, state)
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(GETFIELD, owner, "label", "Ljava/lang/String;")
            visitMethodInsn(INVOKESPECIAL, owner, "<init>", "(ILjava/lang/String;)V", false)
            visitInsn(ARETURN)
        }
        if (!includeComparison) return
        writer.method(ACC_PUBLIC, "compareValue", "(L$owner;)I") {
            val present = Label()
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNONNULL, present)
            visitInsn(ICONST_1)
            visitInsn(IRETURN)
            visitLabel(present)
            val input = policy.methodBodyProfile?.input
            if (input == Input.TEXT) {
                val equalLabels = Label()
                compareLabels(owner)
                visitInsn(DUP)
                visitJumpInsn(IFEQ, equalLabels)
                visitInsn(IRETURN)
                visitLabel(equalLabels)
                visitInsn(POP)
            }
            visitVarInsn(ALOAD, 0)
            readState(owner, state)
            if (input == Input.WIDE) visitInsn(I2L)
            visitVarInsn(ALOAD, 1)
            readState(owner, state)
            when (input) {
                Input.WIDE -> {
                    visitInsn(I2L)
                    visitInsn(LCMP)
                }
                Input.PAIR -> {
                    visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "compare", "(II)I", false)
                    visitInsn(DUP)
                    val ordered = Label()
                    visitJumpInsn(IFNE, ordered)
                    visitInsn(POP)
                    compareLabels(owner)
                    visitLabel(ordered)
                }
                else -> visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "compare", "(II)I", false)
            }
            visitInsn(IRETURN)
        }
    }

    private fun MethodVisitor.compareLabels(owner: String) {
        val leftPresent = Label()
        val rightPresent = Label()
        val ordered = Label()
        visitVarInsn(ALOAD, 0)
        visitFieldInsn(GETFIELD, owner, "label", "Ljava/lang/String;")
        visitVarInsn(ASTORE, 2)
        visitVarInsn(ALOAD, 1)
        visitFieldInsn(GETFIELD, owner, "label", "Ljava/lang/String;")
        visitVarInsn(ASTORE, 3)
        visitVarInsn(ALOAD, 2)
        visitJumpInsn(IFNONNULL, leftPresent)
        visitVarInsn(ALOAD, 3)
        visitJumpInsn(IFNONNULL, rightPresent)
        visitInsn(ICONST_0)
        visitJumpInsn(GOTO, ordered)
        visitLabel(rightPresent)
        visitInsn(ICONST_M1)
        visitJumpInsn(GOTO, ordered)
        visitLabel(leftPresent)
        visitVarInsn(ALOAD, 3)
        val compare = Label()
        visitJumpInsn(IFNONNULL, compare)
        visitInsn(ICONST_1)
        visitJumpInsn(GOTO, ordered)
        visitLabel(compare)
        visitVarInsn(ALOAD, 2)
        visitVarInsn(ALOAD, 3)
        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "compareTo", "(Ljava/lang/String;)I", false)
        visitLabel(ordered)
    }

    private fun extraMethods(
        writer: ClassWriter,
        owner: String,
        random: Random,
        policy: JunkGenerationPolicy,
        shared: String?,
        mandatory: Int,
        omittedMethods: Int = 0,
    ) {
        val input = policy.methodBodyProfile?.input ?: Input.INTEGER
        val target = random.nextInt(policy.minMethodsPerClass, policy.maxMethodsPerClass + 1)
        val extraCount = (target - mandatory - omittedMethods).coerceAtLeast(0)
        repeat(maxOf(extraCount, policy.minMethodsPerClass - mandatory)) { index ->
            // Reaching the method minimum uses a separate stream so the unit's other methods stay deterministic.
            val methodRandom = if (index < extraCount) random else Random(policy.seedFor(owner.hashCode()))
            val name = "step${index}_${methodRandom.nextInt(0x1000).toString(16)}"
            when (methodRandom.nextInt(7)) {
                0 -> writer.method(ACC_PUBLIC, name, "(I)I") {
                    AndroidJunkBytecodeInject.composeInt(this, methodRandom, 1, 2, 3, policy.maxMethodOperations,
                        sharedHelper = shared, input = input, stepProfile = policy.profile)
                    visitVarInsn(ILOAD, 2)
                    visitInsn(IRETURN)
                }
                1 -> writer.method(ACC_PUBLIC, name, "(II)I") {
                    visitVarInsn(ILOAD, 1)
                    visitVarInsn(ILOAD, 2)
                    visitInsn(listOf(IADD, IXOR, ISUB).random(methodRandom))
                    visitVarInsn(ISTORE, 3)
                    AndroidJunkBytecodeInject.composeInt(this, methodRandom, 3, 4, 5, policy.maxMethodOperations,
                        sharedHelper = shared, input = input, stepProfile = policy.profile)
                    visitVarInsn(ILOAD, 4)
                    visitInsn(IRETURN)
                }
                2 -> textMethod(writer, name, methodRandom, policy)
                3 -> booleanMethod(writer, name, methodRandom, policy)
                4 -> collectionMethod(writer, name, methodRandom, policy)
                5 -> longMethod(writer, name, methodRandom, policy)
                else -> writer.method(ACC_PUBLIC, name, "(Z)I") {
                    val unchanged = Label()
                    visitVarInsn(ALOAD, 0)
                    readState(owner, policy.state)
                    visitVarInsn(ISTORE, 2)
                    visitVarInsn(ILOAD, 1)
                    visitJumpInsn(IFEQ, unchanged)
                    AndroidJunkBytecodeInject.composeInt(this, methodRandom, 2, 2, 3, policy.maxMethodOperations,
                        sharedHelper = shared, input = input, stepProfile = policy.profile)
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
            boundTextInput(policy.methodRole, random.nextInt(32, 193))
            policy.profile?.takeIf { it.usesProgramSpecificMethods }?.let { prepareProgramText(it) }
            val operationBudget = minOf(policy.maxMethodOperations, 10).coerceAtLeast(1)
            repeat(random.nextInt(minOf(3, operationBudget), operationBudget + 1)) {
                when (random.nextInt(9)) {
                    0 -> {
                        visitVarInsn(ALOAD, 1)
                        trimText()
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
                        digestText()
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

    /** Prepare the role's bounded slice before sampling text transformations. */
    private fun MethodVisitor.prepareProgramText(profile: JunkGenerationProfile) {
        fun loadProgramOperand() {
            if (profile.usesProfiledTextOperands) {
                loadTextOperand(profile, combineTextFields = true)
            } else {
                visitVarInsn(ALOAD, 1)
            }
        }

        when (profile.program) {
            Program.ARITHMETIC -> {
                loadProgramOperand()
                digestText()
            }
            Program.BRANCHING -> if (profile.usesProfiledBranchText) {
                loadTextOperand(profile)
            } else {
                val ready = Label()
                visitVarInsn(ALOAD, 1)
                visitLdcInsn(' '.code)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
                visitVarInsn(ISTORE, 2)
                visitVarInsn(ALOAD, 1)
                visitVarInsn(ILOAD, 2)
                visitJumpInsn(IFLE, ready)
                visitInsn(ICONST_0)
                visitVarInsn(ILOAD, 2)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
                visitLabel(ready)
            }
            Program.ACCUMULATION -> {
                if (profile.usesProfiledTextOperands) {
                    loadProgramOperand()
                    visitVarInsn(ASTORE, 1)
                }
                val builder = "java/lang/StringBuilder"
                visitTypeInsn(NEW, builder)
                visitInsn(DUP)
                visitMethodInsn(INVOKESPECIAL, builder, "<init>", "()V", false)
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitMethodInsn(INVOKEVIRTUAL, builder, "append", "(I)Ljava/lang/StringBuilder;", false)
                visitLdcInsn(':'.code)
                visitMethodInsn(INVOKEVIRTUAL, builder, "append", "(C)Ljava/lang/StringBuilder;", false)
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, builder, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false)
                visitMethodInsn(INVOKEVIRTUAL, builder, "toString", "()Ljava/lang/String;", false)
            }
            Program.TEXT -> {
                loadProgramOperand()
                trimText()
            }
        }
        visitVarInsn(ASTORE, 1)
    }

    /** Select the operand from the bounded slice using the topology's separator. */
    private fun MethodVisitor.loadTextOperand(profile: JunkGenerationProfile, combineTextFields: Boolean = false) {
        visitVarInsn(ALOAD, 1)
        visitLdcInsn(' '.code)
        when (profile.calls) {
            Calls.FAN_OUT -> {
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
                visitVarInsn(ALOAD, 1)
                visitLdcInsn('_'.code)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
            }
            Calls.CHAIN -> visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
            Calls.PAIRS -> {
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitInsn(ICONST_1)
                visitInsn(IUSHR)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(II)I", false)
            }
            Calls.TREE -> visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(I)I", false)
        }
        visitVarInsn(ISTORE, 2)

        fun loadPrefix() {
            visitVarInsn(ALOAD, 1)
            visitInsn(ICONST_0)
            visitVarInsn(ILOAD, 2)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
        }

        fun loadSuffix() {
            visitVarInsn(ALOAD, 1)
            visitVarInsn(ILOAD, 2)
            visitInsn(ICONST_1)
            visitInsn(IADD)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
        }

        val unsplit = Label()
        val ready = Label()
        visitVarInsn(ILOAD, 2)
        if (profile.prefersNonblankTextValue && !combineTextFields) {
            // A single text field uses the trimmed value after the separator, falling back to the key.
            val trimFallback = Label()
            visitJumpInsn(IFLT, unsplit)
            loadSuffix()
            trimText()
            visitInsn(DUP)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
            visitJumpInsn(IFEQ, ready)
            visitInsn(POP)
            loadPrefix()
            visitJumpInsn(GOTO, trimFallback)
            visitLabel(unsplit)
            visitVarInsn(ALOAD, 1)
            visitLabel(trimFallback)
            trimText()
            visitLabel(ready)
            return
        }
        visitJumpInsn(IFLE, unsplit)
        visitVarInsn(ALOAD, 1)
        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
        visitVarInsn(ISTORE, 3)
        visitVarInsn(ILOAD, 3)
        visitInsn(ICONST_1)
        visitInsn(ISUB)
        visitVarInsn(ILOAD, 2)
        visitJumpInsn(IF_ICMPLE, unsplit)
        when (profile.input) {
            Input.INTEGER -> loadPrefix()
            Input.TEXT -> {
                loadPrefix()
                if (combineTextFields) {
                    trimText()
                    visitLdcInsn(" ")
                    visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
                    loadSuffix()
                    trimText()
                    visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
                }
            }
            Input.WIDE -> loadSuffix()
            Input.PAIR -> {
                val right = Label()
                // Prefer the shorter of the two nonempty fields.
                visitVarInsn(ILOAD, 2)
                visitVarInsn(ILOAD, 3)
                visitVarInsn(ILOAD, 2)
                visitInsn(ISUB)
                visitInsn(ICONST_1)
                visitInsn(ISUB)
                visitJumpInsn(IF_ICMPGT, right)
                loadPrefix()
                visitJumpInsn(GOTO, ready)
                visitLabel(right)
                loadSuffix()
            }
        }
        visitJumpInsn(GOTO, ready)
        visitLabel(unsplit)
        visitVarInsn(ALOAD, 1)
        visitLabel(ready)
        if (profile.input == Input.TEXT && !combineTextFields) trimText()
    }

    private fun MethodVisitor.trimText() {
        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
    }

    private fun MethodVisitor.digestText() {
        visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
        visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false)
    }

    /** Restrict work to a bounded slice. Configuration text contributes its key before '='. */
    private fun MethodVisitor.boundTextInput(role: Roles?, limit: Int) {
        when (role) {
            Roles.RULES -> {
                visitVarInsn(ALOAD, 1)
                visitInsn(ICONST_0)
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitLdcInsn(limit)
                visitInsn(ISUB)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
            }
            Roles.COLLECTIONS -> {
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitInsn(DUP)
                visitLdcInsn(limit)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
                visitVarInsn(ISTORE, 2)
                visitVarInsn(ILOAD, 2)
                visitInsn(ISUB)
                visitInsn(ICONST_1)
                visitInsn(IUSHR)
                visitVarInsn(ISTORE, 3)
                visitVarInsn(ALOAD, 1)
                visitVarInsn(ILOAD, 3)
                visitVarInsn(ILOAD, 3)
                visitVarInsn(ILOAD, 2)
                visitInsn(IADD)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
            }
            else -> {
                visitVarInsn(ALOAD, 1)
                visitInsn(ICONST_0)
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitLdcInsn(limit)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
            }
        }
        visitVarInsn(ASTORE, 1)
        if (role == Roles.CONFIGURATION) {
            val complete = Label()
            visitVarInsn(ALOAD, 1)
            visitLdcInsn('='.code)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I", false)
            visitVarInsn(ISTORE, 2)
            visitVarInsn(ILOAD, 2)
            visitJumpInsn(IFLT, complete)
            visitVarInsn(ALOAD, 1)
            visitInsn(ICONST_0)
            visitVarInsn(ILOAD, 2)
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "substring", "(II)Ljava/lang/String;", false)
            visitVarInsn(ASTORE, 1)
            visitLabel(complete)
        }
    }

    private fun booleanMethod(writer: ClassWriter, name: String, random: Random, policy: JunkGenerationPolicy) {
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
            policy.profile?.takeIf { it.usesProfiledLongAndPredicateMethods }?.let { profile ->
                // Choose the field before evaluating the predicates. Recheck its length after trimming.
                boundTextInput(profile.roles, limit = 128)
                val normalizeText = profile.usesProfiledFilters && profile.program == Program.TEXT
                loadTextOperand(profile, combineTextFields = normalizeText)
                if (normalizeText) trimText()
                visitVarInsn(ASTORE, 1)
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitInsn(DUP)
                visitVarInsn(ISTORE, 2)
                visitJumpInsn(IFEQ, rejected)
            }
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

    private fun longMethod(writer: ClassWriter, name: String, random: Random, policy: JunkGenerationPolicy) {
        writer.method(ACC_PUBLIC, name, "(J)J") {
            policy.profile?.takeIf { it.usesProfiledLongAndPredicateMethods }?.let { profile ->
                prepareLongInput(profile)
                if (profile.usesProgramSpecificLongMethods) prepareProgramLong(profile)
            }
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

    private fun MethodVisitor.prepareLongInput(profile: JunkGenerationProfile) {
        visitVarInsn(LLOAD, 1)
        when (profile.calls) {
            Calls.FAN_OUT -> {
                // Merge the original value and its role-specific projection.
                visitInsn(DUP2)
                projectLongInput(profile.roles)
                visitInsn(LADD)
            }
            Calls.CHAIN -> projectLongInput(profile.roles)
            Calls.PAIRS -> {
                // Project the signed high word and carry the low word through unchanged.
                visitLdcInsn(32)
                visitInsn(LSHR)
                projectLongInput(profile.roles)
                visitLdcInsn(32)
                visitInsn(LSHL)
                visitVarInsn(LLOAD, 1)
                visitLdcInsn(0xffff_ffffL)
                visitInsn(LAND)
                visitInsn(LOR)
            }
            Calls.TREE -> {
                val ready = Label()
                visitInsn(DUP2)
                visitInsn(LCONST_0)
                visitInsn(LCMP)
                visitJumpInsn(IFGE, ready)
                projectLongInput(profile.roles)
                visitLabel(ready)
            }
        }
        visitVarInsn(LSTORE, 1)
    }

    // Consume one long and return its projection on the operand stack.
    private fun MethodVisitor.projectLongInput(role: Roles) {
        when (role) {
            Roles.MODELS -> {
                visitLdcInsn(2L)
                visitInsn(LDIV)
            }
            Roles.RULES -> {
                visitInsn(LCONST_0)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(JJ)J", false)
            }
            Roles.COLLECTIONS -> {
                visitMethodInsn(INVOKESTATIC, "java/lang/Long", "bitCount", "(J)I", false)
                visitInsn(I2L)
            }
            Roles.CONFIGURATION -> visitMethodInsn(INVOKESTATIC, "java/lang/Long", "lowestOneBit", "(J)J", false)
        }
    }

    private fun MethodVisitor.prepareProgramLong(profile: JunkGenerationProfile) {
        val input = if (profile.usesProfiledLongOperands) profile.input else Input.WIDE
        loadLongOperand(input)
        when (profile.program) {
            Program.ARITHMETIC -> {
                visitLdcInsn(3L)
                visitInsn(LMUL)
                visitInsn(LCONST_1)
                visitInsn(LADD)
            }
            Program.BRANCHING -> {
                val ready = Label()
                visitInsn(DUP2)
                visitInsn(L2I)
                visitInsn(ICONST_1)
                visitInsn(IAND)
                visitJumpInsn(IFNE, ready)
                // Halve even values while preserving their sign.
                visitInsn(ICONST_1)
                visitInsn(LSHR)
                visitLabel(ready)
            }
            Program.ACCUMULATION -> {
                if (input != Input.WIDE) {
                    visitInsn(DUP2)
                    visitVarInsn(LSTORE, 1)
                }
                // Sum the two unsigned words of the prepared input.
                visitLdcInsn(32)
                visitInsn(LUSHR)
                visitVarInsn(LLOAD, 1)
                visitLdcInsn(0xffff_ffffL)
                visitInsn(LAND)
                visitInsn(LADD)
            }
            Program.TEXT -> {
                visitMethodInsn(INVOKESTATIC, "java/lang/Long", "toString", "(J)Ljava/lang/String;", false)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitInsn(I2L)
            }
        }
        visitVarInsn(LSTORE, 1)
    }

    private fun MethodVisitor.loadLongOperand(input: Input) {
        visitVarInsn(LLOAD, 1)
        when (input) {
            Input.INTEGER -> {
                visitInsn(L2I)
                visitInsn(I2L)
            }
            Input.PAIR -> {
                // Combine the signed high and low words without overflowing an int.
                visitLdcInsn(32)
                visitInsn(LSHR)
                visitVarInsn(LLOAD, 1)
                visitInsn(L2I)
                visitInsn(I2L)
                visitInsn(LADD)
            }
            Input.WIDE -> Unit
            Input.TEXT -> {
                visitMethodInsn(INVOKESTATIC, "java/lang/Long", "toString", "(J)Ljava/lang/String;", false)
                JunkValueType.TEXT.fold(this)
                visitInsn(I2L)
            }
        }
    }

    private fun collectionMethod(writer: ClassWriter, name: String, random: Random, policy: JunkGenerationPolicy) {
        val profile = policy.methodBodyProfile
        val role = policy.methodRole
        writer.method(ACC_PUBLIC, name, "(Ljava/util/List;)I") {
            val limit = random.nextInt(4, 17)
            val measureStrings = random.nextBoolean()
            val end = Label()
            visitInsn(ICONST_0)
            visitVarInsn(ISTORE, 2)
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, end)
            visitListPrefix(profile?.listTraversal() ?: ListTraversal.INDEXED, limit, end) { next ->
                if (role != null) {
                    summarizeListElement(role, measureStrings, next, profile)
                } else {
                    visitVarInsn(ALOAD, 5)
                    visitJumpInsn(IFNULL, next)
                    if (measureStrings) {
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
                }
            }
            visitLabel(end)
            // A short reducer draws from the full set of operations to keep the available
            // sequences broad even when two projects share a program.
            AndroidJunkBytecodeInject.composeInt(this, random, 2, 2, 6, minOf(policy.maxMethodOperations, 6),
                input = profile?.input ?: Input.INTEGER)
            visitVarInsn(ILOAD, 2)
            visitInsn(IRETURN)
        }
    }

    private fun MethodVisitor.summarizeListElement(role: Roles, measureStrings: Boolean, next: Label, profile: JunkGenerationProfile?) {
        val program = profile?.takeIf { it.usesProfiledFilters }?.program
        visitVarInsn(ALOAD, 5)
        if (role == Roles.MODELS && !measureStrings) {
            visitJumpInsn(IFNULL, next)
            if (profile?.usesProgramSpecificModelCounts == true && program != null && program != Program.ARITHMETIC) {
                val count = Label()
                // Non-string objects remain countable. Strings must satisfy the program's eligibility rule.
                visitVarInsn(ALOAD, 5)
                visitTypeInsn(INSTANCEOF, "java/lang/String")
                visitJumpInsn(IFEQ, count)
                prepareListString(program, next)
                if (program == Program.TEXT) {
                    loadListString()
                    visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
                    visitJumpInsn(IFNE, next)
                }
                visitLabel(count)
            }
            visitIincInsn(2, 1)
            return
        }
        // Only strings reach the program's eligibility rules and the length measurements.
        visitTypeInsn(INSTANCEOF, "java/lang/String")
        visitJumpInsn(IFEQ, next)
        program?.let { prepareListString(it, next) }
        fun loadLength() {
            loadListString()
            visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
        }
        when (role) {
            Roles.MODELS -> visitIincInsn(2, 1)
            Roles.RULES -> {
                loadLength()
                if (measureStrings) {
                    visitVarInsn(ISTORE, 6)
                    visitVarInsn(ILOAD, 6)
                    visitLdcInsn(64)
                    visitJumpInsn(IF_ICMPGT, next)
                    visitVarInsn(ILOAD, 6)
                }
                visitJumpInsn(IFEQ, next)
                visitIincInsn(2, 1)
            }
            Roles.COLLECTIONS -> {
                visitVarInsn(ILOAD, 2)
                loadLength()
                if (measureStrings) visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
                else visitInsn(IADD)
                visitVarInsn(ISTORE, 2)
            }
            Roles.CONFIGURATION -> {
                if (measureStrings) {
                    visitVarInsn(ILOAD, 2)
                    loadLength()
                    visitLdcInsn(32)
                    visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
                    visitInsn(IADD)
                    visitVarInsn(ISTORE, 2)
                } else {
                    loadLength()
                    visitLdcInsn(8)
                    visitJumpInsn(IF_ICMPLT, next)
                    visitIincInsn(2, 1)
                }
            }
        }
    }

    /** Return a bounded filtered copy; the input list and its elements are never mutated. */
    private fun selectStrings(writer: ClassWriter, random: Random, policy: JunkGenerationPolicy) {
        val profile = policy.methodBodyProfile
        val program = profile?.takeIf { it.usesProfiledFilters }?.program
        writer.method(ACC_PUBLIC, "selectStrings", "(Ljava/util/List;)Ljava/util/List;") {
            val limit = random.nextInt(4, 17)
            val minimumLength = random.nextInt(1, 9)
            val end = Label()
            visitTypeInsn(NEW, "java/util/ArrayList")
            visitInsn(DUP)
            visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            visitVarInsn(ASTORE, 2)
            visitVarInsn(ALOAD, 1)
            visitJumpInsn(IFNULL, end)
            visitListPrefix(profile?.listTraversal() ?: ListTraversal.INDEXED, limit, end) { next ->
                visitVarInsn(ALOAD, 5)
                visitTypeInsn(INSTANCEOF, "java/lang/String")
                visitJumpInsn(IFEQ, next)
                program?.let { prepareListString(it, next) }
                loadListString()
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitLdcInsn(minimumLength)
                visitJumpInsn(IF_ICMPLT, next)
                visitVarInsn(ALOAD, 2)
                visitVarInsn(ALOAD, 5)
                visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
                visitInsn(POP)
            }
            visitLabel(end)
            visitVarInsn(ALOAD, 2)
            visitInsn(ARETURN)
        }
    }

    private fun MethodVisitor.loadListString() {
        visitVarInsn(ALOAD, 5)
        visitTypeInsn(CHECKCAST, "java/lang/String")
    }

    /** Apply the program's eligibility and text rules to the current string in slot 5. */
    private fun MethodVisitor.prepareListString(program: Program, next: Label) {
        when (program) {
            Program.ARITHMETIC -> Unit
            Program.BRANCHING -> {
                loadListString()
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
                visitJumpInsn(IFNE, next)
                loadListString()
                visitInsn(ICONST_0)
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
                visitMethodInsn(INVOKESTATIC, "java/lang/Character", "isLetterOrDigit", "(C)Z", false)
                visitJumpInsn(IFEQ, next)
            }
            Program.ACCUMULATION, Program.TEXT -> {
                loadListString()
                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                visitLdcInsn(128)
                visitJumpInsn(IF_ICMPGT, next)
                if (program == Program.TEXT) {
                    loadListString()
                    trimText()
                    visitVarInsn(ASTORE, 5)
                }
            }
        }
    }

    private fun JunkGenerationProfile.listTraversal(): ListTraversal =
        ListTraversal.entries[Math.floorMod(input.ordinal - calls.ordinal, ListTraversal.entries.size)]

    // The caller has checked the list in slot 1. Each element is passed to the body in slot 5.
    private inline fun MethodVisitor.visitListPrefix(traversal: ListTraversal, limit: Int, end: Label, body: (Label) -> Unit) {
        val loop = Label()
        val next = Label()
        when (traversal) {
            ListTraversal.INDEXED, ListTraversal.PAIRED -> {
                visitVarInsn(ALOAD, 1)
                visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true)
                visitLdcInsn(limit)
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
                body(next)
                visitLabel(next)
                visitIincInsn(4, 1)
                if (traversal == ListTraversal.PAIRED) {
                    // Visit two neighboring entries per iteration, with a guard for the last pair.
                    val tail = Label()
                    visitVarInsn(ILOAD, 4)
                    visitVarInsn(ILOAD, 3)
                    visitJumpInsn(IF_ICMPGE, end)
                    visitVarInsn(ALOAD, 1)
                    visitVarInsn(ILOAD, 4)
                    visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true)
                    visitVarInsn(ASTORE, 5)
                    body(tail)
                    visitLabel(tail)
                    visitIincInsn(4, 1)
                }
            }
            ListTraversal.BACKWARD, ListTraversal.FORWARD -> {
                val reverse = traversal == ListTraversal.BACKWARD
                visitVarInsn(ALOAD, 1)
                if (reverse) {
                    // Walk the bounded prefix backwards via ListIterator without indexed reads.
                    visitVarInsn(ALOAD, 1)
                    visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true)
                    visitLdcInsn(limit)
                    visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(II)I", false)
                    visitVarInsn(ISTORE, 4)
                    visitVarInsn(ILOAD, 4)
                    visitMethodInsn(INVOKEINTERFACE, "java/util/List", "listIterator", "(I)Ljava/util/ListIterator;", true)
                } else {
                    visitLdcInsn(limit)
                    visitVarInsn(ISTORE, 4)
                    visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true)
                }
                visitVarInsn(ASTORE, 3)
                visitLabel(loop)
                visitVarInsn(ILOAD, 4)
                visitJumpInsn(IFEQ, end)
                val iterator = if (reverse) "java/util/ListIterator" else "java/util/Iterator"
                visitVarInsn(ALOAD, 3)
                visitMethodInsn(INVOKEINTERFACE, iterator, if (reverse) "hasPrevious" else "hasNext", "()Z", true)
                visitJumpInsn(IFEQ, end)
                visitVarInsn(ALOAD, 3)
                visitMethodInsn(INVOKEINTERFACE, iterator, if (reverse) "previous" else "next", "()Ljava/lang/Object;", true)
                visitVarInsn(ASTORE, 5)
                body(next)
                visitLabel(next)
                visitIincInsn(4, -1)
            }
        }
        visitJumpInsn(GOTO, loop)
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
