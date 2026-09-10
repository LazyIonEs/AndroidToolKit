package org.tool.kit.domain.usecase

import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkSizeEstimator

class EstimateJunkSizeUseCase(private val estimator: JunkSizeEstimator) {
    operator fun invoke(configuration: JunkConfiguration): JunkSizeEstimate = when (configuration) {
        is JunkConfiguration.Single -> JunkSizeEstimate(estimator.bytes(configuration.packageCount, configuration.activityCount))
        is JunkConfiguration.Multi -> {
            // Equivalent to the original repeated Long addition (including overflow); empty/negative ranges add nothing.
            // A very large raw AAR count must not run a proportional loop on the UI thread.
            val count = configuration.aarCount.coerceAtLeast(0).toLong()
            JunkSizeEstimate(estimator.bytes(configuration.leastPackages, configuration.leastActivities) * count,
                estimator.bytes(configuration.maximumPackages, configuration.maximumActivities) * count)
        }
    }
}
