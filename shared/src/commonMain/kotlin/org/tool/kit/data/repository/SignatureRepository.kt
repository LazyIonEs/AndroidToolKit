package org.tool.kit.data.repository

import com.android.apksig.ApkVerifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tool.kit.model.Verifier
import org.tool.kit.model.VerifierResult
import org.tool.kit.utils.getVerifier
import java.io.File

private val logger = KotlinLogging.logger("SignatureRepository")

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 10:29
 */
class SignatureRepository {
    /**
     * APK签名信息
     * @param input 输入APK的路径
     */
    suspend fun apkVerifier(input: String): Result<VerifierResult> = withContext(Dispatchers.IO) {
        val list = ArrayList<Verifier>()
        val inputFile = File(input)
        val path = inputFile.path
        val name = inputFile.name
        val verifier: ApkVerifier = ApkVerifier.Builder(inputFile).build()
        logger.info { "apkVerifier 获取APK签名信息开始, APK文件路径: $input" }
        try {
            val result = verifier.verify()
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
                        list.add(cert.getVerifier("1"))
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
                        list.add(cert.getVerifier("2"))
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
                        list.add(cert.getVerifier("3"))
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
                        list.add(cert.getVerifier("3.1"))
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
                        list.add(cert.getVerifier("4"))
                    }
                    signer.errors.filter { it.issue == ApkVerifier.Issue.JAR_SIG_UNPROTECTED_ZIP_ENTRY }
                        .forEach {
                            error += it.toString() + "\n"
                        }
                }
            }

            if (isSuccess || list.isNotEmpty()) {
                val apkVerifierResult = VerifierResult(isSuccess, true, path, name, list)
                logger.info { "apkVerifier 获取APK签名信息结束, 结果: $apkVerifierResult" }
                Result.success(apkVerifierResult)
            } else {
                logger.error { "apkVerifier 获取APK签名信息异常, 异常信息: $error" }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            logger.error(e) { "apkVerifier 获取APK签名信息异常, 异常信息: ${e.message}" }
            Result.failure(e)
        }
    }
}