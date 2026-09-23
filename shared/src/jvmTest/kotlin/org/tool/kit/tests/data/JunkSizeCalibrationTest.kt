package org.tool.kit.tests.data

import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.generator.*
import kotlin.test.*

class JunkSizeCalibrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun measuredSmallAndMediumCohortsFitThePreviewInterval() {
        val samples = mutableListOf<Map<String, Any>>()
        for (seed in listOf(1L, 20260922L, 81031L)) for (activities in listOf(1, 12, 200)) {
            val generator = AndroidJunkGenerator(temporary.root.path, temporary.newFolder("s${seed}_$activities").path,
                "com.calibration.s$seed", activities, 1, "c_", JunkGenerationPolicy(seed = seed))
            val aar = generator.startGenerate()
            val estimate = JunkSizePredictor.estimate(activities, 1)
            assertTrue(aar.length() in estimate.minimumBytes..estimate.maximumBytes,
                "seed=$seed Activities=$activities actual=${aar.length()} estimate=$estimate")
            samples += linkedMapOf("seed" to seed, "activities" to activities, "actualAarBytes" to aar.length(),
                "estimatedAarBytes" to estimate.aarBytes, "minimumBytes" to estimate.minimumBytes,
                "maximumBytes" to estimate.maximumBytes,
                "relativeError" to (estimate.aarBytes.toDouble() / aar.length() - 1))
        }
        System.getProperty("test.fixtureRoot")?.let { root ->
            File(root, "junk-estimation-calibration.json").apply { parentFile.mkdirs(); writeText(jsonValue(samples) + "\n") }
        }
        assertTrue(JunkSizePredictor.estimate(20, 1, JunkGenerationPolicy(seed = 0, maxMethodOperations = 48)).classesJarBytes >
            JunkSizePredictor.estimate(20, 1).classesJarBytes)
        assertEquals(0, JunkSizePredictor.estimateAarSize(1, 0))
    }
}
