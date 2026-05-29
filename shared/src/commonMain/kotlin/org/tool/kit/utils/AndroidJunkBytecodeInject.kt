package org.tool.kit.utils

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import kotlin.random.Random

object AndroidJunkBytecodeInject {
    fun injectRandomBytecode(mv: MethodVisitor, className: String, generateResName: () -> String, generateBigValue: () -> String) {
        val snippetType = Random.nextInt(288)
        when (snippetType) {
            0 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            1 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            2 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "tan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            3 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "asin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            4 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "acos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            5 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "atan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            6 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "exp", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            7 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            8 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log10", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            9 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            10 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cbrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            11 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            12 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            13 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            14 -> {
            mv.visitLdcInsn(Random.nextFloat())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            15 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            16 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            17 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
            mv.visitInsn(Opcodes.POP)
            }
            18 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            19 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            20 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "compareTo", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            21 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            22 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            23 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            24 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            25 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            26 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            27 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn("A")
            mv.visitLdcInsn("B")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            28 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            29 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            30 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            31 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            32 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false)
            mv.visitInsn(Opcodes.POP)
            }
            33 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            34 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            35 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            36 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "reverseBytes", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            37 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "highestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            38 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "lowestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            39 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfLeadingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            40 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfTrailingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            41 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "reverseBytes", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            42 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "highestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            43 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "lowestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            44 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfLeadingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            45 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfTrailingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            46 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/UUID", "randomUUID", "()Ljava/util/UUID;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/UUID", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            47 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            48 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            49 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            50 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "clear", "()V", false)
            }
            51 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "()[Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            52 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            53 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            54 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            55 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "keySet", "()Ljava/util/Set;", false)
            mv.visitInsn(Opcodes.POP)
            }
            56 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "values", "()Ljava/util/Collection;", false)
            mv.visitInsn(Opcodes.POP)
            }
            57 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "getTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            58 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            59 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "getTimeInMillis", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            60 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "get", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            61 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            62 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            63 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/IllegalArgumentException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            64 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/IOException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/IOException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/IOException", "getLocalizedMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            65 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/NullPointerException", "fillInStackTrace", "()Ljava/lang/Throwable;", false)
            mv.visitInsn(Opcodes.POP)
            }
            66 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/SecurityException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            67 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            68 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            69 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isFile", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            70 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            71 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            72 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            73 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "putInt", "(Ljava/lang/String;I)V", false)
            }
            74 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "containsKey", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            75 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            76 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "clear", "()V", false)
            }
            77 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("android.intent.action.VIEW")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setAction", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            78 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "addCategory", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            79 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "putExtra", "(Ljava/lang/String;I)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            80 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("text/plain")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setType", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            81 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "getExtras", "()Landroid/os/Bundle;", false)
            mv.visitInsn(Opcodes.POP)
            }
            82 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getInt", "(Ljava/lang/String;I)I", true)
            mv.visitInsn(Opcodes.POP)
            }
            83 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getBoolean", "(Ljava/lang/String;Z)Z", true)
            mv.visitInsn(Opcodes.POP)
            }
            84 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getAll", "()Ljava/util/Map;", true)
            mv.visitInsn(Opcodes.POP)
            }
            85 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "edit", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "clear", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "apply", "()V", true)
            }
            86 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isEmpty", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            87 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            88 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "getTrimmedLength", "(Ljava/lang/CharSequence;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            89 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isDigitsOnly", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            90 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "encodeToString", "([BI)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            91 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "decode", "(Ljava/lang/String;I)[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            92 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
            mv.visitInsn(Opcodes.ARRAYLENGTH)
            mv.visitInsn(Opcodes.POP)
            }
            93 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT)
            mv.visitInsn(Opcodes.POP)
            }
            94 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
            mv.visitInsn(Opcodes.POP)
            }
            95 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([B)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            96 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            97 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            98 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "tan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            99 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "asin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            100 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "acos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            101 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "atan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            102 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "exp", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            103 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            104 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log10", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            105 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            106 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cbrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            107 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            108 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            109 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            110 -> {
            mv.visitLdcInsn(Random.nextFloat())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            111 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            112 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            113 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
            mv.visitInsn(Opcodes.POP)
            }
            114 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            115 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            116 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "compareTo", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            117 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            118 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            119 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            120 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            121 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            122 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            123 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn("A")
            mv.visitLdcInsn("B")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            124 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            125 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            126 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            127 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            128 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false)
            mv.visitInsn(Opcodes.POP)
            }
            129 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            130 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            131 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            132 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "reverseBytes", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            133 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "highestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            134 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "lowestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            135 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfLeadingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            136 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfTrailingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            137 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "reverseBytes", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            138 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "highestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            139 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "lowestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            140 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfLeadingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            141 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfTrailingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            142 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/UUID", "randomUUID", "()Ljava/util/UUID;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/UUID", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            143 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            144 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            145 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            146 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "clear", "()V", false)
            }
            147 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "()[Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            148 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            149 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            150 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            151 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "keySet", "()Ljava/util/Set;", false)
            mv.visitInsn(Opcodes.POP)
            }
            152 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "values", "()Ljava/util/Collection;", false)
            mv.visitInsn(Opcodes.POP)
            }
            153 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "getTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            154 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            155 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "getTimeInMillis", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            156 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "get", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            157 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            158 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            159 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/IllegalArgumentException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            160 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/IOException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/IOException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/IOException", "getLocalizedMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            161 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/NullPointerException", "fillInStackTrace", "()Ljava/lang/Throwable;", false)
            mv.visitInsn(Opcodes.POP)
            }
            162 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/SecurityException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            163 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            164 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            165 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isFile", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            166 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            167 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            168 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            169 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "putInt", "(Ljava/lang/String;I)V", false)
            }
            170 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "containsKey", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            171 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            172 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "clear", "()V", false)
            }
            173 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("android.intent.action.VIEW")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setAction", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            174 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "addCategory", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            175 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "putExtra", "(Ljava/lang/String;I)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            176 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("text/plain")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setType", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            177 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "getExtras", "()Landroid/os/Bundle;", false)
            mv.visitInsn(Opcodes.POP)
            }
            178 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getInt", "(Ljava/lang/String;I)I", true)
            mv.visitInsn(Opcodes.POP)
            }
            179 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getBoolean", "(Ljava/lang/String;Z)Z", true)
            mv.visitInsn(Opcodes.POP)
            }
            180 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getAll", "()Ljava/util/Map;", true)
            mv.visitInsn(Opcodes.POP)
            }
            181 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "edit", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "clear", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "apply", "()V", true)
            }
            182 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isEmpty", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            183 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            184 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "getTrimmedLength", "(Ljava/lang/CharSequence;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            185 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isDigitsOnly", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            186 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "encodeToString", "([BI)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            187 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "decode", "(Ljava/lang/String;I)[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            188 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
            mv.visitInsn(Opcodes.ARRAYLENGTH)
            mv.visitInsn(Opcodes.POP)
            }
            189 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT)
            mv.visitInsn(Opcodes.POP)
            }
            190 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
            mv.visitInsn(Opcodes.POP)
            }
            191 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([B)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            192 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            193 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            194 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "tan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            195 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "asin", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            196 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "acos", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            197 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "atan", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            198 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "exp", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            199 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            200 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "log10", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            201 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "sqrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            202 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "cbrt", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            203 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "ceil", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            204 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "floor", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            205 -> {
            mv.visitLdcInsn(Random.nextDouble())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "rint", "(D)D", false)
            mv.visitInsn(Opcodes.POP2)
            }
            206 -> {
            mv.visitLdcInsn(Random.nextFloat())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            207 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            208 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            209 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false)
            mv.visitInsn(Opcodes.POP)
            }
            210 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            211 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equalsIgnoreCase", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            212 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "compareTo", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            213 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            214 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "endsWith", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            215 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            216 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "lastIndexOf", "(Ljava/lang/String;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            217 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "substring", "(I)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            218 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            219 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn("A")
            mv.visitLdcInsn("B")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "replace", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            220 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            221 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toUpperCase", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            222 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "trim", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            223 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            224 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C", false)
            mv.visitInsn(Opcodes.POP)
            }
            225 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            226 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            227 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/Object")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "hashCode", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            228 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "reverseBytes", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            229 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "highestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            230 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "lowestOneBit", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            231 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfLeadingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            232 -> {
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "numberOfTrailingZeros", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            233 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "reverseBytes", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            234 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "highestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            235 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "lowestOneBit", "(J)J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            236 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfLeadingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            237 -> {
            mv.visitLdcInsn(Random.nextLong())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "numberOfTrailingZeros", "(J)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            238 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/UUID", "randomUUID", "()Ljava/util/UUID;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/UUID", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            239 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            240 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            241 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            242 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "clear", "()V", false)
            }
            243 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "toArray", "()[Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            244 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false)
            mv.visitInsn(Opcodes.POP)
            }
            245 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            246 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "containsKey", "(Ljava/lang/Object;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            247 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "keySet", "()Ljava/util/Set;", false)
            mv.visitInsn(Opcodes.POP)
            }
            248 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/HashMap")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/HashMap", "values", "()Ljava/util/Collection;", false)
            mv.visitInsn(Opcodes.POP)
            }
            249 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "getTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            250 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/util/Date")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/Date", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Date", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            251 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "getTimeInMillis", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            252 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Calendar", "getInstance", "()Ljava/util/Calendar;", false)
            mv.visitInsn(Opcodes.ICONST_1)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Calendar", "get", "(I)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            253 -> {
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false)
            mv.visitInsn(Opcodes.POP2)
            }
            254 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/RuntimeException", "getMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            255 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/IllegalArgumentException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            256 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/IOException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/IOException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/IOException", "getLocalizedMessage", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            257 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/NullPointerException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/NullPointerException", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/NullPointerException", "fillInStackTrace", "()Ljava/lang/Throwable;", false)
            mv.visitInsn(Opcodes.POP)
            }
            258 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/SecurityException")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/SecurityException", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/SecurityException", "toString", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            259 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "exists", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            260 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isDirectory", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            261 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "isFile", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            262 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getName", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            263 -> {
            mv.visitTypeInsn(Opcodes.NEW, "java/io/File")
            mv.visitInsn(Opcodes.DUP)
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/io/File", "<init>", "(Ljava/lang/String;)V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/File", "getAbsolutePath", "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            264 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "isEmpty", "()Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            265 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "putInt", "(Ljava/lang/String;I)V", false)
            }
            266 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "containsKey", "(Ljava/lang/String;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            267 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "size", "()I", false)
            mv.visitInsn(Opcodes.POP)
            }
            268 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/os/Bundle")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/os/Bundle", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/os/Bundle", "clear", "()V", false)
            }
            269 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("android.intent.action.VIEW")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setAction", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            270 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "addCategory", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            271 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn(generateResName())
            mv.visitLdcInsn(Random.nextInt())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "putExtra", "(Ljava/lang/String;I)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            272 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitLdcInsn("text/plain")
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "setType", "(Ljava/lang/String;)Landroid/content/Intent;", false)
            mv.visitInsn(Opcodes.POP)
            }
            273 -> {
            mv.visitTypeInsn(Opcodes.NEW, "android/content/Intent")
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/content/Intent", "<init>", "()V", false)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/Intent", "getExtras", "()Landroid/os/Bundle;", false)
            mv.visitInsn(Opcodes.POP)
            }
            274 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getInt", "(Ljava/lang/String;I)I", true)
            mv.visitInsn(Opcodes.POP)
            }
            275 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getBoolean", "(Ljava/lang/String;Z)Z", true)
            mv.visitInsn(Opcodes.POP)
            }
            276 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "getAll", "()Ljava/util/Map;", true)
            mv.visitInsn(Opcodes.POP)
            }
            277 -> {
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitLdcInsn(generateResName())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getSharedPreferences", "(Ljava/lang/String;I)Landroid/content/SharedPreferences;", false)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences", "edit", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "clear", "()Landroid/content/SharedPreferences\$Editor;", true)
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "android/content/SharedPreferences\$Editor", "apply", "()V", true)
            }
            278 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isEmpty", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            279 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitLdcInsn(generateResName())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "equals", "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            280 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "getTrimmedLength", "(Ljava/lang/CharSequence;)I", false)
            mv.visitInsn(Opcodes.POP)
            }
            281 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/text/TextUtils", "isDigitsOnly", "(Ljava/lang/CharSequence;)Z", false)
            mv.visitInsn(Opcodes.POP)
            }
            282 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "getBytes", "()[B", false)
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "encodeToString", "([BI)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
            283 -> {
            mv.visitLdcInsn(generateBigValue())
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Base64", "decode", "(Ljava/lang/String;I)[B", false)
            mv.visitInsn(Opcodes.POP)
            }
            284 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
            mv.visitInsn(Opcodes.ARRAYLENGTH)
            mv.visitInsn(Opcodes.POP)
            }
            285 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_FLOAT)
            mv.visitInsn(Opcodes.POP)
            }
            286 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String")
            mv.visitInsn(Opcodes.POP)
            }
            287 -> {
            mv.visitLdcInsn(Random.nextInt(1, 100))
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Arrays", "toString", "([B)Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
            }
        }
    }
}
