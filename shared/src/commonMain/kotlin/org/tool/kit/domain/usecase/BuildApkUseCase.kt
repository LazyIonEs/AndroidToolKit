package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkBuildWorkspaces
import org.tool.kit.domain.repository.ApkToolRepository

class BuildApkUseCase(
    private val repository: ApkToolRepository,
    private val sign: SignApkUseCase,
    private val workspaces: ApkBuildWorkspaces,
) {
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
            val signing = request.signing?.let { sign(it.copy(inputPath = workspace.outputPath)) }
            currentCoroutineContext().ensureActive()
            // Legacy optional signing failures do not replace the build-complete notification.
            BuildApkOutcome.Success(workspace.outputPath, session.outputSize(), signing)
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { BuildApkOutcome.Failure(error.message) }
}
