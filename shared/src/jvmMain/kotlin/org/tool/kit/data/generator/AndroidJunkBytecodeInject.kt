package org.tool.kit.data.generator

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.IADD
import org.objectweb.asm.Opcodes.IAND
import org.objectweb.asm.Opcodes.ICONST_0
import org.objectweb.asm.Opcodes.ICONST_1
import org.objectweb.asm.Opcodes.IFNE
import org.objectweb.asm.Opcodes.IF_ICMPGE
import org.objectweb.asm.Opcodes.ILOAD
import org.objectweb.asm.Opcodes.IMUL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.IREM
import org.objectweb.asm.Opcodes.ISTORE
import org.objectweb.asm.Opcodes.IUSHR
import org.objectweb.asm.Opcodes.IXOR
import org.tool.kit.data.generator.JunkGenerationProfile.Calls
import org.tool.kit.data.generator.JunkGenerationProfile.Input
import org.tool.kit.data.generator.JunkGenerationProfile.Program
import org.tool.kit.data.generator.JunkGenerationProfile.Roles
import kotlin.random.Random

/**
 * Small, composable operations used by generated methods. All operations are pure, API 21 safe,
 * usable from static methods, and require no Context. There are no process-global random sources,
 * UI actions, logging, storage writes, permissions, or callbacks into application code.
 *
 * The caller owns the method, locals and return instruction. Each operation starts and ends with
 * an empty operand stack and updates one integer local. Loops have at most eight iterations;
 * divisors are positive constants. This replaces the old 288-way side-effecting snippet injector.
 */
object AndroidJunkBytecodeInject {
    private enum class Operation {
        ARITHMETIC, BIT_MIX, CONDITIONAL_FOLD, BOUNDED_ACCUMULATION, SAFE_REMAINDER,
        ROTATE, RANGE_FOLD, DECIMAL_MEASURE, SHARED_HELPER,
    }

    private val standaloneOperations = Operation.entries.dropLast(1)
    private val programOperations = mapOf(
        Program.ARITHMETIC to listOf(Operation.ARITHMETIC, Operation.BIT_MIX, Operation.SAFE_REMAINDER, Operation.ROTATE),
        Program.BRANCHING to listOf(Operation.CONDITIONAL_FOLD, Operation.RANGE_FOLD, Operation.BIT_MIX, Operation.SAFE_REMAINDER),
        Program.ACCUMULATION to listOf(Operation.BOUNDED_ACCUMULATION, Operation.ARITHMETIC, Operation.RANGE_FOLD, Operation.ROTATE),
        Program.TEXT to listOf(Operation.DECIMAL_MEASURE, Operation.SAFE_REMAINDER, Operation.CONDITIONAL_FOLD, Operation.RANGE_FOLD),
    )
    private val shortMethodStarts = mapOf(
        Roles.MODELS to listOf(Operation.RANGE_FOLD, Operation.ARITHMETIC),
        Roles.RULES to listOf(Operation.CONDITIONAL_FOLD, Operation.SAFE_REMAINDER),
        Roles.COLLECTIONS to listOf(Operation.BOUNDED_ACCUMULATION, Operation.ROTATE),
        Roles.CONFIGURATION to listOf(Operation.BIT_MIX, Operation.DECIMAL_MEASURE),
    )

    data class OperationMetadata(
        val name: String,
        val minApi: Int = 21,
        val requiresContext: Boolean = false,
        val supportsStatic: Boolean = true,
        val externalSideEffects: Boolean = false,
        val maxLoopIterations: Int = 0
    )

    val operations: List<OperationMetadata> = listOf(
        OperationMetadata("arithmetic"), OperationMetadata("bit_mix"),
        OperationMetadata("conditional_fold"), OperationMetadata("bounded_accumulation", maxLoopIterations = 8),
        OperationMetadata("safe_remainder"), OperationMetadata("rotate"),
        OperationMetadata("range_fold"), OperationMetadata("decimal_measure"),
        OperationMetadata("shared_helper")
    )

    internal fun composeInt(
        mv: MethodVisitor,
        random: Random,
        inputSlot: Int,
        resultSlot: Int,
        scratchSlot: Int,
        maxOperations: Int,
        sharedHelper: String? = null,
        program: Program? = null,
        input: Input = Input.INTEGER,
        stepProfile: JunkGenerationProfile? = null,
    ) {
        mv.visitVarInsn(ILOAD, inputSlot)
        when (input) {
            Input.INTEGER -> Unit
            Input.PAIR -> {
                mv.adaptCalculationArgument(input)
                mv.visitInsn(IXOR)
            }
            Input.WIDE -> {
                JunkValueType.ACCUMULATOR.pack(mv)
                JunkValueType.ACCUMULATOR.fold(mv)
            }
            Input.TEXT -> {
                mv.adaptCalculationArgument(input)
                JunkValueType.TEXT.fold(mv)
            }
        }
        mv.visitVarInsn(ISTORE, resultSlot)
        val budget = maxOperations.coerceIn(1, 48)
        val count = random.nextInt(minOf(3, budget), budget + 1)
        val shortProfile = stepProfile?.takeIf { it.usesRoleSpecificMethods && count <= 6 }
        // V12 short sequences start with the program's core operation. The role chooses the
        // next operation when the budget permits, and the remaining draws use the full pool.
        val roleOperationIndex = if (shortProfile?.usesProgramSpecificMethods == true) 1 else 0
        val shortStart = shortProfile?.takeIf { roleOperationIndex < count }
            ?.let { shortMethodStarts.getValue(it.roles).random(random) }
        val programPool = (stepProfile?.program ?: program)?.let { programOperations.getValue(it) }
        val operationPool = when {
            shortStart != null && sharedHelper == null -> standaloneOperations
            shortStart != null -> Operation.entries
            programPool == null -> if (sharedHelper == null) standaloneOperations else Operation.entries
            sharedHelper == null -> programPool
            else -> programPool + Operation.SHARED_HELPER
        }
        fun emitOperation(index: Int, slot: Int) {
            val operation = when {
                index == roleOperationIndex && shortStart != null -> shortStart
                index == 0 && programPool != null -> programPool.first()
                else -> operationPool.random(random)
            }
            when (operation) {
                Operation.ARITHMETIC -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(1, 97))
                    mv.visitInsn(if (random.nextBoolean()) IADD else IMUL)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.BIT_MIX -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(1, 16))
                    mv.visitInsn(IUSHR)
                    mv.visitInsn(IXOR)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.CONDITIONAL_FOLD -> {
                    val odd = Label()
                    val end = Label()
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitInsn(ICONST_1)
                    mv.visitInsn(IAND)
                    mv.visitJumpInsn(IFNE, odd)
                    mv.visitIincInsn(slot, random.nextInt(1, 31))
                    mv.visitJumpInsn(GOTO, end)
                    mv.visitLabel(odd)
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(3, 33))
                    mv.visitInsn(IXOR)
                    mv.visitVarInsn(ISTORE, slot)
                    mv.visitLabel(end)
                }
                Operation.BOUNDED_ACCUMULATION -> {
                    val start = Label()
                    val end = Label()
                    mv.visitInsn(ICONST_0)
                    mv.visitVarInsn(ISTORE, scratchSlot)
                    mv.visitLabel(start)
                    mv.visitVarInsn(ILOAD, scratchSlot)
                    mv.visitLdcInsn(random.nextInt(2, 9))
                    mv.visitJumpInsn(IF_ICMPGE, end)
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitVarInsn(ILOAD, scratchSlot)
                    mv.visitInsn(if (random.nextBoolean()) IADD else IXOR)
                    mv.visitVarInsn(ISTORE, slot)
                    mv.visitIincInsn(scratchSlot, 1)
                    mv.visitJumpInsn(GOTO, start)
                    mv.visitLabel(end)
                }
                Operation.SAFE_REMAINDER -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(2, 257))
                    mv.visitInsn(IREM)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.ROTATE -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(1, 31))
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I", false)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.RANGE_FOLD -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitLdcInsn(random.nextInt(31, 8192))
                    mv.visitInsn(IAND)
                    mv.visitLdcInsn(random.nextInt(1, 64))
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", if (random.nextBoolean()) "max" else "min", "(II)I", false)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.DECIMAL_MEASURE -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false)
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitInsn(IADD)
                    mv.visitVarInsn(ISTORE, slot)
                }
                Operation.SHARED_HELPER -> {
                    mv.visitVarInsn(ILOAD, slot)
                    mv.visitMethodInsn(INVOKESTATIC, requireNotNull(sharedHelper), "mix", "(I)I", false)
                    mv.visitVarInsn(ISTORE, slot)
                }
            }
        }

        fun copyInt(from: Int, to: Int) {
            mv.visitVarInsn(ILOAD, from)
            mv.visitVarInsn(ISTORE, to)
        }

        fun mergeIntoResult(slot: Int, opcode: Int) {
            mv.visitVarInsn(ILOAD, resultSlot)
            mv.visitVarInsn(ILOAD, slot)
            mv.visitInsn(opcode)
            mv.visitVarInsn(ISTORE, resultSlot)
        }

        // Step methods own the locals after scratchSlot. Draw operations in index order
        // so topology only changes how values flow between the same operation choices.
        val topology = stepProfile?.takeIf { it.usesTopologyIntSteps && count > 1 }?.calls ?: Calls.CHAIN
        val branchSlot = scratchSlot + 1
        when (topology) {
            Calls.CHAIN -> repeat(count) { emitOperation(it, resultSlot) }
            Calls.FAN_OUT -> {
                val valueSlot = scratchSlot + 2
                copyInt(resultSlot, branchSlot)
                mv.visitInsn(ICONST_0)
                mv.visitVarInsn(ISTORE, resultSlot)
                repeat(count) { index ->
                    copyInt(branchSlot, valueSlot)
                    emitOperation(index, valueSlot)
                    mergeIntoResult(valueSlot, IADD)
                }
            }
            Calls.PAIRS -> {
                for (index in 0 until count - 1 step 2) {
                    copyInt(resultSlot, branchSlot)
                    emitOperation(index, resultSlot)
                    emitOperation(index + 1, branchSlot)
                    mergeIntoResult(branchSlot, IXOR)
                }
                if (count % 2 == 1) emitOperation(count - 1, resultSlot)
            }
            Calls.TREE -> {
                copyInt(resultSlot, branchSlot)
                val middle = (count + 1) / 2
                repeat(middle) { emitOperation(it, resultSlot) }
                for (index in middle until count) emitOperation(index, branchSlot)
                mergeIntoResult(branchSlot, IADD)
            }
        }
    }
}
