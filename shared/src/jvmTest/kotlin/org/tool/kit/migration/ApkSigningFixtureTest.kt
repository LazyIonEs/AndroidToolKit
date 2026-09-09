package org.tool.kit.migration

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.ide.common.signing.KeystoreHelper
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Generate replayable signature fixtures with the same engine, then verify independently. */
class ApkSigningFixtureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun unsignedAndFiveSignaturePoliciesHaveTheExpectedSchemes() {
        val unsigned = temporary.root.resolve("中文 unsigned.apk")
        ZipFile(File(checkNotNull(System.getProperty("migration.apkTemplate")))).use { source ->
            ZipOutputStream(unsigned.outputStream()).use { output ->
                source.entries().asSequence().filterNot { it.name.startsWith("META-INF/") }.forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    source.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        assertFalse(ApkVerifier.Builder(unsigned).build().verify().isVerified)
        val store = temporary.root.resolve("fixture.jks")
        assertTrue(KeystoreHelper.createNewStore("JKS", store, "fixture-only", "fixture-only", "fixture",
            "CN=Fixture,OU=Test,O=AndroidToolKit,L=Test,S=Test,C=CN", 1, 2048))
        val certificate = KeystoreHelper.getCertificateInfo("JKS", store, "fixture-only", "fixture-only", "fixture")
        val config = ApkSigner.SignerConfig.Builder("CERT", KeyConfig.Jca(certificate.key), listOf(certificate.certificate)).build()
        // V1, V2, V2Only, V3, V4; ordinals are not used as policy flags.
        val schemes = linkedMapOf(
            "V1" to listOf(true, false, false, false),
            "V2" to listOf(true, true, false, false),
            "V2Only" to listOf(false, true, false, false),
            "V3" to listOf(true, true, true, false),
            "V4" to listOf(true, true, true, true),
        )
        schemes.forEach { (policy, flags) ->
            val output = temporary.root.resolve("$policy.apk")
            val idsig = temporary.root.resolve("$policy.apk.idsig")
            ApkSigner.Builder(listOf(config)).setInputApk(unsigned).setOutputApk(output)
                .setAlignFileSize(true).setAlignmentPreserved(false)
                .setV1SigningEnabled(flags[0]).setV2SigningEnabled(flags[1])
                .setV3SigningEnabled(flags[2]).setV4SigningEnabled(flags[3])
                .setV4SignatureOutputFile(idsig).setV4ErrorReportingEnabled(true).build().sign()
            val builder = ApkVerifier.Builder(output).setMinCheckedPlatformVersion(if (policy == "V2Only") 24 else 21)
            // A targetSdk 30 APK signed only with V1 is not installable on newer Android.
            // This fixture proves its V1 signature on the platforms that accept that scheme.
            if (policy == "V1") builder.setMaxCheckedPlatformVersion(23)
            if (flags[3]) builder.setV4SignatureFile(idsig)
            val verified = builder.build().verify()
            assertTrue(verified.isVerified, "$policy: ${verified.errors}")
            assertEquals(flags, listOf(verified.isVerifiedUsingV1Scheme, verified.isVerifiedUsingV2Scheme,
                verified.isVerifiedUsingV3Scheme, verified.isVerifiedUsingV4Scheme), policy)
        }
    }
}
