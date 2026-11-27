package org.tool.kit.data.generator

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.math.roundToInt
import org.tool.kit.domain.junk.JunkAarSizeRange
import org.tool.kit.domain.junk.formatAarSize

internal data class SizedJunkArchive(val archive: JunkGeneratedArchive, val activities: Int)

private data class SizeMeasurement(val activities: Int, val bytes: Long)

/**
 * Each candidate uses one Activity per package so the existing generator can change the total
 * one Activity at a time. Only a measured, in-range archive reaches the requested directory.
 */
internal fun generateJunkAarInRange(
    output: File,
    size: JunkAarSizeRange,
    policy: JunkGenerationPolicy,
    checkActive: () -> Unit = {},
    generate: (activities: Int, candidateDirectory: File) -> JunkGeneratedArchive,
): SizedJunkArchive {
    val classesPerActivity = 1 + policy.maxAssociatedClasses
    var least = 1
    var most = minOf(
        policy.maxActivities.toLong(),
        policy.maxMethods / (classesPerActivity * policy.maxMethodsPerClass),
        (policy.maxClasses - 5L) / classesPerActivity,
    ).toInt()
    require(most >= least) { "生成预算至少需要容纳 1 个 Activity。" }
    val midpoint = size.minimumBytes + (size.maximumBytes - size.minimumBytes) / 2
    val bytesPerActivity = JunkSizePredictor.estimate(512, 1, policy).aarBytes / 512.0
    var activities = (midpoint / bytesPerActivity).roundToInt().coerceIn(least, most)
    var smallestOversize: SizeMeasurement? = null
    var largestUndersize: SizeMeasurement? = null
    var closestBytes = 0L
    var closestDistance = Long.MAX_VALUE
    var corrections = 0

    Files.createDirectories(output.toPath())
    val staging = Files.createTempDirectory(output.toPath(), ".junk-size-").toFile()
    try {
        while (least <= most) {
            checkActive()
            val archive = generate(activities, staging)
            checkActive()
            require(archive.file.isFile && archive.file.canonicalFile.parentFile == staging.canonicalFile) {
                "Generated AAR must be staged inside ${staging.absolutePath}"
            }
            val bytes = archive.file.length()
            check(bytes > 0) { "生成的 AAR 文件为空。" }
            if (bytes in size) {
                val destination = output.resolve(archive.file.name)
                require(!destination.exists() || destination.isFile) { "Output is not a regular file: $destination" }
                Files.move(archive.file.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
                return SizedJunkArchive(archive.copy(file = destination), activities)
            }
            Files.delete(archive.file.toPath())

            val measurement = SizeMeasurement(activities, bytes)
            val distance = if (bytes < size.minimumBytes) {
                least = activities + 1
                largestUndersize = measurement
                size.minimumBytes - bytes
            } else {
                most = activities - 1
                smallestOversize = measurement
                bytes - size.maximumBytes
            }
            if (distance < closestDistance) {
                closestBytes = bytes
                closestDistance = distance
            }
            if (least > most) break

            val smaller = largestUndersize
            val larger = smallestOversize
            // Prefer measured corrections first, then halve the remaining interval.
            activities = when {
                corrections++ >= 3 -> least + (most - least) / 2
                smaller != null && larger != null -> {
                    val proportion = (midpoint - smaller.bytes).toDouble() / (larger.bytes - smaller.bytes)
                    (smaller.activities + proportion * (larger.activities - smaller.activities)).roundToInt()
                }
                else -> (activities * (midpoint.toDouble() / bytes)).roundToInt()
            }
                .coerceIn(least, most)
        }
        error("未生成符合大小范围的 AAR。要求 ${size.minimumBytes.formatAarSize()}～${size.maximumBytes.formatAarSize()}，" +
            "最接近的实测结果为 ${closestBytes.formatAarSize()}。")
    } finally {
        check(staging.deleteRecursively()) { "Cannot clean size staging: $staging" }
    }
}
