package org.tool.kit.data.repository

import com.android.apksig.ApkVerifier
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import org.tool.kit.domain.signature.SignatureVerification
import org.tool.kit.data.source.mapApkVerification
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
 * @description : 在 IO 线程读取 APK 签名及密钥库证书，并映射为业务结果
 * @createDate  : 2026/1/29 10:29
 */
class JvmSignatureRepository(private val io: CoroutineDispatcher) : SignatureRepository {
    /**
     * APK签名信息
     * @param path 输入APK的路径
     */
    override suspend fun verifyApk(path: String): Result<SignatureVerification> = withContext(io) {
        val inputFile = File(path)
        logger.info { "apkVerifier 获取APK签名信息开始, APK文件路径: $path" }
        try {
            val result = ApkVerifier.Builder(inputFile).build().verify()
            mapApkVerification(result, inputFile)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            logger.error(e) { "apkVerifier 获取APK签名信息异常, 异常信息: ${e.message}" }
            Result.failure(e)
        }
    }

    /** 读取指定别名的 X.509 证书并映射显示字段；普通读取错误转为 Result.failure。 */
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
