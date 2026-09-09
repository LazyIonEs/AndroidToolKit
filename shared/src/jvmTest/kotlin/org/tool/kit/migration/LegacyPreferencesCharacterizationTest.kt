package org.tool.kit.migration

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Test
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.model.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
class LegacyPreferencesCharacterizationTest {
    private val user = UserData("/fixture/output", false, "-fixture", false,
        DestStoreType.PKCS12, DestStoreSize.ONE_THOUSAND_TWENTY_FOUR)

    @Test fun legacySerializerWritesPhysicalKeysAndNewInstanceReadsThem() {
        val values = mutableMapOf<String, Any>()
        val settings = MapSettings(values)
        settings.encodeValue(UserData.serializer(), "user_data", user)
        assertEquals(mapOf<String, Any>(
            "user_data.defaultOutputPath" to "/fixture/output",
            "user_data.duplicateFileRemoval" to false,
            "user_data.defaultSignerSuffix" to "-fixture",
            "user_data.alignFileSize" to false,
            "user_data.destStoreType" to 1,
            "user_data.destStoreSize" to 0,
        ), values)
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined))
        assertEquals(user, source.userData.value)
        source.saveUserData(user.copy(defaultSignerSuffix = "-new"))
        assertEquals(user.copy(defaultSignerSuffix = "-new"),
            PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined)).userData.value)
    }

    @Test fun emptySettingsPreserveOriginalDefaults() = runTest {
        val source = PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined))
        assertEquals(DarkThemeConfig.FOLLOW_SYSTEM, source.themeConfig.first())
        assertEquals("-sign", source.userData.value.defaultSignerSuffix)
        assertEquals(DestStoreType.JKS, source.userData.value.destStoreType)
        assertEquals(DestStoreSize.TWO_THOUSAND_FORTY_EIGHT, source.userData.value.destStoreSize)
        assertTrue(source.userData.value.alignFileSize)
        assertTrue(source.userData.value.duplicateFileRemoval)
        assertFalse(source.isShowJunkCode.first())
        assertFalse(source.isAlwaysShowLabel.first())
        assertFalse(source.isEnableDeveloperMode.first())
        assertTrue(source.isHuaweiAlignFileSize.first())
        assertTrue(source.isStartCheckUpdate.first())
        assertEquals(CopyMode.UPPERCASE_WITH_COLON, source.copyMode.first())
        assertEquals(JunkMode.SINGLE, source.junkMode.first())
    }

    @Test fun unknownPreferenceEnumsKeepTheExistingFallbacks() = runTest {
        val settings = MapSettings("theme_config" to "future", "signature_copy_mode" to "future", "junk_mode" to "future")
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined))
        assertEquals(DarkThemeConfig.LIGHT, source.themeConfig.first())
        assertEquals(CopyMode.UPPERCASE_WITH_COLON, source.copyMode.first())
        assertEquals(JunkMode.SINGLE, source.junkMode.first())
    }

    @Test fun iconSettingsRoundTripRetainsEnumOrdinalsAndEveryParameter() {
        val settings = MapSettings()
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined))
        val icon = IconFactoryData(PngAlgorithm.Mitchell, JpegAlgorithm.Hamming, false, 42, 80, 4, 3, .5f, 73f)
        source.saveIconFactoryData(icon)
        assertEquals(icon, PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined)).iconFactoryData.value)
        assertEquals(2, settings.getInt("icon_factory_data.pngTypIdx", -1))
        assertEquals(1, settings.getInt("icon_factory_data.jpegTypIdx", -1))
    }
}
