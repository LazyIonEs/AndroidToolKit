package org.tool.kit.tests.data

import com.android.ide.common.signing.KeystoreHelper
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeyStoreFixtureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun originalGeneratorProducesReadableStoresForAllTypeAndSizeCombinations() {
        for (type in listOf("JKS", "PKCS12")) for (size in listOf(1024, 2048)) {
            val file = temporary.root.resolve("fixture-$type-$size.keystore")
            assertTrue(KeystoreHelper.createNewStore(type, file, "fixture-only", "fixture-only", "fixture",
                "CN=Fixture,OU=Test,O=AndroidToolKit,L=Test,S=Test, C=CN", 1, size))
            val keyStore = KeyStore.getInstance(type)
            file.inputStream().use { keyStore.load(it, "fixture-only".toCharArray()) }
            assertEquals(listOf("fixture"), keyStore.aliases().toList())
            assertNotNull(keyStore.getKey("fixture", "fixture-only".toCharArray()))
            assertEquals(size, (keyStore.getCertificate("fixture").publicKey as RSAPublicKey).modulus.bitLength())
        }
    }
}
