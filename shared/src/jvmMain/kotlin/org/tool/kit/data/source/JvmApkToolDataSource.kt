package org.tool.kit.data.source

import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.res.xml.ResXmlUtils
import brut.directory.ExtFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.*
import org.tool.kit.utils.renameManifestPackage
import org.tool.kit.utils.renameValueAppName
import java.io.File

class JvmApkToolDataSource(private val template: File, private val io: CoroutineDispatcher) : ApkToolRepository {
    override suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession = withContext(io) {
        ensureActive()
        val directory = File(workspace.directory)
        val config = Config(request.versionName).apply {
            isAnalysisMode = true
            isForced = true
            isDebuggable = true
            workspace.frameworkDirectory?.let { frameworkDirectory = it }
        }
        val decoder = ApkDecoder(ExtFile(template), config)
        decoder.decode(directory)
        val info = decoder.apkInfo
        object : ApkBuildSession {
            override suspend fun updateManifest() = withContext(io) {
                ensureActive()
                val manifest = File(directory, "AndroidManifest.xml")
                ResXmlUtils.removeManifestVersions(manifest)
                renameManifestPackage(manifest, request.packageName, request.minSdkVersion, request.targetSdkVersion)
            }
            override suspend fun updateAppName() = withContext(io) {
                ensureActive()
                renameValueAppName(File(directory, "res/values/strings.xml"), request.appName)
            }
            override suspend fun copyIcon() = withContext(io) {
                ensureActive()
                val source = File(request.iconPath)
                for (density in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
                    ensureActive()
                    val folder = File(directory, "res/mipmap-$density")
                    folder.deleteRecursively()
                    source.copyTo(File(folder, "ic_launcher.${source.extension}"), overwrite = true)
                }
            }
            override suspend fun saveMetadata(versionCode: Int) = withContext(io) {
                ensureActive()
                info.versionInfo.versionCode = versionCode
                info.versionInfo.versionName = request.versionName
                info.sdkInfo.minSdkVersion = request.minSdkVersion
                info.sdkInfo.targetSdkVersion = request.targetSdkVersion
                info.save(directory)
            }
            override suspend fun build() = withContext(io) {
                ensureActive()
                ExtFile(directory).use { ApkBuilder(it, config).build(File(workspace.outputPath)) }
                ensureActive()
            }
            override suspend fun outputSize() = withContext(io) { File(workspace.outputPath).length() }
        }
    }
}
