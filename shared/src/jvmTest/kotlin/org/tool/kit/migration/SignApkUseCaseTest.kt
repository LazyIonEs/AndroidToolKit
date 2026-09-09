package org.tool.kit.migration

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.tool.kit.domain.signing.*
import org.tool.kit.domain.usecase.SignApkUseCase
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SignApkUseCaseTest {
    private val request = SignApkRequest("input", "output", "", "-signed", false, true, true,
        "Huawei", ApkSigningPolicy.V2, "shared.idsig", SigningCredentials("key", "secret", "alias", "secret"))

    @Test fun schemeMatrixAndHuaweiAlignmentAreExplicit() {
        val expected = listOf(
            SigningSchemes(true, false, false, false), SigningSchemes(true, true, false, false),
            SigningSchemes(false, true, false, false), SigningSchemes(true, true, true, false),
            SigningSchemes(true, true, true, true))
        assertEquals(expected, ApkSigningPolicy.entries.map { it.schemes })
        for (policy in ApkSigningPolicy.entries) for (user in listOf(false, true))
            for (huawei in listOf(false, true)) for (input in listOf("Huawei", "Other")) {
                assertEquals((user && (!huawei || input != "Huawei")) || policy == ApkSigningPolicy.V4,
                    request.copy(policy = policy, userAlign = user, huaweiAlignment = huawei, inputPath = input).align)
            }
    }

    @Test fun namesPreserveBlankAndNonblankPrefixAndSuffixExactly() {
        assertEquals("中文 name-signed.apk", request.outputFileName("中文 name"))
        assertEquals("中文 name-signed.apk", request.copy(prefix = "  ").outputFileName("中文 name"))
        assertEquals(" pre -中文 name.apk.apk", request.copy(prefix = " pre ", suffix = ".apk").outputFileName("中文 name"))
        assertFalse(request.toString().contains("secret"))
    }

    @Test fun batchUsesImmutableRequestsAndInputOrderDespiteCompletionOrder() = runTest {
        val received = mutableListOf<SignApkRequest>()
        val completed = mutableListOf<String>()
        val useCase = SignApkUseCase { snapshot ->
            received += snapshot
            delay(if (snapshot.inputPath == "first") 20 else 1)
            completed += snapshot.inputPath
            SignApkOutcome.Success(snapshot.inputPath, true)
        }
        val inputs = listOf(request.copy(inputPath = "first"), request.copy(inputPath = "last"))
        val outcomes = useCase.batch(inputs)
        assertEquals(inputs, received)
        assertEquals(listOf("last", "first"), completed)
        assertEquals(listOf("first", "last"), outcomes.map { (it as SignApkOutcome.Success).outputPath })
        assertTrue(received.all { it.v4FileName == "shared.idsig" })
    }

    @Test fun partialFailureRemainsInItsInputPosition() = runTest {
        val useCase = SignApkUseCase { if (it.inputPath == "bad") SignApkOutcome.Failure("reason") else SignApkOutcome.Success(it.inputPath, true) }
        assertEquals(listOf(SignApkOutcome.Success("input", true), SignApkOutcome.Failure("reason")),
            useCase.batch(listOf(request, request.copy(inputPath = "bad"))))
    }

    @Test fun cancellationCancelsAllChildrenWithoutReturningFailure() = runTest {
        var finished = 0
        val useCase = SignApkUseCase { try { awaitCancellation() } finally { finished++ } }
        val job = launch { useCase.batch(listOf(request, request)); fail("cancelled batch returned") }
        runCurrent()
        job.cancelAndJoin()
        assertEquals(2, finished)
    }
}
