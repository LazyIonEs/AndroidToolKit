package org.tool.kit.tests.feature.apk

import kotlin.test.*
import org.junit.Test
import org.tool.kit.feature.apk.*
import org.tool.kit.feature.apk.ApkToolIntent.*
import org.tool.kit.feature.signature.SigningCredentialsUi
import org.tool.kit.model.SignaturePolicy

class ApkToolFormReducerTest {
    @Test fun defaultsNumericFiltersAndTextFieldsKeepTheirValidationRules() {
        val original = ApkToolForm()
        assertEquals("org.apk.tool", original.packageName)
        assertEquals("30", original.targetSdkVersion); assertEquals("21", original.minSdkVersion)
        assertEquals("1", original.versionCode); assertEquals("1.0", original.versionName)
        assertEquals("HelloAndroid", original.appName); assertFalse(original.enableSign)
        assertEquals(SignaturePolicy.V3, original.policy)
        for (value in listOf("", "0", "0012", "2147483648", "999999999999", "-1", "2.3", "１２", " 12", "12\n", "abc")) {
            val accept = value.isEmpty() || value.matches(Regex("^\\d+$"))
            assertEquals(if (accept) value else "30", ApkToolFormReducer.field(original, TargetSdkChanged(value)).targetSdkVersion)
            assertEquals(if (accept) value else "21", ApkToolFormReducer.field(original, MinSdkChanged(value)).minSdkVersion)
            assertEquals(if (accept) value else "1", ApkToolFormReducer.field(original, VersionCodeChanged(value)).versionCode)
        }
        val fields = listOf(OutputPathChanged(" 中文 out "), IconPathChanged(" 中文.png "), PackageNameChanged(" org.name "),
            VersionNameChanged(" 2.3-beta "), AppNameChanged(" 中文 Empty "))
        val result = fields.fold(original, ApkToolFormReducer::field)
        assertEquals(" 中文 out ", result.outputPath); assertEquals(" 中文.png ", result.icon)
        assertEquals(" org.name ", result.packageName); assertEquals(" 2.3-beta ", result.versionName)
        assertEquals(" 中文 Empty ", result.appName)
    }

    @Test fun keyAndSigningToggleResetCredentialsOnlyWhenThePathChanges() {
        for (oldPath in listOf("", "old.jks")) for (newPath in listOf("", "old.jks", "new.jks")) {
            val form = ApkToolForm(credentials = SigningCredentialsUi(oldPath, "store", listOf("a", "b"), 1, "key"))
            val actual = ApkToolFormReducer.field(form, KeyPathChanged(newPath)).credentials
            val expected = if (oldPath == newPath) form.credentials else SigningCredentialsUi(path = newPath)
            assertEquals(expected, actual)
            if (newPath.isEmpty()) for (enabled in listOf(false, true)) {
                assertEquals(actual, ApkToolFormReducer.field(form, EnableSignChanged(enabled)).credentials)
            }
        }
    }

    @Test fun aliasClearsOnlyChangedSelectionAndStorePasswordKeepsAliasPassword() {
        val form = ApkToolForm(credentials = SigningCredentialsUi("key", "store", listOf("a", "b"), 1, "key-password"))
        assertEquals("key-password", ApkToolFormReducer.field(form, AliasChanged(1)).credentials.aliasPassword)
        assertEquals("", ApkToolFormReducer.field(form, AliasChanged(0)).credentials.aliasPassword)
        assertEquals(form.credentials.copy(storePassword = "new"), ApkToolFormReducer.field(form, StorePasswordChanged("new")).credentials)
        assertEquals(form.credentials.copy(aliasPassword = "new"), ApkToolFormReducer.field(form, AliasPasswordChanged("new")).credentials)
    }
}
