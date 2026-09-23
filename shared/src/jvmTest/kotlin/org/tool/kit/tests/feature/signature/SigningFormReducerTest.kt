package org.tool.kit.tests.feature.signature

import java.io.File
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.feature.signature.*
import org.tool.kit.feature.signature.ApkSigningIntent.*

class SigningFormReducerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun pathAndPrefixChangesHandleExistingBlankAndMissingPaths() {
        val existing = temporary.newFile("中文 spaced.apk").path
        val folder = temporary.newFolder("folder.apk").path
        val missing = temporary.root.resolve("missing.apk").path
        val initial = ApkSignatureForm(apkPath = existing, outputPrefix = "前缀", v4FileName = "manual.idsig")
        val pathCases = listOf(
            existing to "manual.idsig",
            folder to "前缀-folder.apk.idsig",
            missing to "manual.idsig",
            "All" to "manual.idsig",
            "" to "-apk-name.apk.idsig",
            " " to " -apk-name.apk.idsig",
        )
        for ((path, expectedName) in pathCases) {
            val actual = reduceWithMetadata(initial, ApkPathChanged(path))
            assertEquals(path, actual.apkPath)
            assertEquals(expectedName, actual.v4FileName, "path: $path")
        }
        val prefixCases = listOf(
            Triple(existing, "", "中文 spaced.apk.idsig"),
            Triple(existing, "  ", "中文 spaced.apk.idsig"),
            Triple(existing, " prefix ", " prefix -中文 spaced.apk.idsig"),
            Triple(folder, "prefix", "prefix-folder.apk.idsig"),
            Triple("", "prefix", "prefix-apk-name.apk.idsig"),
            Triple(" ", "prefix", "prefix-apk-name.apk.idsig"),
            Triple("", "", "apk-name.apk.idsig"),
            Triple("", "  ", "apk-name.apk.idsig"),
            Triple(missing, "prefix", "manual.idsig"),
            Triple("All", "prefix", "manual.idsig"),
        )
        for ((path, prefix, expectedName) in prefixCases) {
            val actual = reduceWithMetadata(initial.copy(apkPath = path), PrefixChanged(prefix))
            assertEquals(prefix, actual.outputPrefix)
            assertEquals(expectedName, actual.v4FileName, "prefix: $path / $prefix")
        }
        assertEquals("中文 spaced.apk.idsig", reduceWithMetadata(ApkSignatureForm(), ApkPathChanged(existing)).v4FileName)
    }

    @Test fun credentialsResetOnlyForDifferentKeyAndDifferentAlias() {
        val keys = SigningCredentialsUi("same", "store password", listOf("a", "b"), 1, "alias password")
        val form = ApkSignatureForm(credentials = keys)
        assertEquals(form, SigningFormReducer.field(form, KeyPathChanged("same")))
        assertEquals(SigningCredentialsUi(path = "new"), SigningFormReducer.field(form, KeyPathChanged("new")).credentials)
        assertEquals(keys, SigningFormReducer.field(form, AliasChanged(1)).credentials)
        assertEquals(keys.copy(aliasIndex = 0, aliasPassword = ""), SigningFormReducer.field(form, AliasChanged(0)).credentials)
        assertEquals(keys.copy(storePassword = "new"), SigningFormReducer.field(form, StorePasswordChanged("new")).credentials)
        assertFalse(form.toString().contains("password"))
        assertFalse(StorePasswordChanged("private-store-value").toString().contains("private-store-value"))
        assertFalse(AliasPasswordChanged("private-alias-value").toString().contains("private-alias-value"))
    }

    @Test fun explicitV4NameAndSamePathOrPrefixDoNotRederive() {
        val input = temporary.newFile("input.apk").path
        val form = ApkSignatureForm(apkPath = input, outputPrefix = "prefix", v4FileName = "manual.idsig")
        assertEquals(form, reduceWithMetadata(form, ApkPathChanged(input)))
        assertEquals(form, reduceWithMetadata(form, PrefixChanged("prefix")))
        assertEquals("custom.idsig", SigningFormReducer.field(form, V4NameChanged("custom.idsig")).v4FileName)
    }

    private fun reduceWithMetadata(form: ApkSignatureForm, intent: ApkSigningIntent): ApkSignatureForm {
        val next = SigningFormReducer.field(form, intent)
        if (form.apkPath == next.apkPath && form.outputPrefix == next.outputPrefix) return next
        val file = File(next.apkPath)
        return SigningFormReducer.resolvedName(next, file.takeIf { it.exists() }?.name)
    }
}
