package org.tool.kit.tests.support

import org.tool.kit.domain.signature.*

internal val signatureFixture = SignatureVerification(false, true, "/fixture.apk", "fixture.apk", listOf(
    CertificateInformation("1", "CN=Test Certificate,OU=Fixture,O=AndroidToolKit,L=Shanghai,ST=Shanghai,C=CN",
        "Wed Jan 01 08:00:00 CST 2025", "Thu Jan 01 08:00:00 CST 2026", "RSA", "12345678901234567890", "SHA256withRSA",
        "AB:CD:12:34", "AB:CD:12:34:EF:56", "AB:CD:12:34:EF:56:78:90")))
