package org.tool.kit.tests.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.serialization.json.Json
import org.junit.Test
import org.tool.kit.feature.apk.navigation.*
import org.tool.kit.feature.cleaner.navigation.CleanerNavKey
import org.tool.kit.feature.iconfactory.navigation.IconFactoryNavKey
import org.tool.kit.feature.junk.navigation.JunkCodeNavKey
import org.tool.kit.feature.setting.navigation.SettingNavKey
import org.tool.kit.feature.signature.navigation.*
import org.tool.kit.navigation.platformNavKeySerializer

class NavigationSerializationTest {
    @Test fun desktopAdapterReadsAndWritesAllNineExistingNavigationPayloads() {
        val keys = listOf(
            "apk.navigation.ApkInformationNavKey" to ApkInformationNavKey,
            "apk.navigation.ApkToolNavKey" to ApkToolNavKey,
            "signature.navigation.ApkSignatureNavKey" to ApkSignatureNavKey,
            "signature.navigation.SignatureInformationNavKey" to SignatureInformationNavKey,
            "signature.navigation.SignatureGenerationNavKey" to SignatureGenerationNavKey,
            "cleaner.navigation.CleanerNavKey" to CleanerNavKey,
            "iconfactory.navigation.IconFactoryNavKey" to IconFactoryNavKey,
            "junk.navigation.JunkCodeNavKey" to JunkCodeNavKey,
            "setting.navigation.SettingNavKey" to SettingNavKey,
        )
        val serializer = platformNavKeySerializer<NavKey>()
        for ((name, key) in keys) {
            // The serialized format uses JVM binary class names and an empty object value.
            val stored = """{"type":"org.tool.kit.feature.$name","value":{}}"""
            assertSame(key, Json.decodeFromString(serializer, stored))
            assertEquals(stored, Json.encodeToString(serializer, key))
        }
    }
}
