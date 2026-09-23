package org.tool.kit.data.generator

import org.objectweb.asm.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.MessageDigest

/** Measured AAR statistics. DEX/APK size is deliberately absent until a host build supplies it. */
data class JunkGenerationReport(val values: Map<String, Any?>) {
    fun toJson(): String = jsonValue(values) + "\n"
}

internal fun jsonValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + buildString {
        value.forEach { c -> append(when (c) {
            '"' -> "\\\""; '\\' -> "\\\\"; '\n' -> "\\n"; '\r' -> "\\r"; '\t' -> "\\t"
            else -> if (c.code < 32) "\\u%04x".format(c.code) else c.toString()
        }) }
    } + "\""
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { jsonValue(it.key.toString()) + ":" + jsonValue(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { jsonValue(it) }
    is Number, is Boolean -> value.toString()
    else -> jsonValue(value.toString())
}

internal fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8)).let { java.util.HexFormat.of().formatHex(it) }

/** Names and constants are normalized; this measures generator structure, not store similarity. */
internal class JunkCodeMetrics {
    var classBytes = 0L; private set
    var methods = 0L; private set
    var fields = 0L; private set
    var maxMethodCodeBytes = 0; private set
    var maxClassBytes = 0; private set
    val roles = sortedMapOf<String, Int>()
    private val structures = mutableMapOf<String, Int>()
    private var algorithmMethods = 0L

    @Synchronized fun add(bytes: ByteArray, role: String) {
        classBytes += bytes.size
        maxClassBytes = maxOf(maxClassBytes, bytes.size)
        roles[role] = (roles[role] ?: 0) + 1
        val methodLengths = codeLengths(bytes)
        require(methodLengths.all { it < 60_000 }) { "Method exceeds safe Code length (60000 bytes)" }
        maxMethodCodeBytes = maxOf(maxMethodCodeBytes, methodLengths.maxOrNull() ?: 0)
        ClassReader(bytes).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                check(access and Opcodes.ACC_MODULE == 0); fields++; return null
            }
            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                methods++
                val body = StringBuilder(normalize(descriptor))
                var count = 0
                val labels = mutableMapOf<Label, Int>()
                fun label(l: Label) = labels.getOrPut(l) { labels.size }
                fun token(s: String) { body.append('|').append(s); count++ }
                return object : MethodVisitor(Opcodes.ASM9) {
                    override fun visitInsn(opcode: Int) = token(when (opcode) {
                        in Opcodes.ICONST_M1..Opcodes.ICONST_5 -> "cInteger"
                        in Opcodes.LCONST_0..Opcodes.LCONST_1 -> "cLong"
                        in Opcodes.FCONST_0..Opcodes.FCONST_2 -> "cFloat"
                        in Opcodes.DCONST_0..Opcodes.DCONST_1 -> "cDouble"
                        else -> "o$opcode"
                    })
                    override fun visitIntInsn(opcode: Int, operand: Int) = token(if (opcode == Opcodes.NEWARRAY) "array$operand" else "cInteger")
                    override fun visitVarInsn(opcode: Int, slot: Int) = token("v$opcode:$slot")
                    override fun visitTypeInsn(opcode: Int, type: String) = token("t$opcode:${normalize(type)}")
                    override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) = token("f$opcode:${normalize(owner)}:${normalize(descriptor)}")
                    override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) = token("m$opcode:${normalize(owner)}:${if (isPlatformType(owner)) name else "member"}:${normalize(descriptor)}:$isInterface")
                    override fun visitJumpInsn(opcode: Int, target: Label) = token("j$opcode:${label(target)}")
                    override fun visitLabel(target: Label) { body.append("|l${label(target)}") }
                    override fun visitLdcInsn(value: Any) = token("c${value.javaClass.simpleName}")
                    override fun visitIincInsn(slot: Int, increment: Int) = token("inc$slot")
                    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) = token("switch${labels.size}")
                    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) = token("lookup${keys.size}")
                    override fun visitEnd() {
                        // Constructors/accessors are deliberately reported separately from algorithms.
                        if (name != "<init>" && count >= 12) {
                            algorithmMethods++
                            val key = fingerprint(body.toString()); structures[key] = (structures[key] ?: 0) + 1
                        }
                    }
                }
            }
        }, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    fun snapshot(): Map<String, Any> = linkedMapOf("classes" to roles.values.sum(), "classRoles" to roles,
        "methods" to methods, "fields" to fields, "classBytes" to classBytes, "maxClassBytes" to maxClassBytes,
        "maxMethodCodeBytes" to maxMethodCodeBytes, "algorithmMethods" to algorithmMethods,
        "uniqueNormalizedMethods" to structures.size, "mostRepeatedNormalizedMethodCount" to (structures.values.maxOrNull() ?: 0),
        "normalizedMethodDuplicateRate" to if (algorithmMethods == 0L) 0.0 else 1.0 - structures.size.toDouble() / algorithmMethods)

    private fun normalize(s: String): String = s.replace(Regex("L(?!java/|android/|androidx/)[^;]+;"), "Lgenerated;")
        .let { if ('/' in it && !isPlatformType(it) && !it.contains('(') && !it.startsWith('L')) "generated" else it }

    private fun isPlatformType(name: String): Boolean =
        name.startsWith("java/") || name.startsWith("android/") || name.startsWith("androidx/")
}

/** Reads actual Code attributes, rather than estimating their size from ASM instruction counts. */
internal fun codeLengths(bytes: ByteArray): List<Int> = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
    input.readInt(); input.readUnsignedShort(); input.readUnsignedShort()
    val utf = mutableMapOf<Int, String>()
    val poolSize = input.readUnsignedShort()
    var index = 1
    while (index < poolSize) {
        when (val tag = input.readUnsignedByte()) {
            1 -> utf[index] = input.readUTF()
            3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
            5, 6 -> { input.skipNBytes(8); index++ }
            7, 8, 16, 19, 20 -> input.skipNBytes(2)
            15 -> input.skipNBytes(3)
            else -> error("Unknown constant pool tag $tag")
        }; index++
    }
    input.skipNBytes(6); repeat(input.readUnsignedShort()) { input.skipNBytes(2) }
    fun skipAttributes() { repeat(input.readUnsignedShort()) { input.readUnsignedShort(); input.skipNBytes(input.readInt().toLong()) } }
    repeat(input.readUnsignedShort()) { input.skipNBytes(6); skipAttributes() }
    buildList {
        repeat(input.readUnsignedShort()) {
            input.skipNBytes(6)
            repeat(input.readUnsignedShort()) {
                val name = utf[input.readUnsignedShort()]; val length = input.readInt()
                if (name == "Code") {
                    input.skipNBytes(4); add(input.readInt()); input.skipNBytes((length - 8).toLong())
                } else input.skipNBytes(length.toLong())
            }
        }
    }
}
