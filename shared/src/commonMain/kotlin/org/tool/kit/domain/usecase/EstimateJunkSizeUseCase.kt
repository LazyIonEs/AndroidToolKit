package org.tool.kit.domain.usecase

import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkSizeEstimator

class EstimateJunkSizeUseCase(private val estimator: JunkSizeEstimator) {
    /** 估算单个 AAR 的大小或批量生成的大小范围；只做计算，不创建文件。 */
    operator fun invoke(configuration: JunkConfiguration): JunkSizeEstimate = when (configuration) {
        is JunkConfiguration.Single -> estimator.range(configuration.packageCount, configuration.activityCount)
        is JunkConfiguration.Multi -> {
            // Empty or negative counts produce no output; huge previews saturate instead of wrapping negative.
            // A very large raw AAR count must not run a proportional loop on the UI thread.
            val count = configuration.aarCount.coerceAtLeast(0).toLong()
            val lower = estimator.range(configuration.leastPackages, configuration.leastActivities)
            val upper = estimator.range(maxOf(configuration.leastPackages, configuration.maximumPackages),
                maxOf(configuration.leastActivities, configuration.maximumActivities))
            JunkSizeEstimate(multiplySaturated(lower.minimum, count), multiplySaturated(upper.maximum ?: upper.minimum, count))
        }
    }

    private fun multiplySaturated(bytes: Long, count: Long): Long = when {
        bytes <= 0 || count <= 0 -> 0
        bytes > Long.MAX_VALUE / count -> Long.MAX_VALUE
        else -> bytes * count
    }
}
