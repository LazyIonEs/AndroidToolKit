package org.tool.kit.data.repository

import com.android.apksig.ApkVerifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tool.kit.domain.signature.CertificateInformation
import org.tool.kit.domain.signature.SignatureVerification
import org.tool.kit.data.source.toCertificateInformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import java.security.KeyStore
import java.security.cert.X509Certificate
import org.tool.kit.domain.repository.SignatureRepository
import java.io.File

private val logger = KotlinLogging.logger("SignatureRepository")

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 10:29
 */
class JvmSignatureRepository(private val io: CoroutineDispatcher) : SignatureRepository {
    /**
     * APK签名信息
     * @param input 输入APK的路径
     */
    override suspend fun verifyApk(path: String): Result<SignatureVerification> = withContext(io) {
        val list = ArrayList<CertificateInformation>()
        val inputFile = File(path)
        val path = inputFile.path
        val name = inputFile.name
        logger.info { "apkVerifier 获取APK签名信息开始, APK文件路径: $path" }
        try {
            val result = ApkVerifier.Builder(inputFile).build().verify()
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

            if (isSuccess || list.isNotEmpty()) {
                val apkVerifierResult = SignatureVerification(isSuccess, true, path, name, list.toList())
                logger.info { "apkVerifier 获取APK签名信息结束, 结果: $apkVerifierResult" }
                Result.success(apkVerifierResult)
            } else {
                logger.error { "apkVerifier 获取APK签名信息异常, 异常信息: $error" }
                Result.failure(Exception(error.takeUnless { it.isBlank() }))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            logger.error(e) { "apkVerifier 获取APK签名信息异常, 异常信息: ${e.message}" }
            Result.failure(e)
        }
    }

    override suspend fun verifyCertificate(path: String, password: String, alias: String): Result<SignatureVerification> = withContext(io) {
        try {
            val file = File(path)
            val store = KeyStore.getInstance(KeyStore.getDefaultType())
            file.inputStream().use { store.load(it, password.toCharArray()) }
            val cert = store.getCertificate(alias)
            if (cert.type != "X.509") throw Exception("Key Certificate Type Is Not X509Certificate")
            cert as X509Certificate
            Result.success(SignatureVerification(true, false, path, file.name,
                listOf(cert.toCertificateInformation(cert.version.toString()))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
