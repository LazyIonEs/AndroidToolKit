package org.tool.kit.migration

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.*
import org.tool.kit.data.source.JvmApkSignerDataSource
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.SignApkUseCase
import com.android.apksig.ApkVerifier
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun cancellationBeforeIoDoesNotDeleteExistingOutputOrReportFailure() = runTest {
        val output = temporary.root.resolve("input-sign.apk").apply { writeText("original") }
        val sign = SignApkUseCase(JvmApkSignerDataSource(StandardTestDispatcher(testScheduler)))
        val request = SignApkRequest(temporary.root.resolve("input.apk").path, temporary.root.path,
            "", "-sign", true, true, false, "unused", ApkSigningPolicy.V2, "shared.idsig",
            SigningCredentials("unused", "unused", "unused", "unused"))
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            sign(request)
            kotlin.test.fail("Cancellation must not become a normal outcome")
        }
        job.cancelAndJoin(); runCurrent()
        assertEquals("original", output.readText())
        assertEquals(listOf(output.name), temporary.root.listFiles()!!.map { it.name })
    }

    @Test fun unsignedAndFiveSignaturePoliciesHaveTheExpectedSchemes() = runBlocking {
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
        val sign = SignApkUseCase(JvmApkSignerDataSource(Dispatchers.IO))
        // V1, V2, V2Only, V3, V4; ordinals are not used as policy flags.
        val schemes = linkedMapOf(
            "V1" to listOf(true, false, false, false),
            "V2" to listOf(true, true, false, false),
            "V2Only" to listOf(false, true, false, false),
            "V3" to listOf(true, true, true, false),
            "V4" to listOf(true, true, true, true),
        )
        schemes.forEach { (policy, flags) ->
            val idsig = temporary.root.resolve("$policy.apk.idsig")
            val request = SignApkRequest(unsigned.path, temporary.root.path, "", "", false,
                true, false, "unused", ApkSigningPolicy.valueOf(policy), idsig.name,
                SigningCredentials(store.path, "fixture-only", "fixture", "fixture-only"))
            val actualInput = unsigned.copyTo(temporary.root.resolve("$policy unsigned.apk"))
            val namedRequest = request.copy(inputPath = actualInput.path, suffix = "-signed")
            val outcome = sign(namedRequest) as SignApkOutcome.Success
            val signedOutput = File(outcome.outputPath)
            assertTrue(outcome.outputExists)
            assertEquals("$policy unsigned-signed.apk", signedOutput.name)
            assertEquals(SignApkOutcome.OutputAlreadyExists(signedOutput.name), sign(namedRequest))
            assertTrue(sign(namedRequest.copy(overwrite = true)) is SignApkOutcome.Success)
            assertTrue(sign(namedRequest.copy(overwrite = true, credentials = request.credentials.copy(storePassword = "wrong"))) is SignApkOutcome.Failure)
            assertTrue(sign(namedRequest.copy(overwrite = true)) is SignApkOutcome.Success)
            val builder = ApkVerifier.Builder(signedOutput).setMinCheckedPlatformVersion(if (policy == "V2Only") 24 else 21)
            // A targetSdk 30 APK signed only with V1 is not installable on newer Android.
            // This fixture proves its V1 signature on the platforms that accept that scheme.
            if (policy == "V1") builder.setMaxCheckedPlatformVersion(23)
            if (flags[3]) builder.setV4SignatureFile(idsig)
            val verified = builder.build().verify()
            assertTrue(verified.isVerified, "$policy: ${verified.errors}")
            assertEquals(flags, listOf(verified.isVerifiedUsingV1Scheme, verified.isVerifiedUsingV2Scheme,
                verified.isVerifiedUsingV3Scheme, verified.isVerifiedUsingV4Scheme), policy)
        }
        val batchInputs = listOf("oppo", "vivo", "huawei", "xiaomi", "qq", "honor").map { title ->
            unsigned.copyTo(temporary.root.resolve("$title.apk"))
        }
        val batch = sign.batch(batchInputs.map { input -> SignApkRequest(input.path, temporary.root.path,
            "批量 prefix", "-signed", false, true, true, batchInputs[2].path, ApkSigningPolicy.V2,
            "shared.idsig", SigningCredentials(store.path, "fixture-only", "fixture", "fixture-only")) })
        assertEquals(batchInputs.map { "批量 prefix-${it.nameWithoutExtension}-signed.apk" },
            batch.map { File((it as SignApkOutcome.Success).outputPath).name })
        batch.forEach { result ->
            val verified = ApkVerifier.Builder(File((result as SignApkOutcome.Success).outputPath)).setMinCheckedPlatformVersion(21).build().verify()
            assertTrue(verified.isVerified, verified.errors.toString())
            assertTrue(verified.isVerifiedUsingV1Scheme); assertTrue(verified.isVerifiedUsingV2Scheme)
        }
    }
}
