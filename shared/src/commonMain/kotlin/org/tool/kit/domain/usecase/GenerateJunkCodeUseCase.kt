package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.tool.kit.domain.junk.*
import org.tool.kit.domain.repository.JunkCodeRepository

class GenerateJunkCodeUseCase(private val repository: JunkCodeRepository) {
    suspend operator fun invoke(request: GenerateJunkCodeRequest): GenerateJunkCodeOutcome = try {
        currentCoroutineContext().ensureActive()
        val result = repository.generate(request)
        currentCoroutineContext().ensureActive()
        GenerateJunkCodeOutcome.Success(result)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        GenerateJunkCodeOutcome.Failure(error.message)
    }
}
