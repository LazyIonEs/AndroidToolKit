package org.tool.kit.tests.data

import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import com.android.apksig.KeyConfig
import com.android.ide.common.signing.KeystoreHelper
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.zip.*
import kotlin.test.*
import kotlinx.coroutines.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.repository.JvmKeyStoreRepository
import org.tool.kit.data.repository.JvmSignatureRepository
import org.tool.kit.data.source.mapApkVerification
import org.tool.kit.data.source.toCertificateInformation
import org.tool.kit.feature.signature.*
import org.tool.kit.platform.DesktopFileSelection

class CertificateMappingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun jksAndPkcs12KeepEveryOriginalCertificateFieldAndWrongPasswordBehavior() = runBlocking {
        for (type in listOf("JKS", "PKCS12")) {
            val file = store(type, "fixture-$type")
            val raw = KeyStore.getInstance(type)
            file.inputStream().use { raw.load(it, "fixture-only".toCharArray()) }
            val certificate = raw.getCertificate("fixture-$type") as X509Certificate
            val outcome = JvmSignatureRepository(Dispatchers.IO).verifyCertificate(file.path, "fixture-only", "fixture-$type").getOrThrow()
            assertTrue(outcome.isSuccess); assertFalse(outcome.isApk)
            assertEquals(file.path, outcome.path); assertEquals(file.name, outcome.name)
            val mapped = outcome.data.single()
            assertEquals(certificate.version.toString(), mapped.version)
            assertEquals(certificate.subjectX500Principal.name, mapped.subject)
            assertEquals(certificate.notBefore.toString(), mapped.validFrom)
            assertEquals(certificate.notAfter.toString(), mapped.validUntil)
            assertEquals("RSA", mapped.publicKeyType)
            assertEquals((certificate.publicKey as RSAPublicKey).modulus.toString(10), mapped.modulus)
            assertEquals(certificate.sigAlgName, mapped.signatureType)
            fun digest(algorithm: String) = MessageDigest.getInstance(algorithm).digest(certificate.encoded)
                .joinToString(":") { "%02X".format(it) }
            assertEquals(digest("MD5"), mapped.md5)
            assertEquals(digest("SHA-1"), mapped.sha1)
            assertEquals(digest("SHA-256"), mapped.sha256)
            val originalError = runCatching { file.inputStream().use { raw.load(it, "wrong".toCharArray()) } }.exceptionOrNull()
            val actualError = JvmSignatureRepository(Dispatchers.IO).verifyCertificate(file.path, "wrong", "fixture-$type").exceptionOrNull()
            assertNotNull(actualError); assertEquals(originalError?.message, actualError.message)
            assertTrue(JvmSignatureRepository(Dispatchers.IO).verifyCertificate(file.path, "fixture-only", "absent").isFailure)
            assertNull(JvmKeyStoreRepository(Dispatchers.IO).loadAliases(file.path, "wrong"))
        }
    }

    @Test fun emptyStoresHaveNoAliasesAndMalformedAndUnsignedApksUseOriginalFallback() = runBlocking {
        for (type in listOf("JKS", "PKCS12")) {
            val file = temporary.root.resolve("empty-$type.jks")
            val empty = KeyStore.getInstance(type); empty.load(null, "fixture-only".toCharArray())
            file.outputStream().use { empty.store(it, "fixture-only".toCharArray()) }
            assertEquals(emptyList(), JvmKeyStoreRepository(Dispatchers.IO).loadAliases(file.path, "fixture-only"))
            assertTrue(JvmSignatureRepository(Dispatchers.IO).verifyCertificate(file.path, "fixture-only", "absent").isFailure)
        }
        val unsigned = unsigned()
        val repository = JvmSignatureRepository(Dispatchers.IO)
        val rejected = repository.verifyApk(unsigned.path)
        assertTrue(rejected.isFailure)
        assertNull(rejected.exceptionOrNull()?.message, "No handled errors requests the original APK fallback")
        val damaged = temporary.root.resolve("损坏 file.apk").apply { writeText("not an apk") }
        val expected = runCatching { ApkVerifier.Builder(damaged).build().verify() }.exceptionOrNull()
        assertNotNull(expected)
        assertEquals(expected.message, repository.verifyApk(damaged.path).exceptionOrNull()?.message)
    }

    @Test fun realSignedApkKeepsVersionOrderAndV4RequiresTheSameExplicitSidecarAsBefore() = runBlocking {
        val input = unsigned()
        val signer = signer("first")
        val output = temporary.root.resolve("中文 signed.apk")
        val sidecar = temporary.root.resolve("signed.apk.idsig")
        ApkSigner.Builder(listOf(signer)).setInputApk(input).setOutputApk(output)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .setV4SigningEnabled(true).setV4SignatureOutputFile(sidecar).build().sign()
        val actual = JvmSignatureRepository(Dispatchers.IO).verifyApk(output.path).getOrThrow()
        assertTrue(actual.isSuccess)
        assertEquals(listOf("1", "2", "3"), actual.data.map { it.version })
        assertEquals(1, actual.data.map { it.sha256 }.distinct().size)
        val withSidecar = ApkVerifier.Builder(output).setV4SignatureFile(sidecar).build().verify()
        assertTrue(withSidecar.isVerifiedUsingV4Scheme)
        assertEquals(listOf("1", "2", "3", "4"), mapApkVerification(withSidecar, output).getOrThrow().data.map { it.version })
    }

    @Test fun multipleSignersKeepTheirOrderAndCertificatesStillDisplayWhenVerificationFails() = runBlocking {
        val input = unsigned()
        val output = temporary.root.resolve("multi.apk")
        val signers = listOf(signer("first"), signer("second"))
        ApkSigner.Builder(signers).setInputApk(input).setOutputApk(output)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign()
        val raw = ApkVerifier.Builder(output).build().verify()
        assertTrue(raw.isVerified)
        val actual = JvmSignatureRepository(Dispatchers.IO).verifyApk(output.path).getOrThrow()
        assertEquals(listOf("1", "1", "2", "2"), actual.data.map { it.version })
        assertEquals(raw.v1SchemeSigners.map { it.certificate.subjectX500Principal.name } +
            raw.v2SchemeSigners.map { it.certificate.subjectX500Principal.name }, actual.data.map { it.subject })
        // Alter a ZIP timestamp while retaining the signing block and valid ZIP structure.
        // The V2 content digest then fails after its certificates have been decoded.
        val tampered = temporary.root.resolve("tampered.apk")
        val bytes = output.readBytes()
        bytes[10] = (bytes[10].toInt() xor 1).toByte()
        tampered.writeBytes(bytes)
        val broken = ApkVerifier.Builder(tampered).build().verify()
        assertFalse(broken.isVerified)
        assertEquals(2, broken.v2SchemeSigners.size)
        val mapped = JvmSignatureRepository(Dispatchers.IO).verifyApk(tampered.path).getOrThrow()
        assertFalse(mapped.isSuccess)
        assertEquals(listOf("2", "2"), mapped.data.map { it.version })
    }

    @Test fun rotatedApkDisplaysV3CertificatesInTheV31Section() = runBlocking {
        val first = signer("old")
        val second = signer("rotated")
        fun lineageSigner(config: ApkSigner.SignerConfig) = com.android.apksig.SigningCertificateLineage.SignerConfig.Builder(
            config.keyConfig, config.certificates.single()).build()
        val lineage = com.android.apksig.SigningCertificateLineage.Builder(lineageSigner(first), lineageSigner(second)).build()
        val output = temporary.root.resolve("rotated.apk")
        ApkSigner.Builder(listOf(first, second)).setInputApk(unsigned()).setOutputApk(output)
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true).setV4SigningEnabled(false)
            .setSigningCertificateLineage(lineage).setMinSdkVersionForRotation(33).build().sign()
        val raw = ApkVerifier.Builder(output).build().verify()
        assertTrue(raw.isVerified)
        assertTrue(raw.v31SchemeSigners.isNotEmpty())
        assertNotEquals(raw.v3SchemeSigners.single().certificate, raw.v31SchemeSigners.single().certificate)
        val mapped = JvmSignatureRepository(Dispatchers.IO).verifyApk(output.path).getOrThrow()
        assertEquals(listOf("1", "2", "3", "3.1"), mapped.data.map { it.version })
        assertEquals(mapped.data[2].sha256, mapped.data[3].sha256,
            "The V3.1 section currently displays the V3 certificate list")
    }

    @Test fun ecCertificateKeepsOriginalEmptyRsaOnlyPublicKeyFields() {
        val keyPair = java.security.KeyPairGenerator.getInstance("EC").generateKeyPair()
        val principal = javax.security.auth.x500.X500Principal("CN=EC fixture")
        val builder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(principal, java.math.BigInteger.ONE,
            java.util.Date(0), java.util.Date(86400000), principal, keyPair.public)
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.private)
        val certificate = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(builder.build(signer))
        val mapped = certificate.toCertificateInformation("3")
        assertEquals("", mapped.publicKeyType)
        assertEquals("", mapped.modulus)
        assertEquals(certificate.sigAlgName, mapped.signatureType)
        assertEquals(certificate.subjectX500Principal.name, mapped.subject)
    }

    @Test fun supportedVerificationErrorsKeepTheirDisplayOrderAndTrailingNewline() {
        val input = unsigned()
        val raw = ApkVerifier.Builder(input).build().verify()
        raw.errors.clear()
        val included = ApkVerifier.IssueWithParams(ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY, arrayOf("fixture.txt"))
        raw.errors.add(included)
        raw.errors.add(ApkVerifier.IssueWithParams(ApkVerifier.Issue.JAR_SIG_NO_SIGNATURES, emptyArray()))
        assertEquals(included.toString() + "\n", mapApkVerification(raw, input).exceptionOrNull()?.message)
        raw.errors.remove(included)
        assertNull(mapApkVerification(raw, input).exceptionOrNull()?.message)
    }

    @Test fun droppedFilesAreFilteredAsAWholeThenOnlyFirstSurvivorIsConsidered() = runBlocking {
        val missing = temporary.root.resolve("missing.apk")
        val first = temporary.newFile("first.txt")
        val second = temporary.newFile("second.apk")
        val files = DesktopFileSelection(Dispatchers.IO).resolveDrop(listOf(missing, first, second).map { it.toURI().toString() })
        assertEquals(listOf(first.toPath(), second.toPath()), files)
        assertNull(signatureFileIntent(files.first().toString()), "An unsupported surviving first item never falls through to a later APK")
        assertIs<SignatureInformationIntent.VerifyApk>(signatureFileIntent(second.path))
        assertIs<SignatureInformationIntent.KeyStoreSelected>(signatureFileIntent("file.keystore"))
        assertNull(signatureFileIntent("UPPER.APK"))
        assertNull(signatureFileIntent("UPPER.JKS"))
    }

    private fun store(type: String, alias: String): File = temporary.root.resolve("中文 $alias.jks").also {
        assertTrue(KeystoreHelper.createNewStore(type, it, "fixture-only", "fixture-only", alias,
            "CN=$alias,OU=Test,O=AndroidToolKit,L=Test,S=Test,C=CN", 1, 2048))
    }
    private fun signer(alias: String): ApkSigner.SignerConfig {
        val file = store("JKS", alias)
        val info = KeystoreHelper.getCertificateInfo("JKS", file, "fixture-only", "fixture-only", alias)
        return ApkSigner.SignerConfig.Builder(alias, KeyConfig.Jca(info.key), listOf(info.certificate)).build()
    }
    private fun unsigned() = temporary.root.resolve("中文 unsigned.apk").also { output ->
        ZipFile(File(checkNotNull(System.getProperty("test.apkTemplate")))).use { source ->
            ZipOutputStream(output.outputStream()).use { target ->
                source.entries().asSequence().filterNot { it.name.startsWith("META-INF/") }.forEach { entry ->
                    target.putNextEntry(ZipEntry(entry.name)); source.getInputStream(entry).use { it.copyTo(target) }; target.closeEntry()
                }
            }
        }
    }
}
