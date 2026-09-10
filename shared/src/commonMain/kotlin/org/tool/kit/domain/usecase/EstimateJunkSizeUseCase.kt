package org.tool.kit.domain.usecase

import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkSizeEstimator

class EstimateJunkSizeUseCase(private val estimator: JunkSizeEstimator) {
    /** 估算单个 AAR 的大小或批量生成的大小范围；只做计算，不创建文件。 */
    operator fun invoke(configuration: JunkConfiguration): JunkSizeEstimate = when (configuration) {
        is JunkConfiguration.Single -> JunkSizeEstimate(estimator.bytes(configuration.packageCount, configuration.activityCount))
        is JunkConfiguration.Multi -> {
            // Empty or negative counts produce no output; arithmetic uses Long overflow semantics.
            // A very large raw AAR count must not run a proportional loop on the UI thread.
            val count = configuration.aarCount.coerceAtLeast(0).toLong()
            JunkSizeEstimate(estimator.bytes(configuration.leastPackages, configuration.leastActivities) * count,
                estimator.bytes(configuration.maximumPackages, configuration.maximumActivities) * count)
        }
    }
}
