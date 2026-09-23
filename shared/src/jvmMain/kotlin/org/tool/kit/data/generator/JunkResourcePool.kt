package org.tool.kit.data.generator

import java.io.File
import kotlin.random.Random

/** Counts resource entries, rather than treating a values XML file as one resource. */
internal data class JunkResourceStats(
    val generated: Map<String, Int>,
    val requests: Map<String, Int>,
    val reused: Map<String, Int>,
)

/** An AAR-local pool. A resource becomes visible only after its complete write succeeds. */
internal class JunkResourcePool(
    private val workspace: File,
    private val prefix: String,
    private val budget: JunkResourceBudget,
) {
    private val limits = linkedMapOf(
        "drawable" to budget.drawable, "mipmap" to budget.mipmap,
        "anim" to budget.anim, "string" to budget.string, "assets" to budget.assets,
    )
    private val names = limits.keys.associateWith { mutableListOf<String>() }
    private val requests = limits.keys.associateWith { 0 }.toMutableMap()
    private val reused = limits.keys.associateWith { 0 }.toMutableMap()
    private var total = 0

    init {
        require(budget.total >= 0 && limits.values.all { it >= 0 }) { "Resource budgets must be nonnegative" }
        require(budget.newResourceProbability in 0.0..1.0) { "Resource creation probability must be between 0 and 1" }
        require(prefix.matches(Regex("[a-z][a-z0-9_]*"))) { "Invalid resource prefix: $prefix" }
    }

    @Synchronized
    fun request(type: String, random: Random): String? {
        val pool = requireNotNull(names[type]) { "Unsupported pooled resource type: $type" }
        requests[type] = requests.getValue(type) + 1
        if (total < budget.total && pool.size < limits.getValue(type) && random.nextDouble() < budget.newResourceProbability) {
            val name = "${prefix}pool_${type}_${pool.size}"
            write(type, name, random)
            // Keep the write inside the lock. Failed files never consume a reservation or get reused.
            pool += name
            total++
            return name
        }
        if (pool.isEmpty()) return null
        reused[type] = reused.getValue(type) + 1
        return pool[random.nextInt(pool.size)]
    }

    @Synchronized
    fun symbols(): Map<String, List<String>> = names.filterKeys { it != "assets" }.mapValues { it.value.toList() }

    @Synchronized
    fun snapshot(): JunkResourceStats = JunkResourceStats(
        names.mapValues { it.value.size }, requests.toMap(), reused.toMap(),
    )

    /** All writes are immediate; provided as an explicit completion boundary for the generator. */
    @Synchronized
    fun finish() = Unit

    private fun write(type: String, name: String, random: Random) {
        val file = when (type) {
            "string" -> File(workspace, "res/values/$name.xml")
            "assets" -> File(workspace, "assets/$name.json")
            else -> File(workspace, "res/$type/$name.xml")
        }
        check(file.parentFile.isDirectory || file.parentFile.mkdirs()) { "Cannot create resource directory: ${file.parent}" }
        val content = when (type) {
            "drawable", "mipmap" -> drawable(random)
            "anim" -> animation(random)
            "string" -> "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources><string name=\"$name\" translatable=\"false\">${labels.random(random)} ${random.nextInt(1, 100)}</string></resources>\n"
            else -> "{\"version\":1,\"mode\":${random.nextInt(4)},\"limit\":${random.nextInt(8, 65)}}\n"
        }
        file.writeText(content, Charsets.UTF_8)
    }

    private fun drawable(random: Random): String {
        val color = colors.random(random)
        return if (random.nextBoolean()) {
            """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="$color" />
    <corners android:radius="${random.nextInt(2, 17)}dp" />
    <stroke android:width="1dp" android:color="${colors.random(random)}" />
    <padding android:left="8dp" android:top="6dp" android:right="8dp" android:bottom="6dp" />
</shape>
"""
        } else {
            val path = listOf("M4,4 L20,4 L20,20 L4,20 Z", "M12,2 L22,20 L2,20 Z", "M3,10 L10,10 L10,3 L14,3 L14,10 L21,10 L21,14 L14,14 L14,21 L10,21 L10,14 L3,14 Z").random(random)
            """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="$color" android:pathData="$path" />
</vector>
"""
        }
    }

    private fun animation(random: Random): String {
        val duration = random.nextInt(80, 241)
        val body = when (random.nextInt(3)) {
            0 -> "<alpha android:fromAlpha=\"0.6\" android:toAlpha=\"1.0\" android:duration=\"$duration\" />"
            1 -> "<translate android:fromYDelta=\"4%\" android:toYDelta=\"0%\" android:duration=\"$duration\" />"
            else -> "<scale android:fromXScale=\"0.96\" android:toXScale=\"1.0\" android:fromYScale=\"0.96\" android:toYScale=\"1.0\" android:pivotX=\"50%\" android:pivotY=\"50%\" android:duration=\"$duration\" />"
        }
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<set xmlns:android=\"http://schemas.android.com/apk/res/android\">$body</set>\n"
    }

    private companion object {
        val labels = listOf("Overview", "Recent entries", "Details", "Options", "Continue", "Summary", "Review", "Available items")
        val colors = listOf("#E6EEF4", "#C7D8E8", "#DAE7DB", "#D9D1E3", "#F0E3D3", "#CEDFDF", "#36556F", "#526948")
    }
}
