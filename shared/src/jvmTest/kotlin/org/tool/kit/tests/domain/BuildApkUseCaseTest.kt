package org.tool.kit.tests.domain

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.*
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.*

@OptIn(ExperimentalCoroutinesApi::class)
class BuildApkUseCaseTest {
    private val credentials = SigningCredentials("fixture.jks", "store", "alias", "key")
    private val signing = SignApkRequest("ignored", "out", "", "-sign", true, true, false,
        "huawei", ApkSigningPolicy.V3, "apk-name.apk.idsig", credentials)
    private fun request(icon: String = "icon.png", sign: Boolean = true) = BuildApkRequest(
        "out", icon, "org.fixture", "30", "21", "12", "2.0", "中文 Empty", if (sign) signing else null)

    private class Pipeline(private val failureAt: String? = null) : ApkToolRepository, ApkBuildSession, ApkBuildWorkspaces {
        val calls = mutableListOf<String>()
        var version = 0
        private fun step(name: String) { calls += name; if (name == failureAt) error("failed-$name") }
        override suspend fun <T> use(request: BuildApkRequest, block: suspend (ApkBuildWorkspace) -> T): T {
            step("workspace")
            return try { block(ApkBuildWorkspace("owned", "out/${request.outputFileName}")) }
            finally { calls += "cleanup" }
        }
        override suspend fun decode(workspace: ApkBuildWorkspace, request: BuildApkRequest): ApkBuildSession { step("decode"); return this }
        override suspend fun updateManifest() = step("manifest")
        override suspend fun updateAppName() = step("name")
        override suspend fun copyIcon() = step("icon")
        override suspend fun saveMetadata(versionCode: Int) { version = versionCode; step("metadata") }
        override suspend fun build() = step("build")
        override suspend fun outputSize(): Long { step("size"); return 2048 }
    }

    @Test fun pipelinePreservesOrderAndUsesTheSharedSignerWithTheBuiltPath() = runTest {
        val pipeline = Pipeline()
        val requests = mutableListOf<SignApkRequest>()
        val useCase = BuildApkUseCase(pipeline, SignApkUseCase {
            pipeline.calls += "sign"; requests += it; SignApkOutcome.Success("out/signed.apk", true)
        }, pipeline)
        val result = useCase(request()) as BuildApkOutcome.Success
        assertEquals(listOf("workspace", "decode", "manifest", "name", "icon", "metadata", "build", "sign", "size", "cleanup"), pipeline.calls)
        assertEquals(12, pipeline.version)
        assertEquals(signing.copy(inputPath = "out/中文 Empty.apk"), requests.single())
        assertEquals("out/中文 Empty.apk", result.outputPath)
        assertEquals(2048, result.sizeBytes)
        assertEquals(SignApkOutcome.Success("out/signed.apk", true), result.signing)
    }

    @Test fun blankIconAndDisabledSigningSkipOnlyThoseSteps() = runTest {
        val pipeline = Pipeline()
        val useCase = BuildApkUseCase(pipeline, SignApkUseCase { error("No signing") }, pipeline)
        val result = useCase(request(icon = " ", sign = false)) as BuildApkOutcome.Success
        assertNull(result.signing)
        assertEquals(listOf("workspace", "decode", "manifest", "name", "metadata", "build", "size", "cleanup"), pipeline.calls)
    }

    @Test fun everyPipelineFailureStopsFurtherWorkAndCleansItsWorkspace() = runTest {
        val steps = listOf("decode", "manifest", "name", "icon", "metadata", "build", "size")
        for (step in steps) {
            val pipeline = Pipeline(step)
            val useCase = BuildApkUseCase(pipeline, SignApkUseCase { error("No signing") }, pipeline)
            assertEquals(BuildApkOutcome.Failure("failed-$step"), useCase(request(sign = false)))
            assertEquals(listOf("workspace") + steps.take(steps.indexOf(step) + 1) + "cleanup", pipeline.calls)
        }
        val invalid = Pipeline()
        val result = BuildApkUseCase(invalid, SignApkUseCase { error("No signing") }, invalid)(request().copy(versionCode = "999999999999"))
        assertIs<BuildApkOutcome.Failure>(result)
        assertTrue(invalid.calls.isEmpty(), "Version parsing precedes decoding, as before")
    }

    @Test fun optionalSigningOutcomesRemainSeparateFromUnsignedBuildSuccess() = runTest {
        val outcomes = listOf(SignApkOutcome.Failure("wrong password"), SignApkOutcome.Failure(null),
            SignApkOutcome.OutputAlreadyExists("existing.apk"), SignApkOutcome.Success("missing.apk", false))
        for (signingOutcome in outcomes) {
            val pipeline = Pipeline()
            val useCase = BuildApkUseCase(pipeline, SignApkUseCase { signingOutcome }, pipeline)
            assertEquals(BuildApkOutcome.Success("out/中文 Empty.apk", 2048, signingOutcome), useCase(request()))
            assertEquals("cleanup", pipeline.calls.last())
        }
    }

    @Test fun cancellationDuringSigningPropagatesAndStillCleans() = runTest {
        val pipeline = Pipeline()
        val entered = CompletableDeferred<Unit>()
        val useCase = BuildApkUseCase(pipeline, SignApkUseCase { entered.complete(Unit); awaitCancellation() }, pipeline)
        val job = launch { useCase(request()); fail("Cancelled builds cannot publish a result") }
        entered.await(); job.cancelAndJoin()
        assertEquals("cleanup", pipeline.calls.last())
        assertFalse("size" in pipeline.calls)
    }
}
