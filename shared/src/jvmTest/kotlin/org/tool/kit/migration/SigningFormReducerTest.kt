package org.tool.kit.migration

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.feature.signature.*
import org.tool.kit.feature.signature.ApkSigningIntent.*
import java.io.File
import kotlin.test.*

class SigningFormReducerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun pathAndPrefixSetterMatrixMatchesFrozenLegacyIncludingBlankAndMissingPaths() {
        val existing = temporary.newFile("中文 spaced.apk").path
        val folder = temporary.newFolder("folder.apk").path
        val missing = temporary.root.resolve("missing.apk").path
        val paths = listOf("", " ", existing, folder, missing, "All")
        val prefixes = listOf("", "  ", "前缀", " prefix ")
        for (start in paths) for (prefix in prefixes) for (next in paths) {
            val old = LegacySigningForm().also { it.apkPath = start; it.outputPrefix = prefix }
            var form = ApkSignatureForm(apkPath = old.apkPath, outputPrefix = old.outputPrefix, v4FileName = old.v4SignatureOutputFileName)
            old.apkPath = next
            form = reduceWithMetadata(form, ApkPathChanged(next))
            assertEquals(old.v4SignatureOutputFileName, form.v4FileName, "path: $start / $prefix / $next")
            assertEquals(old.apkPath, form.apkPath)
            for (nextPrefix in prefixes) {
                val legacy = old.copy().also { it.outputPrefix = nextPrefix }
                val reduced = reduceWithMetadata(form, PrefixChanged(nextPrefix))
                assertEquals(legacy.v4SignatureOutputFileName, reduced.v4FileName, "prefix: $next / $prefix / $nextPrefix")
                assertEquals(legacy.outputPrefix, reduced.outputPrefix)
            }
        }
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
