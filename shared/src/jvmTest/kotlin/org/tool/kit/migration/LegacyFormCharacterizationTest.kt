package org.tool.kit.migration

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.migration.LegacySigningForm as ApkSignature
import org.tool.kit.model.JunkCodeInfo
import org.tool.kit.model.JunkMode
import org.tool.kit.model.Sign
import org.tool.kit.model.SignaturePolicy
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Lock down the old behavior before replacing the mutable models with reducers. */
class LegacyFormCharacterizationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sameKeyPathKeepsCredentialsAndChangedPathClearsOnlyDependentFields() {
        val aliases = arrayListOf("first", "second")
        val sign = Sign(_keyStorePath = "old.jks", keyStorePolicy = SignaturePolicy.V4,
            keyStorePassword = "test-only", keyStoreAlisaList = aliases, keyStoreAlisaIndex = 1,
            keyStoreAlisaPassword = "test-only", v4SignatureOutputFileName = "custom.idsig")
        sign.keyStorePath = "old.jks"
        assertEquals("test-only", sign.keyStorePassword)
        assertSame(aliases, sign.keyStoreAlisaList)
        assertEquals(1, sign.keyStoreAlisaIndex)
        sign.keyStorePath = "new.jks"
        assertEquals("", sign.keyStorePassword)
        assertNull(sign.keyStoreAlisaList)
        assertEquals(0, sign.keyStoreAlisaIndex)
        assertEquals("", sign.keyStoreAlisaPassword)
        assertEquals(SignaturePolicy.V4, sign.keyStorePolicy)
        assertEquals("custom.idsig", sign.v4SignatureOutputFileName)
    }

    @Test fun existingApkAndPrefixDeriveV4NameButSamePathKeepsManualName() {
        val apk = temporary.newFile("中文 input.apk")
        val form = ApkSignature()
        form.apkPath = apk.path
        assertEquals("中文 input.apk.idsig", form.v4SignatureOutputFileName)
        form.outputPrefix = "prefix"
        assertEquals("prefix-中文 input.apk.idsig", form.v4SignatureOutputFileName)
        form.v4SignatureOutputFileName = "manual.idsig"
        form.apkPath = apk.path
        assertEquals("manual.idsig", form.v4SignatureOutputFileName)
        form.apkPath = temporary.root.resolve("missing.apk").path
        assertEquals("manual.idsig", form.v4SignatureOutputFileName)
    }

    @Test fun clearingApkWithPrefixPreservesTheKnownLeadingHyphenDefect() {
        val form = ApkSignature()
        form.outputPrefix = "prefix"
        assertEquals("prefix-apk-name.apk.idsig", form.v4SignatureOutputFileName)
        form.apkPath = temporary.newFile("input.apk").path
        form.apkPath = ""
        assertEquals("-apk-name.apk.idsig", form.v4SignatureOutputFileName)
    }

    @Test fun junkNamingUsesUnderscoresAndTheOriginalFixedVersion() {
        val form = JunkCodeInfo()
        form.packageName = "com.example.test"
        assertEquals("junk_com_example_test_plugin_TT2.2.0.aar", form.aarName)
        form.suffix = "abc"
        assertEquals("junk_com_example_test_abc_TT2.2.0.aar", form.aarName)
        form.packageName = ""
        assertEquals("junk__abc_TT2.2.0.aar", form.aarName)
    }

    @Test fun junkMultiRangeWithEqualBoundsMatchesRepeatedSingleEstimate() {
        val form = JunkCodeInfo(packageCount = "1", activityCountPerPackage = "1", aarCount = "1",
            leastPackageCount = "1", maximumPackageCount = "1",
            leastActivityCountPerPackage = "1", maximumActivityCountPerPackage = "1")
        val single = form.estimateAarSize(JunkMode.SINGLE)
        assertEquals("$single ~ $single", form.estimateAarSize(JunkMode.MULTI))
    }
}
