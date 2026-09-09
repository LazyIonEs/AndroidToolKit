package org.tool.kit.migration

import com.android.ide.common.signing.KeystoreHelper
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.repository.JvmKeyStoreRepository
import org.tool.kit.data.repository.JvmStorageRepository
import org.tool.kit.model.CopyMode
import org.tool.kit.model.FileSelectorType
import org.tool.kit.platform.DesktopFileSelection
import org.tool.kit.utils.formatClipboardValue
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.*

class Phase2PlatformTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun repositoryLoadsBothStoreTypesAndPreservesFailureResults() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
            val keys = JvmKeyStoreRepository(io)
            for (type in listOf("JKS", "PKCS12")) {
                val path = temporary.root.resolve("$type fixture.keystore")
                assertTrue(KeystoreHelper.createNewStore(type, path, "fixture-only", "fixture-only", "fixture",
                    "CN=Fixture,OU=Test,O=AndroidToolKit,L=Test,S=Test,C=CN", 1, 1024))
                assertEquals(listOf("fixture"), keys.loadAliases(path.path, "fixture-only"))
                assertNull(keys.loadAliases(path.path, "wrong"))
                assertTrue(keys.validateAliasPassword(path.path, "fixture-only", "fixture", "fixture-only"))
                assertFalse(keys.validateAliasPassword(path.path, "fixture-only", "fixture", "wrong"))
                assertFalse(keys.validateAliasPassword(path.path, "fixture-only", null, "fixture-only"))
                assertFalse(keys.validateAliasPassword(path.path, "fixture-only", "missing", "fixture-only"))
            }
            assertNull(keys.loadAliases(temporary.root.resolve("missing").path, "fixture-only"))
        }
    }

    @Test fun fileSelectionRetainsUriOrderSymlinksAndOriginalPickerAcceptance() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
            val files = DesktopFileSelection(io)
            val first = temporary.newFile("first space.apk").toPath()
            val second = temporary.newFile("second.jks").toPath()
            val missing = temporary.root.resolve("missing.apk").toPath()
            val link = temporary.root.resolve("broken-link.apk").toPath()
            Files.createSymbolicLink(link, missing)
            val uris = listOf(missing, first, second, link, first).map { it.toUri().toString() }
            assertEquals(listOf(first, second, link, first), files.resolveDrop(uris))
            assertEquals(first.toString(), files.acceptPickerPath(first.toString(), listOf(FileSelectorType.APK)))
            // The original extension check does not require existence. Preserve that rule for picker callbacks.
            assertEquals(missing.toString(), files.acceptPickerPath(missing.toString(), listOf(FileSelectorType.APK)))
            assertNull(files.acceptPickerPath(first.toString(), listOf(FileSelectorType.KEY)))
            assertNull(files.acceptPickerPath("image.webp", listOf(FileSelectorType.IMAGE)))
            assertNull(files.acceptPickerPath("image.PNG", listOf(FileSelectorType.IMAGE)))
            assertEquals("image.png", files.acceptPickerPath("image.png", listOf(FileSelectorType.IMAGE)))
            assertNull(files.acceptPickerPath(null, listOf(FileSelectorType.APK)))
            assertEquals(second.toString(), files.acceptPickerPath(second.toString(), listOf(FileSelectorType.KEY, FileSelectorType.APK)))
            val storage = JvmStorageRepository(io)
            assertTrue(storage.inspectPath(first.toString()).isFile)
            assertFalse(storage.inspectPath(missing.toString()).isFile)
            assertTrue(storage.inspectPath(temporary.root.path).isDirectory)
        }
    }

    @Test fun fingerprintFormattingKeepsAllFourModes() {
        val fingerprint = "Ab:Cd:01"
        assertEquals("AB:CD:01", formatClipboardValue(fingerprint, CopyMode.UPPERCASE_WITH_COLON))
        assertEquals("ab:cd:01", formatClipboardValue(fingerprint, CopyMode.LOWERCASE_WITH_COLON))
        assertEquals("ABCD01", formatClipboardValue(fingerprint, CopyMode.UPPERCASE_WITHOUT_COLON))
        assertEquals("abcd01", formatClipboardValue(fingerprint, CopyMode.LOWERCASE_WITHOUT_COLON))
    }
}
