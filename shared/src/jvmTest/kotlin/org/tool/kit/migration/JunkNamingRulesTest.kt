package org.tool.kit.migration

import org.junit.Test
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.usecase.EstimateJunkSizeUseCase
import org.tool.kit.feature.junk.*
import org.tool.kit.feature.junk.JunkCodeIntent.*
import org.tool.kit.model.JunkMode
import org.tool.kit.data.generator.JunkSizePredictor
import org.tool.kit.utils.formatFileSize
import kotlin.test.*

class JunkNamingRulesTest {
    @Test fun packageAndSuffixSettersMatchTheFrozenModelIncludingDotsWhitespaceAndEmptyStrings() {
        var form = SingleJunkForm(); val legacy = LegacyJunkForm()
        for (value in listOf("com.example", "", " .中文.name. ", "unchanged")) {
            form = JunkFormReducer.single(form, PackageNameChanged(value)); legacy.packageName = value
            assertEquals(legacy.aarName, form.aarName); assertEquals(value, form.packageName)
            for (suffix in listOf("plugin", "a.b", "", " suffix ")) {
                form = JunkFormReducer.single(form, SuffixChanged(suffix)); legacy.suffix = suffix
                assertEquals(legacy.aarName, form.aarName); assertEquals(suffix, form.suffix)
            }
        }
        val state = JunkCodeUiState("out", JunkMode.SINGLE, single = SingleJunkForm(packageName = "com.example", suffix = "a.b"))
        assertEquals("com.example.a.b", assertIs<JunkConfiguration.Single>(state.configuration()).appPackageName)
        val display = JunkFormReducer.single(state.single, SuffixChanged("a.b")).aarName
        assertEquals("junk_com_example_a.b_TT2.2.0.aar", display) // Real generator flattens the suffix's dot as well.
    }

    @Test fun everyRawFieldAndOverflowFallbackArePreserved() {
        var single = SingleJunkForm(); var multi = MultiJunkForm()
        for (event in listOf(PackageCountChanged("0001"), ActivityCountChanged("2147483648"), ResPrefixChanged(" prefix ")))
            single = JunkFormReducer.single(single, event)
        for (event in listOf(OutputDirChanged(" dir "), AarCountChanged(""), LeastPackagesChanged("0002"), MaximumPackagesChanged("2147483648"), LeastActivitiesChanged(" "), MaximumActivitiesChanged("5")))
            multi = JunkFormReducer.multi(multi, event)
        assertEquals("0001", single.packageCount); assertEquals("2147483648", single.activityCountPerPackage)
        val state = JunkCodeUiState(" output ", JunkMode.SINGLE, single, multi)
        assertEquals(JunkConfiguration.Single("com.dev.junk.plugin", 1, 0, " prefix "), state.configuration())
        assertEquals(JunkConfiguration.Multi(" dir ", 0, 2, 0, 0, 5), state.copy(mode = JunkMode.MULTI).configuration())
        assertTrue(state.hasMissingFields(), "Hidden multi draft still participates in the original validation")
        val spaces = JunkCodeUiState("out", JunkMode.SINGLE, SingleJunkForm(activityCountPerPackage = " "), MultiJunkForm(leastActivityCountPerPackage = " "))
        assertFalse(spaces.hasMissingFields(), "These two legacy fields use isEmpty, not isBlank")
    }

    @Test fun estimateMatchesFrozenFormulaAndRepeatedAdditionAcrossBothModes() {
        val estimate = EstimateJunkSizeUseCase(JunkSizePredictor::estimateAarSize)
        for (packages in listOf("", "0", "1", "0002", "50", "2147483648")) {
            for (activities in listOf("", "1", "5")) for (count in listOf("", "0", "1", "3", "-1")) {
                val old = LegacyJunkForm(packageCount = packages, activityCountPerPackage = activities, aarCount = count,
                    leastPackageCount = packages, maximumPackageCount = "7", leastActivityCountPerPackage = activities, maximumActivityCountPerPackage = "9")
                val state = JunkCodeUiState("out", JunkMode.SINGLE, SingleJunkForm(packageCount = packages, activityCountPerPackage = activities),
                    MultiJunkForm(aarCount = count, leastPackageCount = packages, maximumPackageCount = "7", leastActivityCountPerPackage = activities, maximumActivityCountPerPackage = "9"))
                for (mode in JunkMode.entries) {
                    val size = estimate(state.copy(mode = mode).configuration())
                    val text = size.minimum.formatFileSize(scale = 1) + (size.maximum?.let { " ~ ${it.formatFileSize(scale = 1)}" } ?: "")
                    assertEquals(old.estimateAarSize(mode), text)
                }
            }
        }
        val huge = estimate(JunkConfiguration.Multi("out", Int.MAX_VALUE, 1, 1, 1, 1))
        assertEquals(JunkSizePredictor.estimateAarSize(1, 1) * Int.MAX_VALUE.toLong(), huge.minimum)
    }
}
