package org.tool.kit.data.generator

import kotlin.random.Random

/** Internal defaults; the existing Activity inputs remain the only required scale controls. */
data class JunkResourceBudget(
    val total: Int = 1280,
    val drawable: Int = 640,
    val mipmap: Int = 128,
    val anim: Int = 128,
    val string: Int = 320,
    val assets: Int = 64,
    val newResourceProbability: Double = 0.08,
) {
    init {
        require(listOf(total, drawable, mipmap, anim, string, assets).all { it >= 0 })
        require(newResourceProbability in 0.0..1.0)
    }
}

data class JunkGenerationPolicy(
    val seed: Long = Random.nextLong(),
    val resources: JunkResourceBudget = JunkResourceBudget(),
    val batchResourceTotal: Int = 25600,
    val minAssociatedClasses: Int = 3,
    val maxAssociatedClasses: Int = 9,
    val minMethodsPerClass: Int = 8,
    val maxMethodsPerClass: Int = 28,
    /** Integer-program budget; specialized predicates/long/string programs also have their own small caps. */
    val maxMethodOperations: Int = 24,
    val maxFragmentsPerActivity: Int = 2,
    val enableFragments: Boolean = true,
    val maxActivities: Int = 30_000,
    val maxClasses: Int = 400_000,
    val maxParallelism: Int = 4,
    val maxMethods: Long = 5_000_000,
    val maxFields: Long = 2_000_000,
    val maxClassBytes: Int = 256 * 1024,
    val maxLayoutNodes: Int = 72,
    val maxLayoutDepth: Int = 5,
    val maxLayoutBytes: Int = 48 * 1024,
    /** Resource namespace is independent of the code prefix and host applicationId. */
    val resourceNamespace: String? = null,
    /**
     * Relative weights for optional associated roles, after the first utility and optional
     * Fragments. Missing/zero roles are disabled. An interface consumes an implementation slot;
     * when only one slot remains, that interface choice becomes a DTO. No UI input is required.
     */
    val associatedRoleWeights: Map<String, Int> = mapOf(
        "utility" to 1, "dto" to 1, "converter" to 1, "validator" to 1,
        "collection" to 1, "config" to 1, "customView" to 1, "interface" to 1,
    ),
) {
    init {
        require(minAssociatedClasses in 0..maxAssociatedClasses && maxAssociatedClasses <= 32)
        require(minMethodsPerClass in 1..maxMethodsPerClass && maxMethodsPerClass in 11..128)
        require(maxMethodOperations in 1..128 && maxFragmentsPerActivity in 0..4)
        require(maxActivities in 1..100_000 && maxClasses in 1..2_000_000)
        require(maxParallelism in 1..16 && maxClassBytes in 4096..1_048_576)
        require(maxLayoutNodes in 8..128 && maxLayoutDepth in 2..8 && maxLayoutBytes in 4096..131072)
        require(batchResourceTotal >= 0 && maxMethods > 0 && maxFields > 0)
        val supportedRoles = setOf("utility", "dto", "converter", "validator", "collection", "config", "customView", "interface")
        require(associatedRoleWeights.all { (role, weight) -> role in supportedRoles && weight in 0..100 }) {
            "Associated role weights must use supported roles and values in 0..100"
        }
        require(associatedRoleWeights.values.sum() > 0) { "At least one associated role weight must be positive" }
    }

    fun snapshot(): Map<String, Any?> = linkedMapOf(
        "seed" to seed, "resources" to linkedMapOf("total" to resources.total,
            "drawable" to resources.drawable, "mipmap" to resources.mipmap, "anim" to resources.anim,
            "string" to resources.string, "assets" to resources.assets, "newResourceProbability" to resources.newResourceProbability),
        "batchResourceTotal" to batchResourceTotal, "minAssociatedClasses" to minAssociatedClasses,
        "maxAssociatedClasses" to maxAssociatedClasses, "minMethodsPerClass" to minMethodsPerClass,
        "maxMethodsPerClass" to maxMethodsPerClass, "maxMethodOperations" to maxMethodOperations,
        "maxFragmentsPerActivity" to maxFragmentsPerActivity, "enableFragments" to enableFragments,
        "maxActivities" to maxActivities, "maxClasses" to maxClasses, "maxMethods" to maxMethods,
        "maxFields" to maxFields, "maxParallelism" to maxParallelism, "maxClassBytes" to maxClassBytes,
        "maxLayoutNodes" to maxLayoutNodes, "maxLayoutDepth" to maxLayoutDepth, "maxLayoutBytes" to maxLayoutBytes,
        "resourceNamespace" to resourceNamespace,
        "associatedRoleWeights" to associatedRoleWeights.toSortedMap(),
    )

    /** SplitMix64-derived streams depend on task identity, never on worker scheduling. */
    fun seedFor(index: Int, phase: Int = 0): Long {
        var z = seed + -7046029254386353131L * (index.toLong() + 1) + phase * 1442695040888963407L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}

data class JunkGenerationProgress(val phase: String, val completed: Int, val total: Int)
