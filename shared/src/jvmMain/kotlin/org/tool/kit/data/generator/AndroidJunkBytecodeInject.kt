package org.tool.kit.data.generator

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
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
        sharedHelper: String? = null
    ) {
        mv.visitVarInsn(ILOAD, inputSlot)
        mv.visitVarInsn(ISTORE, resultSlot)
        val budget = maxOperations.coerceIn(1, 48)
        val count = random.nextInt(minOf(3, budget), budget + 1)
        repeat(count) {
            when (random.nextInt(if (sharedHelper == null) 8 else 9)) {
                0 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(1, 97))
                    mv.visitInsn(if (random.nextBoolean()) IADD else IMUL)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                1 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(1, 16))
                    mv.visitInsn(IUSHR)
                    mv.visitInsn(IXOR)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                2 -> {
                    val odd = Label()
                    val end = Label()
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitInsn(ICONST_1)
                    mv.visitInsn(IAND)
                    mv.visitJumpInsn(IFNE, odd)
                    mv.visitIincInsn(resultSlot, random.nextInt(1, 31))
                    mv.visitJumpInsn(GOTO, end)
                    mv.visitLabel(odd)
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(3, 33))
                    mv.visitInsn(IXOR)
                    mv.visitVarInsn(ISTORE, resultSlot)
                    mv.visitLabel(end)
                }
                3 -> {
                    val start = Label()
                    val end = Label()
                    mv.visitInsn(ICONST_0)
                    mv.visitVarInsn(ISTORE, scratchSlot)
                    mv.visitLabel(start)
                    mv.visitVarInsn(ILOAD, scratchSlot)
                    mv.visitLdcInsn(random.nextInt(2, 9))
                    mv.visitJumpInsn(IF_ICMPGE, end)
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitVarInsn(ILOAD, scratchSlot)
                    mv.visitInsn(if (random.nextBoolean()) IADD else IXOR)
                    mv.visitVarInsn(ISTORE, resultSlot)
                    mv.visitIincInsn(scratchSlot, 1)
                    mv.visitJumpInsn(GOTO, start)
                    mv.visitLabel(end)
                }
                4 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(2, 257))
                    mv.visitInsn(IREM)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                5 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(1, 31))
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "rotateLeft", "(II)I", false)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                6 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitLdcInsn(random.nextInt(31, 8192))
                    mv.visitInsn(IAND)
                    mv.visitLdcInsn(random.nextInt(1, 64))
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Math", if (random.nextBoolean()) "max" else "min", "(II)I", false)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                7 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false)
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitInsn(IADD)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
                8 -> {
                    mv.visitVarInsn(ILOAD, resultSlot)
                    mv.visitMethodInsn(INVOKESTATIC, requireNotNull(sharedHelper), "mix", "(I)I", false)
                    mv.visitVarInsn(ISTORE, resultSlot)
                }
            }
        }
    }
}
