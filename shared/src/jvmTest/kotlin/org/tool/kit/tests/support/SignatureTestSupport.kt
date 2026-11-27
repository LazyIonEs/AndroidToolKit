package org.tool.kit.tests.support

import org.tool.kit.domain.repository.SavedSigningProfile
import org.tool.kit.domain.repository.SigningProfileRepository
import org.tool.kit.domain.signature.*
import org.tool.kit.domain.signing.SigningCredentials

internal val signatureFixture = SignatureVerification(false, true, "/fixture.apk", "fixture.apk", listOf(
    CertificateInformation("1", "CN=Test Certificate,OU=Fixture,O=AndroidToolKit,L=Shanghai,ST=Shanghai,C=CN",
        "Wed Jan 01 08:00:00 CST 2025", "Thu Jan 01 08:00:00 CST 2026", "RSA", "12345678901234567890", "SHA256withRSA",
        "AB:CD:12:34", "AB:CD:12:34:EF:56", "AB:CD:12:34:EF:56:78:90")))

internal class TestSigningProfiles(initial: List<SigningCredentials> = emptyList()) : SigningProfileRepository {
    private val records = initial.toMutableList()
    val saves = mutableListOf<SigningCredentials>()

    override suspend fun list(): List<SavedSigningProfile> = records.map { it.profile() }

    override suspend fun load(profile: SavedSigningProfile): SigningCredentials? = records.firstOrNull { it.profile() == profile }

    override suspend fun save(credentials: SigningCredentials): List<SavedSigningProfile> {
        saves += credentials
        val profile = credentials.profile()
        records.removeAll { it.profile() == profile }
        records.add(0, credentials)
        return list()
    }

    private fun SigningCredentials.profile() = SavedSigningProfile(path, requireNotNull(alias))
}
