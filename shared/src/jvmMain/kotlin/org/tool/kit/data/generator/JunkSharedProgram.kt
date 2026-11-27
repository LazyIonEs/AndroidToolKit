package org.tool.kit.data.generator

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.tool.kit.data.generator.JunkGenerationProfile.Calls
import org.tool.kit.data.generator.JunkGenerationProfile.Input
import kotlin.random.Random

/** Shared work is part of the calculation graph, with one call per stage. */
internal class JunkSharedProgram private constructor(
    private val profile: JunkGenerationProfile,
    private val prepareOwner: String,
    private val evaluateOwner: String,
) {
    private val signature = Signature(profile)
    val needsLocalResult get() =
        signature.usesLegacyMerge || signature.context == JunkTopologyContext.CHAIN || signature.context == JunkTopologyContext.PAIRS
    val aggregatesBranches get() = signature.context == JunkTopologyContext.TREE
    val hasSeparateStages get() = prepareOwner != evaluateOwner

    fun emit(
        visitor: MethodVisitor,
        inputSlot: Int,
        resultSlot: Int,
        localResultSlot: Int,
        wideContextSlot: Int,
        dependencyCount: Int,
        inlinePreparation: Boolean,
    ) = with(visitor) {
        val dependencyResultSlot = if (dependencyCount == 0) inputSlot else resultSlot
        if (signature.context == JunkTopologyContext.CHAIN) {
            // Carry the local and continuation values through both stages in order.
            packOrderedValues(localResultSlot, dependencyResultSlot)
            visitVarInsn(LSTORE, wideContextSlot)
        }
        visitVarInsn(ILOAD, resultSlot)
        if (inlinePreparation) {
            if (profile.usesPreparedOperands) {
                emitPreparedOperands(signature.intermediate,
                    input = { visitVarInsn(ILOAD, inputSlot) },
                    programResult = {
                        visitVarInsn(ILOAD, resultSlot)
                        signature.output.pack(this)
                        signature.output.fold(this)
                    })
            } else {
                visitInsn(DUP)
                if (profile.usesProgramContracts) {
                    signature.output.pack(this)
                    signature.output.fold(this)
                    visitVarInsn(ILOAD, inputSlot)
                    visitInsn(IADD)
                }
                signature.intermediate.pack(this)
            }
        } else {
            if (profile.usesProgramContracts) visitVarInsn(ILOAD, inputSlot) else visitInsn(DUP)
            adaptCalculationArgument(profile.input)
            loadTopologyArguments(localResultSlot, dependencyResultSlot, wideContextSlot, dependencyCount)
            if (profile.usesProgramContracts) {
                visitVarInsn(ILOAD, resultSlot)
                signature.output.pack(this)
            }
            visitMethodInsn(INVOKESTATIC, prepareOwner, "prepare", signature.prepare, false)
        }
        visitVarInsn(ILOAD, inputSlot)
        adaptCalculationArgument(profile.input)
        loadTopologyArguments(localResultSlot, dependencyResultSlot, wideContextSlot, dependencyCount)
        if (signature.usesLegacyMerge) visitVarInsn(ILOAD, localResultSlot)
        visitMethodInsn(INVOKESTATIC, evaluateOwner, "evaluate", signature.evaluate, false)
        signature.output.fold(this)
        visitInsn(IXOR)
        visitVarInsn(ISTORE, resultSlot)
    }

    private fun MethodVisitor.loadTopologyArguments(
        localResultSlot: Int,
        dependencyResultSlot: Int,
        wideContextSlot: Int,
        dependencyCount: Int,
    ) {
        when (signature.context) {
            JunkTopologyContext.NONE -> Unit
            JunkTopologyContext.CHAIN -> visitVarInsn(LLOAD, wideContextSlot)
            JunkTopologyContext.PAIRS -> {
                visitVarInsn(ILOAD, localResultSlot)
                visitVarInsn(ILOAD, dependencyResultSlot)
            }
            JunkTopologyContext.TREE -> {
                visitVarInsn(LLOAD, wideContextSlot)
                visitLdcInsn(dependencyCount + 1)
            }
        }
    }

    private class Signature(profile: JunkGenerationProfile) {
        val intermediate = profile.roles.valueType
        val output = profile.program.resultType
        val usesLegacyMerge = !profile.usesTopologyStages && (profile.calls == Calls.PAIRS || profile.calls == Calls.TREE)
        val context = if (profile.usesTopologyStages) profile.calls.context else JunkTopologyContext.NONE
        private val preparationResult = if (profile.usesProgramContracts) output.descriptor else ""
        private val prepared = if (profile.usesPreparedOperands) intermediate.arrayDescriptor else intermediate.descriptor
        val prepare = "(${profile.input.parameters}${context.parameters}$preparationResult)$prepared"
        private val evaluateContext = if (usesLegacyMerge) "I" else context.parameters
        val evaluate = "($prepared${profile.input.parameters}$evaluateContext)${output.descriptor}"
    }

    companion object {
        fun forUnit(unit: JunkCodeUnit, profile: JunkGenerationProfile?): JunkSharedProgram? {
            val helpers = unit.sharedHelpers
            if (profile?.usesTypedStages != true || helpers.isEmpty()) return null
            val first = unit.index % helpers.size
            // Fan-out and paired units dispatch the stages to separate services. Chain and
            // tree units use a single service for preparation and evaluation.
            val second = when (profile.calls) {
                Calls.FAN_OUT, Calls.PAIRS -> (first + 1) % helpers.size
                Calls.CHAIN, Calls.TREE -> first
            }
            return JunkSharedProgram(profile, helpers[first], helpers[second])
        }

        fun emitMethods(writer: ClassWriter, random: Random, profile: JunkGenerationProfile) {
            val signature = Signature(profile)
            writer.method(ACC_PUBLIC or ACC_STATIC, "prepare", signature.prepare) {
                normalizeCalculationInput(profile.input, 0)
                if (profile.usesPreparedOperands) {
                    emitPreparedOperands(signature.intermediate,
                        input = { emitPreparationInput(profile, signature, random) },
                        programResult = { signature.output.loadFolded(this, profile.input.slots + signature.context.slots) })
                    visitInsn(ARETURN)
                } else {
                    emitPreparationInput(profile, signature, random)
                    signature.intermediate.pack(this)
                    visitInsn(signature.intermediate.returnOpcode)
                }
            }
            writer.method(ACC_PUBLIC or ACC_STATIC, "evaluate", signature.evaluate) {
                val intermediate = signature.intermediate
                val originalInput = if (profile.usesPreparedOperands) {
                    visitVarInsn(ALOAD, 0)
                    intermediate.loadFoldedElement(this, 0)
                    visitVarInsn(ALOAD, 0)
                    intermediate.loadFoldedElement(this, 1)
                    visitInsn(IXOR)
                    1
                } else {
                    intermediate.loadFolded(this, 0)
                    intermediate.slots
                }
                normalizeCalculationInput(profile.input, originalInput)
                visitVarInsn(ILOAD, originalInput)
                visitInsn(IADD)
                val contextSlot = originalInput + profile.input.slots
                if (signature.usesLegacyMerge) {
                    visitVarInsn(ILOAD, contextSlot)
                    visitInsn(IXOR)
                } else if (signature.context != JunkTopologyContext.NONE) {
                    signature.context.loadFolded(this, contextSlot)
                    visitInsn(IXOR)
                }
                val start = contextSlot + if (signature.usesLegacyMerge) 1 else signature.context.slots
                visitVarInsn(ISTORE, start)
                AndroidJunkBytecodeInject.composeInt(this, random, start, start + 1, start + 2, 8, program = profile.program)
                visitVarInsn(ILOAD, start + 1)
                signature.output.pack(this)
                visitInsn(signature.output.returnOpcode)
            }
        }

        private fun MethodVisitor.emitPreparationInput(profile: JunkGenerationProfile, signature: Signature, random: Random) {
            visitVarInsn(ILOAD, 0)
            if (signature.context != JunkTopologyContext.NONE) {
                signature.context.loadFolded(this, profile.input.slots)
                visitInsn(IADD)
            }
            if (profile.usesProgramContracts && !profile.usesPreparedOperands) {
                signature.output.loadFolded(this, profile.input.slots + signature.context.slots)
                visitInsn(IADD)
            }
            visitLdcInsn(random.nextInt(1, 32))
            visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I", false)
        }
    }
}

internal enum class JunkTopologyContext(val parameters: String, val slots: Int) {
    NONE("", 0), CHAIN("J", 2), PAIRS("II", 2), TREE("JI", 3),
    ;

    val usesOrderedValues get() = this == CHAIN || this == PAIRS

    fun loadFolded(visitor: MethodVisitor, slot: Int) = with(visitor) {
        when (this@JunkTopologyContext) {
            NONE -> error("No topology inputs")
            CHAIN -> {
                visitVarInsn(LLOAD, slot)
                visitLdcInsn(32)
                visitInsn(LUSHR)
                visitInsn(L2I)
                visitVarInsn(LLOAD, slot)
                visitInsn(L2I)
                visitInsn(ISUB)
            }
            PAIRS -> {
                visitVarInsn(ILOAD, slot)
                visitVarInsn(ILOAD, slot + 1)
                visitInsn(IADD)
            }
            TREE -> {
                visitVarInsn(LLOAD, slot)
                visitVarInsn(ILOAD, slot + 2)
                visitInsn(ICONST_1)
                visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(II)I", false)
                visitInsn(I2L)
                visitInsn(LDIV)
                visitInsn(L2I)
            }
        }
    }
}

internal fun MethodVisitor.packOrderedValues(firstSlot: Int, secondSlot: Int) {
    visitVarInsn(ILOAD, firstSlot)
    visitInsn(I2L)
    visitLdcInsn(32)
    visitInsn(LSHL)
    visitVarInsn(ILOAD, secondSlot)
    visitInsn(I2L)
    visitLdcInsn(0xffff_ffffL)
    visitInsn(LAND)
    visitInsn(LOR)
}

internal enum class JunkValueType(
    val descriptor: String,
    private val load: Int,
    val returnOpcode: Int,
    private val elementLoad: Int,
    private val elementStore: Int,
    val slots: Int = 1,
) {
    NUMBER("I", ILOAD, IRETURN, IALOAD, IASTORE),
    PREDICATE("Z", ILOAD, IRETURN, BALOAD, BASTORE),
    ACCUMULATOR("J", LLOAD, LRETURN, LALOAD, LASTORE, 2),
    TEXT("Ljava/lang/String;", ALOAD, ARETURN, AALOAD, AASTORE);

    val arrayDescriptor get() = "[$descriptor"

    fun pack(visitor: MethodVisitor) = with(visitor) {
        when (this@JunkValueType) {
            NUMBER -> Unit
            PREDICATE -> {
                visitInsn(ICONST_1)
                visitInsn(IAND)
            }
            ACCUMULATOR -> {
                visitInsn(I2L)
                visitLdcInsn(0x1_0000_01B3L)
                visitInsn(LMUL)
            }
            TEXT -> visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false)
        }
    }

    fun fold(visitor: MethodVisitor) = with(visitor) {
        when (this@JunkValueType) {
            NUMBER, PREDICATE -> Unit
            ACCUMULATOR -> {
                visitInsn(DUP2)
                visitLdcInsn(32)
                visitInsn(LUSHR)
                visitInsn(LXOR)
                visitInsn(L2I)
            }
            TEXT -> visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
        }
    }

    fun loadFolded(visitor: MethodVisitor, slot: Int) = with(visitor) {
        if (this@JunkValueType == TEXT) {
            loadStringHash(slot)
        } else {
            visitVarInsn(load, slot)
            fold(this)
        }
    }

    fun newArray(visitor: MethodVisitor) = with(visitor) {
        when (this@JunkValueType) {
            NUMBER -> visitIntInsn(NEWARRAY, T_INT)
            PREDICATE -> visitIntInsn(NEWARRAY, T_BOOLEAN)
            ACCUMULATOR -> visitIntInsn(NEWARRAY, T_LONG)
            TEXT -> visitTypeInsn(ANEWARRAY, "java/lang/String")
        }
    }

    fun loadFoldedElement(visitor: MethodVisitor, index: Int) = with(visitor) {
        visitLdcInsn(index)
        visitInsn(elementLoad)
        fold(this)
    }

    fun packAndStoreElement(visitor: MethodVisitor) = with(visitor) {
        pack(this)
        visitInsn(elementStore)
    }
}

/** The evaluator receives the normalized input and the program result as separate role values. */
private inline fun MethodVisitor.emitPreparedOperands(
    type: JunkValueType,
    input: MethodVisitor.() -> Unit,
    programResult: MethodVisitor.() -> Unit,
) {
    visitInsn(ICONST_2)
    type.newArray(this)
    visitInsn(DUP)
    visitInsn(ICONST_0)
    input()
    type.packAndStoreElement(this)
    visitInsn(DUP)
    visitInsn(ICONST_1)
    programResult()
    type.packAndStoreElement(this)
}

/** Adapt an int on the stack to the profile's calculation signature. */
internal fun MethodVisitor.adaptCalculationArgument(input: Input) {
    when (input) {
        Input.INTEGER -> Unit
        Input.PAIR -> {
            visitInsn(DUP)
            visitInsn(ICONST_1)
            visitInsn(IUSHR)
        }
        Input.WIDE -> visitInsn(I2L)
        Input.TEXT -> visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false)
    }
}

internal fun MethodVisitor.normalizeCalculationInput(input: Input, slot: Int) {
    when (input) {
        Input.INTEGER -> return
        Input.PAIR -> {
            visitVarInsn(ILOAD, slot)
            visitVarInsn(ILOAD, slot + 1)
            visitInsn(IADD)
        }
        Input.WIDE -> {
            visitVarInsn(LLOAD, slot)
            visitInsn(L2I)
        }
        Input.TEXT -> loadStringHash(slot)
    }
    visitVarInsn(ISTORE, slot)
}

private fun MethodVisitor.loadStringHash(slot: Int) {
    val present = Label()
    val ready = Label()
    visitVarInsn(ALOAD, slot)
    visitJumpInsn(IFNONNULL, present)
    visitInsn(ICONST_0)
    visitJumpInsn(GOTO, ready)
    visitLabel(present)
    visitVarInsn(ALOAD, slot)
    visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
    visitLabel(ready)
}

internal inline fun ClassWriter.method(access: Int, name: String, descriptor: String, body: MethodVisitor.() -> Unit) {
    val visitor = visitMethod(access, name, descriptor, null, null)
    visitor.visitCode()
    visitor.body()
    visitor.visitMaxs(0, 0)
    visitor.visitEnd()
}
