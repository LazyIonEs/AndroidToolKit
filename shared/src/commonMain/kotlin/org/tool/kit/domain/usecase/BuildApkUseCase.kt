package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkBuildWorkspaces
import org.tool.kit.domain.repository.ApkToolRepository

/** 编排模板解码、资源修改、APK 构建及可选签名，工作目录的生命周期由 workspaces 管理。 */
class BuildApkUseCase(
    private val repository: ApkToolRepository,
    private val sign: SignApkUseCase,
    private val workspaces: ApkBuildWorkspaces,
) {
    /**
     * 按一次提交的参数生成 APK。普通异常转为失败结果，协程取消继续向上传播。
     * 构建成功与可选签名的结果分别保留，签名失败不会抹去已生成的未签名 APK。
     */
    suspend operator fun invoke(request: BuildApkRequest): BuildApkOutcome = try {
        val versionCode = request.versionCode.toInt()
        workspaces.use(request) { workspace ->
            currentCoroutineContext().ensureActive()
            val session = repository.decode(workspace, request)
            session.updateManifest()
            session.updateAppName()
            if (request.iconPath.isNotBlank()) session.copyIcon()
            session.saveMetadata(versionCode)
            session.build()
            currentCoroutineContext().ensureActive()
            // 签名输入必须使用本次实际构建的输出路径，而不是表单中预先保存的路径。
            val signing = request.signing?.let { sign(it.copy(inputPath = workspace.outputPath)) }
            currentCoroutineContext().ensureActive()
            // Optional signing failures do not replace the build-complete notification.
            BuildApkOutcome.Success(workspace.outputPath, session.outputSize(), signing)
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { BuildApkOutcome.Failure(error.message) }
}
