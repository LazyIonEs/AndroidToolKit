package org.tool.kit.data.source

import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.android.ide.common.signing.KeystoreHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tool.kit.domain.repository.ApkSigningRepository
import org.tool.kit.domain.signing.*
import java.io.File

class JvmApkSignerDataSource(private val io: CoroutineDispatcher) : ApkSigningRepository {
    /** 在 IO 线程完成输出冲突处理、密钥加载和 APK 签名；保留覆盖策略与签名方案设置。 */
    override suspend fun sign(request: SignApkRequest): SignApkOutcome = withContext(io) {
        try {
            ensureActive()
            val input = File(request.inputPath)
            val output = File(request.outputDirectory, request.outputFileName(input.nameWithoutExtension))
            if (output.exists()) {
                if (request.overwrite) output.delete()
                else return@withContext SignApkOutcome.OutputAlreadyExists(output.name)
            }
            val credentials = request.credentials
            val certificate = KeystoreHelper.getCertificateInfo("JKS", File(credentials.path),
                credentials.storePassword, credentials.aliasPassword, credentials.alias)
            // 签名引擎需要私钥和对应证书链；凭据只用于加载，不进入结果对象。
            val config = ApkSigner.SignerConfig.Builder("CERT", KeyConfig.Jca(certificate.key),
                listOf(certificate.certificate)).build()
            val schemes = request.policy.schemes
            ensureActive()
            ApkSigner.Builder(listOf(config)).setInputApk(input).setOutputApk(output)
                .setAlignFileSize(request.align).setAlignmentPreserved(!request.align)
                .setV1SigningEnabled(schemes.v1).setV2SigningEnabled(schemes.v2)
                .setV3SigningEnabled(schemes.v3).setV4SigningEnabled(schemes.v4)
                .setV4SignatureOutputFile(File(request.outputDirectory, request.v4FileName))
                .setV4ErrorReportingEnabled(true).build().sign()
            // The synchronous library may finish writing after cancellation; never publish that result.
            ensureActive()
            SignApkOutcome.Success(output.path, output.exists())
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { SignApkOutcome.Failure(error.message) }
    }
}
