package org.tool.kit.migration

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import com.russhwolf.settings.serialization.encodeValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import org.junit.Test
import org.tool.kit.data.source.PreferencesDataSource
import org.tool.kit.model.*
import org.tool.kit.domain.preferences.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalSettingsApi::class, ExperimentalSerializationApi::class)
class LegacyPreferencesCharacterizationTest {
    private val user = UserData("/fixture/output", false, "-fixture", false,
        DestStoreType.PKCS12, DestStoreSize.ONE_THOUSAND_TWENTY_FOUR)

    @Test fun legacySerializerWritesPhysicalKeysAndNewInstanceReadsThem() = runTest {
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
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined)
        assertEquals(user, source.read().userData)
        source.write(PreferenceChange.SignerSuffix("-new"), source.read().changed(PreferenceChange.SignerSuffix("-new")))
        assertEquals(user.copy(defaultSignerSuffix = "-new"),
            PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined).read().userData)
    }

    @Test fun emptySettingsPreserveOriginalDefaults() = runTest {
        val source = PreferencesDataSource(MapSettings().toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined)
        assertEquals(ThemePreference.FOLLOW_SYSTEM, source.read().themeConfig)
        assertEquals("-sign", source.read().userData.defaultSignerSuffix)
        assertEquals(DestStoreType.JKS, source.read().userData.destStoreType)
        assertEquals(DestStoreSize.TWO_THOUSAND_FORTY_EIGHT, source.read().userData.destStoreSize)
        assertTrue(source.read().userData.alignFileSize)
        assertTrue(source.read().userData.duplicateFileRemoval)
        assertFalse(source.read().isShowJunkCode)
        assertFalse(source.read().isAlwaysShowLabel)
        assertFalse(source.read().isEnableDeveloperMode)
        assertTrue(source.read().isHuaweiAlignFileSize)
        assertTrue(source.read().isStartCheckUpdate)
        assertEquals(CopyPreference.UPPERCASE_WITH_COLON, source.read().copyMode)
        assertEquals(JunkPreference.SINGLE, source.read().junkMode)
    }

    @Test fun unknownPreferenceEnumsKeepTheExistingFallbacks() = runTest {
        val settings = MapSettings("theme_config" to "future", "signature_copy_mode" to "future", "junk_mode" to "future")
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined)
        assertEquals(ThemePreference.LIGHT, source.read().themeConfig)
        assertEquals(CopyPreference.UPPERCASE_WITH_COLON, source.read().copyMode)
        assertEquals(JunkPreference.SINGLE, source.read().junkMode)
    }

    @Test fun iconSettingsRoundTripRetainsEnumOrdinalsAndEveryParameter() = runTest {
        val settings = MapSettings()
        val source = PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined)
        val icon = IconFactoryData(PngAlgorithm.Mitchell, JpegAlgorithm.Hamming, false, 42, 80, 4, 3, .5f, 73f)
        source.write(PreferenceChange.IconSettings(icon), source.read().changed(PreferenceChange.IconSettings(icon)))
        assertEquals(icon, PreferencesDataSource(settings.toFlowSettings(Dispatchers.Unconfined), Dispatchers.Unconfined).read().iconFactoryData)
        assertEquals(2, settings.getInt("icon_factory_data.pngTypIdx", -1))
        assertEquals(1, settings.getInt("icon_factory_data.jpegTypIdx", -1))
    }
}
