package org.tool.kit.tests.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.tool.kit.data.repository.JvmSigningProfileRepository
import org.tool.kit.domain.repository.SavedSigningProfile
import org.tool.kit.domain.signing.SigningCredentials
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SigningProfileRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun profilesSurviveReopeningAndUpdateByPathAndAlias() = runTest {
        val file = temporary.root.resolve("profiles.json").toPath()
        val key = ByteArray(32) { it.toByte() }
        val repository = JvmSigningProfileRepository(Dispatchers.Unconfined, file) { key.copyOf() }
        val release = SigningCredentials("/密钥文件.jks", "fixture store password", "release", "fixture alias password")
        val preview = release.copy(alias = "preview", aliasPassword = "fixture preview password 😀")
        val releaseProfile = SavedSigningProfile(release.path, "release")
        val previewProfile = SavedSigningProfile(preview.path, "preview")

        assertEquals(emptyList(), repository.list())
        assertEquals(listOf(releaseProfile), repository.save(release))
        assertEquals(listOf(previewProfile, releaseProfile), repository.save(preview))
        val updated = release.copy(aliasPassword = "fixture updated password")
        assertEquals(listOf(releaseProfile, previewProfile), repository.save(updated))

        val reopened = JvmSigningProfileRepository(Dispatchers.Unconfined, file) { key.copyOf() }
        assertEquals(listOf(releaseProfile, previewProfile), reopened.list())
        assertEquals(updated, reopened.load(releaseProfile))
        assertEquals(preview, reopened.load(previewProfile))
        assertNull(reopened.load(SavedSigningProfile(release.path, "missing")))
        assertFalse(Files.readString(file).contains("fixture"))

        val inaccessible = JvmSigningProfileRepository(Dispatchers.Unconfined, file) { error("No credential store") }
        assertEquals(reopened.list(), inaccessible.list())
        assertFails { inaccessible.save(preview.copy(aliasPassword = "different password")) }
        assertEquals(preview, reopened.load(previewProfile))
    }
}
