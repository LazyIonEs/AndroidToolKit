package org.tool.kit.tests.data

import com.android.ide.common.signing.KeystoreHelper
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.naming.ldap.LdapName
import javax.security.auth.x500.X500Principal
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.repository.JvmKeyStoreRepository
import org.tool.kit.data.source.JvmKeyStoreDataSource
import org.tool.kit.domain.keystore.*
import org.tool.kit.domain.usecase.GenerateKeyStoreUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class KeyStoreGenerationDataTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun jks1024MatchesOriginalStructure() = verifyFixture(KeyStoreFormat.JKS, 1024)
    @Test fun jks2048MatchesOriginalStructure() = verifyFixture(KeyStoreFormat.JKS, 2048)
    @Test fun pkcs121024MatchesOriginalStructure() = verifyFixture(KeyStoreFormat.PKCS12, 1024)
    @Test fun pkcs122048MatchesOriginalStructure() = verifyFixture(KeyStoreFormat.PKCS12, 2048)

    private fun verifyFixture(format: KeyStoreFormat, size: Int) = runBlocking {
        val directory = temporary.newFolder("含空格 fixture ${format.name} $size")
        val request = request(directory, format, size)
        val generated = assertIs<GenerateKeyStoreOutcome.Success>(GenerateKeyStoreUseCase(JvmKeyStoreRepository(Dispatchers.IO))(request))
        val file = File(generated.outputPath)
        val actual = read(file, request)
        val oldFile = directory.resolve("old helper.keystore")
        withContext(Dispatchers.IO) {
            assertTrue(KeystoreHelper.createNewStore(format.name, oldFile, request.storePassword,
                request.aliasPassword, request.alias, dn(request), request.validityYears.toInt(), size))
        }
        val old = read(oldFile, request)
        assertEquals(old.subjectX500Principal, actual.subjectX500Principal)
        assertEquals(old.issuerX500Principal, actual.issuerX500Principal)
        assertEquals(old.sigAlgName, actual.sigAlgName)
        assertEquals(old.version, actual.version)
        assertEquals(old.serialNumber, actual.serialNumber)
        assertEquals(old.notAfter.time - old.notBefore.time, actual.notAfter.time - actual.notBefore.time)
        assertEquals(format.name, KeyStore.getInstance(file, request.storePassword.toCharArray()).type.uppercase())
    }

    @Test fun existingStoreRetainsOtherAliasesAndFailureDoesNotSilentlyCreateParentDirectories(): Unit = runBlocking {
        val directory = temporary.newFolder("existing")
        val repository = JvmKeyStoreRepository(Dispatchers.IO)
        val first = request(directory, KeyStoreFormat.JKS, 1024)
        assertIs<GenerateKeyStoreOutcome.Success>(repository.generate(first))
        assertIs<GenerateKeyStoreOutcome.Success>(repository.generate(first.copy(alias = "second")))
        assertEquals(setOf(first.alias, "second"), repository.loadAliases(directory.resolve(first.fileName).path, first.storePassword)?.toSet())
        val missing = directory.resolve("missing parent")
        assertIs<GenerateKeyStoreOutcome.Failure>(repository.generate(first.copy(outputDirectory = missing.path)))
        assertFalse(missing.exists())
        assertIs<GenerateKeyStoreOutcome.Failure>(repository.generate(first.copy(storePassword = "wrong password")))
    }

    @Test fun invalidValidityAndDnKeepOriginalFailureMessages() = runBlocking {
        val directory = temporary.newFolder("failures")
        val source = JvmKeyStoreDataSource(Dispatchers.IO)
        val base = request(directory, KeyStoreFormat.JKS, 1024)
        val nonNumeric = assertIs<GenerateKeyStoreOutcome.Failure>(source.generate(base.copy(validityYears = "not a number")))
        assertEquals(runCatching { "not a number".toInt() }.exceptionOrNull()?.message, nonNumeric.message)
        for (invalid in listOf(base.copy(validityYears = "0"), base.copy(authorName = "broken, DN"))) {
            val expected = runCatching {
                KeystoreHelper.createNewStore(invalid.format.name, directory.resolve("old failure.jks"),
                    invalid.storePassword, invalid.aliasPassword, invalid.alias, dn(invalid), invalid.validityYears.toInt(), invalid.keySize)
            }.exceptionOrNull()
            assertNotNull(expected)
            assertEquals(expected.message, assertIs<GenerateKeyStoreOutcome.Failure>(source.generate(invalid)).message)
        }
        assertFalse(directory.resolve(base.fileName).exists())
    }

    @Test fun generationDispatchesBeforeTouchingOutputAndCancellationBeforeIoEntryCanRetry() = runTest {
        val directory = temporary.newFolder("cancel")
        val source = JvmKeyStoreDataSource(StandardTestDispatcher(testScheduler))
        val request = request(directory, KeyStoreFormat.JKS, 1024)
        val first = async(start = CoroutineStart.UNDISPATCHED) { source.generate(request) }
        assertFalse(directory.resolve(request.fileName).exists(), "No synchronous output work on the caller")
        first.cancel(); runCurrent()
        assertTrue(first.isCancelled)
        assertFalse(directory.resolve(request.fileName).exists())
        val retry = async(start = CoroutineStart.UNDISPATCHED) { source.generate(request) }
        assertFalse(directory.resolve(request.fileName).exists())
        runCurrent()
        val outcome = assertIs<GenerateKeyStoreOutcome.Success>(retry.await())
        read(File(outcome.outputPath), request)
    }

    private fun request(directory: File, format: KeyStoreFormat, size: Int) = GenerateKeyStoreRequest(
        directory.path, "生成 fixture.jks", "fixture store password", "fixture alias password", "fixture",
        "2", "Fixture Author", "Fixture Unit", "Fixture Organization", "Shanghai", "Shanghai", "CN", format, size)

    private fun dn(request: GenerateKeyStoreRequest) = "CN=${request.authorName},OU=${request.organizationalUnit},O=${request.organization},L=${request.city},S=${request.province}, C=${request.countryCode}"

    private fun read(file: File, request: GenerateKeyStoreRequest): X509Certificate {
        val store = KeyStore.getInstance(request.format.name)
        file.inputStream().use { store.load(it, request.storePassword.toCharArray()) }
        assertEquals(listOf(request.alias), store.aliases().toList())
        val key = assertIs<RSAPrivateKey>(store.getKey(request.alias, request.aliasPassword.toCharArray()))
        assertEquals(request.keySize, key.modulus.bitLength())
        val certificate = assertIs<X509Certificate>(store.getCertificate(request.alias))
        assertEquals(request.keySize, (certificate.publicKey as RSAPublicKey).modulus.bitLength())
        assertEquals("SHA256withRSA", certificate.sigAlgName)
        // KeystoreHelper/BouncyCastle reverses the RDN sequence. Match fields here,
        // then compare the exact encoded principal with the original helper above.
        assertEquals(LdapName(X500Principal(dn(request)).name).rdns.toSet(),
            LdapName(certificate.subjectX500Principal.name).rdns.toSet())
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals(request.validityYears.toLong() * 365 * 24 * 60 * 60 * 1000,
            certificate.notAfter.time - certificate.notBefore.time)
        assertEquals(1, store.getCertificateChain(request.alias).size)
        certificate.checkValidity()
        certificate.verify(certificate.publicKey)
        return certificate
    }
}
