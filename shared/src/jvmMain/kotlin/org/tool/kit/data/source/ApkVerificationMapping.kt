package org.tool.kit.data.source

import com.android.apksig.ApkVerifier
import org.tool.kit.domain.signature.CertificateInformation
import org.tool.kit.domain.signature.SignatureVerification
import java.io.File

/** Maps verifier results into the displayed signer order. */
internal fun mapApkVerification(result: ApkVerifier.Result, inputFile: File): Result<SignatureVerification> {
    val path = inputFile.path
    val name = inputFile.name
    val list = ArrayList<CertificateInformation>()
    var error = ""
    val isSuccess = result.isVerified

    result.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
        .forEach {
            error += it.toString() + "\n"
        }

    if (result.v1SchemeSigners.isNotEmpty()) {
        for (signer in result.v1SchemeSigners) {
            val cert = signer.certificate ?: continue
            if (signer.certificate.type == "X.509") {
                list.add(cert.toCertificateInformation("1"))
            }
            signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }
        }
    }

    if (result.v2SchemeSigners.isNotEmpty()) {
        for (signer in result.v2SchemeSigners) {
            val cert = signer.certificate ?: continue
            if (signer.certificate.type == "X.509") {
                list.add(cert.toCertificateInformation("2"))
            }
            signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }
        }
    }

    if (result.v3SchemeSigners.isNotEmpty()) {
        for (signer in result.v3SchemeSigners) {
            val cert = signer.certificate ?: continue
            if (signer.certificate.type == "X.509") {
                list.add(cert.toCertificateInformation("3"))
            }
            signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }
        }
    }

    if (result.v31SchemeSigners.isNotEmpty()) {
        for (signer in result.v3SchemeSigners) {
            val cert = signer.certificate ?: continue
            if (signer.certificate.type == "X.509") {
                list.add(cert.toCertificateInformation("3.1"))
            }
            signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }
        }
    }

    if (result.v4SchemeSigners.isNotEmpty()) {
        for (signer in result.v4SchemeSigners) {
            val cert = signer.certificate ?: continue
            if (signer.certificate.type == "X.509") {
                list.add(cert.toCertificateInformation("4"))
            }
            signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                .forEach {
                    error += it.toString() + "\n"
                }
        }
    }

    // 即使整包校验未通过，也允许展示已读到的证书；是否通过仍由 isSuccess 单独表达。
    return if (isSuccess || list.isNotEmpty()) {
        val apkVerifierResult = SignatureVerification(isSuccess, true, path, name, list.toList())
        Result.success(apkVerifierResult)
    } else {
        Result.failure(Exception(error.takeUnless { it.isBlank() }))
    }
}
