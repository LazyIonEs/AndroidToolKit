package org.tool.kit.data.source

import org.tool.kit.domain.signature.CertificateInformation
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.MessageDigest

fun X509Certificate.toCertificateInformation(version: String): CertificateInformation {
    val subject = this.subjectX500Principal.name
    val validFrom = this.notBefore.toString()
    val validUntil = this.notAfter.toString()
    val publicKeyType = (this.publicKey as? RSAPublicKey)?.algorithm ?: ""
    val modulus = (this.publicKey as? RSAPublicKey)?.modulus?.toString(10) ?: ""
    val signatureType = this.sigAlgName
    val md5 = getThumbPrint(this, "MD5") ?: ""
    val sha1 = getThumbPrint(this, "SHA-1") ?: ""
    val sha256 = getThumbPrint(this, "SHA-256") ?: ""
    val apkVerifier = CertificateInformation(
        version,
        subject,
        validFrom,
        validUntil,
        publicKeyType,
        modulus,
        signatureType,
        md5,
        sha1,
        sha256
    )
    return apkVerifier
}

fun getThumbPrint(cert: X509Certificate?, type: String?): String? {
    val md = MessageDigest.getInstance(type) // lgtm [java/weak-cryptographic-algorithm]
    val der: ByteArray = cert?.encoded ?: return null
    md.update(der)
    val digest = md.digest()
    return hexify(digest)
}

private fun hexify(bytes: ByteArray): String {
    val hexDigits = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    )
    val buf = StringBuilder(bytes.size * 3)
    for (aByte in bytes) {
        buf.append(hexDigits[aByte.toInt() and 0xf0 shr 4])
        buf.append(hexDigits[aByte.toInt() and 0x0f])
        buf.append(' ')
    }
    return buf.toString().trim().replace(' ', ':')
}

