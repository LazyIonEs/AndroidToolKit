package org.tool.kit.data.generator

import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ACC_INTERFACE
import org.objectweb.asm.Opcodes.ASM9
import org.objectweb.asm.Opcodes.INVOKEINTERFACE
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKESTATIC
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL

internal object JunkAarComparison {
    fun compare(archives: List<JunkProjectArchive>, output: File): JunkGenerationReport {
        val samples = archives.associate { it.project.projectId to analyze(it.file) }
        val pairs = buildList {
            for (left in archives.indices) {
                val leftId = archives[left].project.projectId
                val first = samples.getValue(leftId)
                for (right in left + 1 until archives.size) {
                    val rightId = archives[right].project.projectId
                    val second = samples.getValue(rightId)
                    add(Overlap(leftId, rightId, first.methods.overlap(second.methods),
                        first.classes.overlap(second.classes), first.calls.overlap(second.calls),
                        first.edges.fingerprints.compare(second.edges.fingerprints)))
                }
            }
        }
        return JunkGenerationReport(linkedMapOf(
            "schema" to 2,
            "scope" to "AAR classes.jar",
            "archives" to archives.map { archive ->
                val sample = samples.getValue(archive.project.projectId)
                linkedMapOf(
                    "projectId" to archive.project.projectId, "profileId" to archive.project.profileId,
                    "file" to archive.file.relativeTo(output).invariantSeparatorsPath, "activities" to archive.activities,
                    "methods" to sample.methods.size, "classes" to sample.classes.size, "callPatterns" to sample.calls.size,
                    "callEdges" to sample.edges.fingerprints.size, "callEdgeHistogram" to sample.edges.histogram(),
                )
            },
            "pairCount" to pairs.size,
            "highestPairs" to linkedMapOf(
                "methods" to pairs.sortedByDescending { it.methods }.take(5).map { it.snapshot() },
                "classes" to pairs.sortedByDescending { it.classes }.take(5).map { it.snapshot() },
                "calls" to pairs.sortedByDescending { it.calls }.take(5).map { it.snapshot() },
                "callEdges" to pairs.sortedByDescending { it.edges.rate }.take(5).map { pair ->
                    val leading = samples.getValue(pair.left).edges.leadingMatches(samples.getValue(pair.right).edges)
                    pair.snapshot().apply { put("leadingCallEdges", leading) }
                },
            ),
            "pairs" to pairs.map { it.snapshot() },
        ))
    }

    private data class Overlap(
        val left: String, val right: String, val methods: Double, val classes: Double, val calls: Double, val edges: MatchCount,
    ) {
        fun snapshot() = linkedMapOf<String, Any>(
            "left" to left, "right" to right, "methodOverlap" to methods, "classStructureOverlap" to classes,
            "callPatternOverlap" to calls, "callEdgeOverlap" to edges.rate,
            "matchedCallEdges" to edges.matched, "comparedCallEdges" to edges.compared,
        )
    }

    private data class MatchCount(val matched: Int, val compared: Int) {
        val rate get() = if (compared == 0) 0.0 else matched.toDouble() / compared
    }

    private class Fingerprints<K>(private val counts: Map<K, Int>) {
        val size = counts.values.sum()

        fun overlap(other: Fingerprints<K>): Double = compare(other).rate

        fun compare(other: Fingerprints<K>): MatchCount {
            val (smaller, larger) = if (counts.size <= other.counts.size) counts to other.counts else other.counts to counts
            val matched = smaller.entries.sumOf { (key, count) -> minOf(count, larger[key] ?: 0) }
            return MatchCount(matched, minOf(size, other.size))
        }
    }

    private data class CallEdge(val callerKind: String, val targetKind: String, val opcode: Int, val descriptor: String) {
        fun snapshot() = linkedMapOf<String, Any>(
            "callerKind" to callerKind,
            "targetKind" to targetKind,
            "opcode" to when (opcode) {
                INVOKEINTERFACE -> "INVOKEINTERFACE"
                INVOKESPECIAL -> "INVOKESPECIAL"
                INVOKESTATIC -> "INVOKESTATIC"
                INVOKEVIRTUAL -> "INVOKEVIRTUAL"
                else -> opcode.toString()
            },
            "descriptor" to descriptor,
        )
    }

    private class EdgeOccurrences {
        var count = 0
        val calleeNames = sortedSetOf<String>()
    }

    private class CallEdgeStats(private val occurrences: Map<CallEdge, EdgeOccurrences>) {
        val fingerprints = Fingerprints(occurrences.mapValues { it.value.count })

        fun histogram() = occurrences.entries.sortedByDescending { it.value.count }.map { (edge, occurrence) ->
            edge.snapshot().apply {
                put("count", occurrence.count)
                put("calleeNames", occurrence.calleeNames)
            }
        }

        fun leadingMatches(other: CallEdgeStats): List<Map<String, Any>> {
            val shared = occurrences.mapNotNull { (edge, left) ->
                val right = other.occurrences[edge] ?: return@mapNotNull null
                edge to minOf(left.count, right.count)
            }
            return shared.sortedByDescending { it.second }.take(5).map { (edge, matched) ->
                val left = occurrences.getValue(edge)
                val right = other.occurrences.getValue(edge)
                edge.snapshot().apply {
                    put("matched", matched)
                    put("leftCount", left.count)
                    put("rightCount", right.count)
                    put("leftCalleeNames", left.calleeNames)
                    put("rightCalleeNames", right.calleeNames)
                }
            }
        }
    }

    private data class CallSite(val opcode: Int, val name: String, val descriptor: String) {
        fun patternToken() = "$opcode:$descriptor"
    }

    private data class CallTarget(val kind: String, val sites: List<CallSite>) {
        fun patternToken() = "$kind:${sites.map { it.patternToken() }.sorted()}"
    }

    private data class ArchiveSignatures(
        val methods: Fingerprints<String>, val classes: Fingerprints<String>, val calls: Fingerprints<String>,
        val edges: CallEdgeStats,
    )

    private fun analyze(file: File): ArchiveSignatures {
        val methods = JunkCodeMetrics()
        val classes = mutableMapOf<String, ClassStructure>()
        ZipFile(file).use { archive ->
            val classesJar = requireNotNull(archive.getEntry("classes.jar")) { "No classes.jar in $file" }
            ZipInputStream(archive.getInputStream(classesJar).buffered()).use { jar ->
                while (true) {
                    val entry = jar.nextEntry ?: break
                    if (!entry.name.endsWith(".class")) continue
                    val bytes = jar.readBytes()
                    val reader = ClassReader(bytes)
                    val structure = ClassStructure(reader)
                    reader.accept(structure, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
                    check(classes.put(reader.className, structure) == null) { "Duplicate class ${reader.className} in $file" }
                    methods.add(bytes, structure.kind)
                }
            }
        }
        require(classes.isNotEmpty()) { "No classes in $file" }
        val callPatterns = mutableMapOf<String, Int>()
        val callEdges = mutableMapOf<CallEdge, EdgeOccurrences>()
        for (source in classes.values) {
            val targets = source.targetsWithin(classes)
            if (targets.isEmpty()) continue
            // Preserve the complete fingerprint's per-owner grouping. Count individual bytecode
            // call sites from those same targets, including repeated calls to the same method.
            val pattern = fingerprint("${source.kind}|${targets.map { it.patternToken() }.sorted()}")
            callPatterns.merge(pattern, 1, Int::plus)
            for (target in targets) {
                for (site in target.sites) {
                    val edge = CallEdge(source.kind, target.kind, site.opcode, site.descriptor)
                    val occurrence = callEdges.getOrPut(edge) { EdgeOccurrences() }
                    occurrence.count++
                    occurrence.calleeNames += site.name
                }
            }
        }
        return ArchiveSignatures(
            Fingerprints(methods.methodFingerprints()),
            Fingerprints(classes.values.groupingBy { it.signature() }.eachCount()),
            Fingerprints(callPatterns),
            CallEdgeStats(callEdges),
        )
    }

    private class ClassStructure(reader: ClassReader) : ClassVisitor(ASM9) {
        private val name = reader.className
        private val parent = reader.superName?.let(::normalizeGeneratedType).orEmpty()
        private val interfaces = reader.interfaces.map(::normalizeGeneratedType).sorted()
        private val access = reader.access
        val kind = if (access and ACC_INTERFACE != 0) "interface" else parent
        private val fields = mutableListOf<String>()
        private val methods = mutableListOf<String>()
        private val calls = mutableMapOf<String, MutableList<CallSite>>()

        override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
            fields += "$access:${normalizeGeneratedType(descriptor)}"
            return null
        }

        override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
            val specialName = if (name.startsWith('<')) name else ""
            methods += "$access:$specialName:${normalizeGeneratedType(descriptor)}"
            return object : MethodVisitor(ASM9) {
                override fun visitMethodInsn(opcode: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                    if (owner != this@ClassStructure.name && name != "<init>") {
                        calls.getOrPut(owner) { mutableListOf() } += CallSite(opcode, name, normalizeGeneratedType(descriptor))
                    }
                }
            }
        }

        fun signature(): String = fingerprint("$access|$parent|$interfaces|${fields.sorted()}|${methods.sorted()}")

        fun targetsWithin(classes: Map<String, ClassStructure>): List<CallTarget> {
            return calls.mapNotNull { (owner, sites) -> classes[owner]?.let { CallTarget(it.kind, sites) } }
        }
    }
}
