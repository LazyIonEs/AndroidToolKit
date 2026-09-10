package org.tool.kit.tests.support

import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.usecase.*

fun unusedBuildApk() = BuildApkUseCase(object : ApkToolRepository {
    override suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession = error("Unexpected build")
}, SignApkUseCase { error("Unexpected signing") }, object : ApkBuildWorkspaces {
    override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T = error("Unexpected workspace")
})
