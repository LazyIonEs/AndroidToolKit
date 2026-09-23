package org.tool.kit.data.generator

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/** Plans deterministic Activity units, bounds optional resources, and publishes only complete AARs. */
class AndroidJunkGenerator(
    private val dir: String,
    private val output: String,
    private val appPackageName: String,
    private val packageCount: Int,
    private val activityCountPerPackage: Int,
    private val resPrefix: String,
    private val policy: JunkGenerationPolicy = JunkGenerationPolicy(),
    private val checkCancelled: () -> Unit = {},
    private val onProgress: (JunkGenerationProgress) -> Unit = {},
    private val logReport: Boolean = true,
) {
    var lastReport: JunkGenerationReport? = null; private set

    fun startGenerate(): File {
        validate()
        checkCancelled()
        val start = System.nanoTime()
        val timings = linkedMapOf<String, Long>()
        fun timed(name: String, block: () -> Unit) {
            val before = System.nanoTime(); block(); timings[name] = (System.nanoTime() - before) / 1_000_000
        }
        val root = File(dir).apply { mkdirs() }
        val workspace = Files.createTempDirectory(root.toPath(), "junk-").toFile()
        try {
            val token = java.lang.Long.toUnsignedString(policy.seedFor(0, 7), 36)
            val namespace = policy.resourceNamespace ?: "$appPackageName.toolkitres$token"
            require(packagePattern.matches(namespace)) { "Invalid resource namespace: $namespace" }
            require(namespace != appPackageName) { "Resource namespace must differ from the code prefix; the host may already own its R class" }
            val random = Random(policy.seedFor(0, 1))
            val rootActivities = random.nextInt(activityCountPerPackage) + activityCountPerPackage / 2
            val total = packageCount.toLong() * activityCountPerPackage + rootActivities
            require(total in 1..policy.maxActivities.toLong()) { "Activity count $total exceeds configured safe range 1..${policy.maxActivities}" }
            require(total * (1 + policy.maxAssociatedClasses) * policy.maxMethodsPerClass <= policy.maxMethods) {
                "Requested worst-case method workload exceeds ${policy.maxMethods}; reduce Activities or the internal method/class ranges"
            }
            require(total * (1 + policy.maxAssociatedClasses) + 5 <= policy.maxClasses) { "Requested worst-case classes exceed ${policy.maxClasses}" }
            val shared = List(random.nextInt(2, 6)) { "$appPackageName.generated$token.shared.Core$it".replace('.', '/') }
            val packages = List(packageCount) { i ->
                val depth = random.nextInt(1, 4)
                appPackageName + "." + List(depth) { level -> "p${i.toString(36)}${level}_${random.nextInt(1_000_000).toString(36)}" }.joinToString(".")
            }
            val units = List(total.toInt()) { index ->
                val pkg = if (index < packageCount.toLong() * activityCountPerPackage) packages[index / activityCountPerPackage] else appPackageName
                JunkCodeUnit(index, "${pkg.replace('.', '/')}/A${token}_${index.toString(36)}Activity",
                    "${resPrefix}layout_${token}_${index.toString(36)}", policy.seedFor(index, 2), shared)
            }
            val metrics = JunkCodeMetrics()
            val classNames = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            fun writeClass(name: String, bytes: ByteArray, role: String) {
                require(bytes.size <= policy.maxClassBytes) { "Class $name has ${bytes.size} bytes, limit ${policy.maxClassBytes}" }
                check(classNames.add(name)) { "Duplicate generated class: $name" }
                require(classNames.size <= policy.maxClasses) { "Class count exceeds ${policy.maxClasses}" }
                metrics.add(bytes, role)
                require(metrics.methods <= policy.maxMethods && metrics.fields <= policy.maxFields) { "Generated methods/fields exceed configured work budget" }
                File(workspace, "classes/$name.class").apply { parentFile.mkdirs(); writeBytes(bytes) }
            }
            shared.forEachIndexed { i, name -> writeClass(name, JunkCodeComposer.sharedHelper(name, policy.seedFor(i, 3)), "sharedHelper") }
            val customViews = arrayOfNulls<List<String>>(units.size)
            var completedClasses = 0
            val progressLock = Any()
            timed("classes") {
                parallelJunkWork(units.size, policy.maxParallelism) { index ->
                    checkCancelled()
                    try {
                        val result = JunkCodeComposer.compose(units[index], namespace, policy)
                        result.classes.forEach { (name, bytes) -> writeClass(name, bytes, result.roles[name] ?: "helper") }
                        customViews[index] = result.customViews
                        synchronized(progressLock) {
                            completedClasses++
                            if (completedClasses % 32 == 0 || completedClasses == units.size)
                                onProgress(JunkGenerationProgress("classes", completedClasses, units.size))
                        }
                    } catch (error: Exception) {
                        if (error is java.util.concurrent.CancellationException) throw error
                        throw IllegalStateException("Activity unit $index (seed=${units[index].seed}): ${error.message}", error)
                    }
                }
            }
            val pool = JunkResourcePool(workspace, "${resPrefix}r${token}_", policy.resources)
            val ids = sortedSetOf<String>()
            val layoutFingerprints = mutableSetOf<String>()
            val layoutTypes = sortedSetOf<String>()
            val complexities = sortedMapOf<String, Int>()
            val containers = mutableListOf<Int>(); val attributes = mutableListOf<Int>()
            val nodes = mutableListOf<Int>(); val depths = mutableListOf<Int>(); val layoutSizes = mutableListOf<Int>()
            var layoutBytes = 0L
            timed("layoutsAndResources") {
                // Deterministic publication order makes quotas and reuse independent of worker scheduling.
                units.forEachIndexed { index, unit ->
                    checkCancelled()
                    val result = JunkLayoutComposer.compose(unit.layoutName, Random(policy.seedFor(index, 4)), pool, customViews[index].orEmpty(), policy)
                    val bytes = result.xml.toByteArray(Charsets.UTF_8)
                    check(bytes.size <= policy.maxLayoutBytes && result.nodes <= policy.maxLayoutNodes && result.depth <= policy.maxLayoutDepth) { "Layout ${unit.layoutName} exceeded limits: bytes=${bytes.size}, nodes=${result.nodes}, depth=${result.depth}" }
                    File(workspace, "res/layout/${unit.layoutName}.xml").apply { parentFile.mkdirs(); writeBytes(bytes) }
                    ids.addAll(result.ids); layoutFingerprints.add(result.fingerprint); layoutTypes.addAll(result.types.map { if ('.' in it) "generated.CustomView" else it })
                    complexities[result.complexity] = (complexities[result.complexity] ?: 0) + 1
                    containers.add(result.containers); attributes.add(result.attributes); nodes.add(result.nodes); depths.add(result.depth); layoutSizes.add(bytes.size); layoutBytes += bytes.size
                    // Optional animations/assets have their own bounded request strategy; never grow per-package unbounded.
                    val extras = Random(policy.seedFor(index, 5))
                    if (extras.nextDouble() < 0.04) pool.request("anim", extras)
                    if (extras.nextDouble() < 0.01) pool.request("assets", extras)
                    if (index % 32 == 0 || index == units.lastIndex) onProgress(JunkGenerationProgress("layouts", index + 1, units.size))
                }
                pool.finish()
            }
            val symbols = pool.symbols().toMutableMap().apply {
                put("layout", units.map { it.layoutName }.sorted()); put("id", ids.toList())
            }
            val activities = units.map { it.activityName.replace('/', '.') }
            timed("metadata") {
                File(workspace, "AndroidManifest.xml").writeText(buildString {
                    append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$namespace\">\n")
                    append("<uses-sdk android:minSdkVersion=\"21\"/><application>\n")
                    activities.forEach { append("<activity android:name=\"$it\" android:exported=\"false\"/>\n") }
                    append("</application></manifest>\n")
                })
                // proguard.txt is the consumer rule entry in the AAR specification. No host-wide wildcard.
                File(workspace, "proguard.txt").bufferedWriter().use { writer ->
                    classNames.sorted().forEach { writer.write("-keep class ${it.replace('/', '.')} { *; }\n") }
                }
                // Layouts have direct R references in kept Activities; XML tracks their resource dependencies.
                // Do not add an unbudgeted raw keep resource (in particular when total budget is zero).
                File(workspace, "R.txt").bufferedWriter().use { writer ->
                    symbols.toSortedMap().filterKeys { it != "assets" }.forEach { (type, names) ->
                        names.sorted().forEach { writer.write("int $type $it 0x0\n") }
                    }
                }
                File(workspace, "toolkit-generation.json").writeText(jsonValue(linkedMapOf(
                    "schema" to 1, "seed" to policy.seed, "codePackagePrefix" to appPackageName, "resourceNamespace" to namespace,
                    "minSdk" to 21, "androidxFragmentRequired" to policy.enableFragments,
                    "activityLayouts" to units.associate { it.activityName.replace('/', '.') to it.layoutName },
                    "code" to metrics.snapshot(), "optionalResources" to pool.snapshot().generated)))
            }
            timed("classesJar") { zip(File(workspace, "classes"), File(workspace, "classes.jar")) }
            val classJarBytes = File(workspace, "classes.jar").length()
            val archiveName = "junk_${appPackageName.replace('.', '_')}_TT3.0.0.aar"
            val staged = File(workspace, archiveName)
            timed("aar") { zip(workspace, staged) { !it.startsWith("classes/") && it != archiveName } }
            val resourceFiles = File(workspace, "res").walkTopDown().filter { it.isFile }.toList()
            val optionalFiles = resourceFiles.filter { !it.relativeTo(workspace).invariantSeparatorsPath.startsWith("res/layout/") && !it.relativeTo(workspace).invariantSeparatorsPath.startsWith("res/raw/") }
            val assets = File(workspace, "assets").walkTopDown().filter { it.isFile }.toList()
            val compressed = sortedMapOf<String, Long>()
            ZipFile(staged).use { z -> z.entries().asSequence().forEach { e ->
                val kind = when { e.name == "classes.jar" -> "classesJar"; e.name.startsWith("res/layout/") -> "layouts"; e.name.startsWith("res/") -> "otherResources"; e.name.startsWith("assets/") -> "assets"; else -> "metadata" }
                compressed[kind] = (compressed[kind] ?: 0) + e.compressedSize
            } }
            val resourceStats = pool.snapshot()
            fun distribution(values: List<Int>) = linkedMapOf("min" to (values.minOrNull() ?: 0), "max" to (values.maxOrNull() ?: 0), "mean" to if (values.isEmpty()) 0.0 else values.average())
            val report = JunkGenerationReport(linkedMapOf(
                "schema" to 1, "seed" to policy.seed, "codePackagePrefix" to appPackageName, "resourceNamespace" to namespace,
                "packageCount" to packageCount, "activitiesPerPackage" to activityCountPerPackage, "rootActivities" to rootActivities,
                "activities" to units.size, "policy" to policy.snapshot(), "code" to metrics.snapshot(),
                "layouts" to linkedMapOf("count" to units.size, "bytes" to layoutBytes, "nodes" to distribution(nodes), "containers" to distribution(containers), "attributes" to distribution(attributes), "depth" to distribution(depths), "fileBytes" to distribution(layoutSizes), "types" to layoutTypes, "complexity" to complexities, "uniqueStructures" to layoutFingerprints.size, "normalizedDuplicateRate" to 1.0 - layoutFingerprints.size.toDouble() / units.size),
                "resources" to linkedMapOf("generated" to resourceStats.generated, "requests" to resourceStats.requests, "reused" to resourceStats.reused,
                    "reuseRate" to if (resourceStats.requests.values.sum() == 0) 0.0 else resourceStats.reused.values.sum().toDouble() / resourceStats.requests.values.sum(),
                    "optionalFiles" to optionalFiles.size, "optionalBytes" to optionalFiles.sumOf { it.length() }, "valuesEntries" to (resourceStats.generated["string"] ?: 0), "ids" to ids.size,
                    "assets" to assets.size, "assetBytes" to assets.sumOf { it.length() }, "metadataResourceFiles" to 0),
                "sizes" to linkedMapOf("aarBytes" to staged.length(), "classesJarBytes" to classJarBytes, "classBytes" to metrics.classBytes,
                    "layoutBytes" to layoutBytes, "compressedEntryBytes" to compressed,
                    "classShareOfClassAndResourceBytes" to metrics.classBytes.toDouble() / (metrics.classBytes + resourceFiles.sumOf { it.length() } + assets.sumOf { it.length() })),
                "phasesMs" to timings, "totalMs" to (System.nanoTime() - start) / 1_000_000,
                "runtime" to linkedMapOf("java" to System.getProperty("java.version"), "os" to System.getProperty("os.name"), "arch" to System.getProperty("os.arch"), "processors" to Runtime.getRuntime().availableProcessors()),
                "dexOrApkMeasured" to false,
                "warnings" to buildList { if (metrics.methods >= 60000 || metrics.fields >= 60000) add("DEX references exceed a single-dex planning threshold; verify host multidex and Release output") ; if (units.size >= 5000) add("Layouts still grow with Activities; validate AAPT2/host resource pressure") },
            ))
            val outputDir = File(output).apply { mkdirs() }
            checkCancelled()
            onProgress(JunkGenerationProgress("publish", 1, 1))
            val destination = File(outputDir, archiveName)
            // Atomic replacement requires staging on the destination filesystem, even when work root is elsewhere.
            publishAll(listOf(staged to destination))
            val completedReport = report.copy(values = report.values + ("totalMs" to (System.nanoTime() - start) / 1_000_000))
            lastReport = completedReport
            if (logReport) logJunkArchiveReport(destination, completedReport)
            return destination
        } finally {
            check(workspace.deleteRecursively()) { "Cannot clean generator workspace: $workspace" }
        }
    }

    private fun validate() {
        require(packagePattern.matches(appPackageName)) { "Invalid code package prefix: $appPackageName" }
        require(resPrefix.isEmpty() || Regex("[a-z][a-z0-9_]*").matches(resPrefix)) { "Resource prefix must contain lowercase ASCII letters, digits or underscores" }
        require(packageCount >= 0 && activityCountPerPackage > 0) { "Package count must be nonnegative and Activity count positive" }
        require(packageCount.toLong() * activityCountPerPackage + activityCountPerPackage / 2 <= policy.maxActivities) { "Requested Activity scale exceeds ${policy.maxActivities}" }
    }

    private fun zip(root: File, output: File, include: (String) -> Boolean = { true }) {
        val entries = root.walkTopDown().filter { it.isFile && it != output }
            .map { it.relativeTo(root).invariantSeparatorsPath to it }.filter { include(it.first) }.sortedBy { it.first }.toList()
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            entries.forEach { (name, file) ->
                checkCancelled()
                zip.putNextEntry(ZipEntry(name).apply { time = 0L })
                file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    private fun publishAll(files: List<Pair<File, File>>) {
        val staged = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()
        val backups = mutableListOf<Pair<java.nio.file.Path, java.nio.file.Path>>()
        val installed = mutableListOf<java.nio.file.Path>()
        var committed = false
        try {
            files.forEach { (source, destination) ->
                val temp = Files.createTempFile(destination.parentFile.toPath(), ".${destination.name}", ".part")
                staged += temp to destination.toPath()
                Files.copy(source.toPath(), temp, StandardCopyOption.REPLACE_EXISTING)
            }
            staged.forEach { (_, destination) ->
                if (Files.exists(destination)) {
                    require(Files.isRegularFile(destination)) { "Output is not a regular file: $destination" }
                    val backup = Files.createTempFile(destination.parent, ".junk-backup-", ".part")
                    Files.move(destination, backup, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    backups += backup to destination
                }
            }
            staged.forEach { (source, destination) ->
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                installed.add(destination)
            }
            committed = true
        } catch (error: Throwable) {
            installed.forEach { Files.deleteIfExists(it) }
            backups.asReversed().forEach { (backup, destination) ->
                try { Files.move(backup, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
                catch (restore: Exception) { error.addSuppressed(restore) } // Preserve a backup if restoration itself fails.
            }
            throw error
        } finally {
            staged.forEach { Files.deleteIfExists(it.first) }
            if (committed) backups.forEach { Files.deleteIfExists(it.first) }
        }
    }

    companion object {
        private val packagePattern = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")
    }
}
