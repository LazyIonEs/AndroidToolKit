package org.tool.kit.tests.domain

import kotlin.test.*
import org.junit.Test
import org.tool.kit.data.generator.JunkSizePredictor
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.usecase.EstimateJunkSizeUseCase
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import org.tool.kit.model.JunkMode

class JunkNamingRulesTest {
    @Test fun archiveNamesFlattenPackageDotsAndPreserveRawSuffixes() {
        var form = SingleJunkForm()
        val packages = listOf("com.example" to "com_example", "" to "", " .中文.name. " to " _中文_name_ ", "unchanged" to "unchanged")
        for ((value, fileStem) in packages) {
            form = JunkFormReducer.single(form, PackageNameChanged(value))
            assertEquals("junk_${fileStem}_${form.suffix}_TT3.1.0.aar", form.aarName)
            assertEquals(value, form.packageName)
            for (suffix in listOf("plugin", "a.b", "", " suffix ")) {
                form = JunkFormReducer.single(form, SuffixChanged(suffix))
                assertEquals("junk_${fileStem}_${suffix}_TT3.1.0.aar", form.aarName)
                assertEquals(suffix, form.suffix)
            }
        }
        val state = JunkCodeUiState("out", JunkMode.SINGLE, single = SingleJunkForm(packageName = "com.example", suffix = "a.b"))
        assertEquals("com.example.a.b", assertIs<JunkConfiguration.SingleBySize>(state.configuration()).appPackageName)
        val display = JunkFormReducer.single(state.single, SuffixChanged("a.b")).aarName
        assertEquals("junk_com_example_a.b_TT3.1.0.aar", display) // Real generator flattens the suffix's dot as well.
    }

    @Test fun sizeFormAcceptsDecimalMegabytesAndOnlyTheSelectedRangeIsSubmitted() {
        val single = SingleJunkForm(resPrefix = " prefix ", size = JunkAarSizeForm("017.5", "22,5"))
        val multi = MultiJunkForm(outputDir = " dir ", aarCount = "002", size = JunkAarSizeForm(".5", "1"))
        val state = JunkCodeUiState(" output ", JunkMode.SINGLE, single, multi)
        assertEquals(JunkConfiguration.SingleBySize("com.dev.junk.plugin", JunkAarSizeRange(17_500_000, 22_500_000), " prefix "), state.configuration())
        assertEquals(JunkConfiguration.MultiBySize(" dir ", 2, JunkAarSizeRange(500_000, 1_000_000)),
            state.copy(mode = JunkMode.MULTI).configuration())
        assertNull(state.copy(single = single.copy(size = JunkAarSizeForm("22.5", "17.5"))).configuration())
        assertNull(JunkAarSizeRange.fromMegabytes("0", "22.5"))
        assertNull(JunkAarSizeRange.fromMegabytes("1", "9223372036854.775808"))
    }

    @Test fun sizeEstimatesUseParsedCountsAndAccumulateBothBatchBounds() {
        val estimate = EstimateJunkSizeUseCase(JunkSizePredictor::estimateAarSize)
        for (packages in listOf("", "0", "1", "0002", "50", "2147483648")) {
            for (activities in listOf("", "1", "5")) for (count in listOf("", "0", "1", "3", "-1")) {
                val packageCount = packages.toIntOrNull() ?: 0
                val activityCount = activities.toIntOrNull() ?: 0
                val minimum = JunkSizePredictor.estimateAarSize(packageCount, activityCount)
                val maximum = JunkSizePredictor.estimateAarSize(maxOf(7, packageCount), 9)
                assertEquals(JunkSizeEstimate(minimum), estimate(JunkConfiguration.Single("com.fixture", packageCount, activityCount, "f_")))
                val range = 0 until (count.toIntOrNull() ?: 0)
                assertEquals(JunkSizeEstimate(range.sumOf { minimum }, range.sumOf { maximum }),
                    estimate(JunkConfiguration.Multi("out", count.toIntOrNull() ?: 0, packageCount, 7, activityCount, 9)))
            }
        }
        val huge = estimate(JunkConfiguration.Multi("out", Int.MAX_VALUE, 1, 1, 1, 1))
        assertEquals(JunkSizePredictor.estimateAarSize(1, 1) * Int.MAX_VALUE.toLong(), huge.minimum)
    }
}
