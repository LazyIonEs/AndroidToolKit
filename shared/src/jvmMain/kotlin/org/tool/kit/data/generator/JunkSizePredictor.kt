package org.tool.kit.data.generator

/** Empirical compressed-size model; a preview, never an Android resource or archive safety limit. */
object JunkSizePredictor {
    data class Estimate(
        val aarBytes: Long, val minimumBytes: Long, val maximumBytes: Long,
        val classesJarBytes: Long, val layoutBytes: Long, val otherResourceBytes: Long,
        val expectedActivities: Double,
    )

    fun estimateAarSize(packageCount: Int, activityCountPerPackage: Int): Long =
        estimate(packageCount, activityCountPerPackage).aarBytes

    fun estimate(packageCount: Int, activityCountPerPackage: Int, policy: JunkGenerationPolicy = JunkGenerationPolicy(seed = 0)): Estimate {
        if (packageCount < 0 || activityCountPerPackage <= 0) return Estimate(0, 0, 0, 0, 0, 0, 0.0)
        val activities = packageCount.toDouble() * activityCountPerPackage + activityCountPerPackage / 2 + (activityCountPerPackage - 1) / 2.0
        val helpers = (policy.minAssociatedClasses + policy.maxAssociatedClasses) / 2.0
        val methods = (policy.minMethodsPerClass + policy.maxMethodsPerClass) / 2.0
        // Calibrated at seed 20260922: 12/200 Activities, classes.jar 239817/3818481 bytes.
        // Coefficients are empirical; the range intentionally covers role/complexity variation.
        val operationScale = 0.55 + 0.45 * policy.maxMethodOperations.coerceAtMost(48) / 24.0
        val classJar = activities * (1 + helpers) * (340.0 + methods * 130.0 * operationScale) + 4000
        // Simple/medium/complex trees have different node/attribute densities; XML is separately measured.
        val meanNodes = minOf(policy.maxLayoutNodes.toDouble(), (10.0 + 28.0 + 54.0) / 3.0)
        val layoutRaw = activities * minOf(200 + meanNodes * 420, policy.maxLayoutBytes * 0.8)
        val caps = policy.resources
        val extraEntries = minOf(caps.total.toDouble(), activities * meanNodes * caps.newResourceProbability * 0.25,
            (caps.drawable.toLong() + caps.mipmap + caps.anim + caps.string + caps.assets).toDouble())
        val extrasRaw = extraEntries * 350
        // Outer AAR deflates classes.jar again. Metadata grows with both classes and Activities.
        val aar = classJar * 0.90 + layoutRaw * 0.11 + extrasRaw * 0.55 + activities * (260 + helpers * 36) + 2500
        // One Activity can draw any role/size combination; aggregate samples have less sampling spread.
        val spread = 0.35 + 0.35 / kotlin.math.sqrt(activities.coerceAtLeast(1.0))
        return Estimate(aar.toLong(), (aar * (1 - spread)).toLong(), (aar * (1 + spread)).toLong(), classJar.toLong(), layoutRaw.toLong(), extrasRaw.toLong(), activities)
    }
}
