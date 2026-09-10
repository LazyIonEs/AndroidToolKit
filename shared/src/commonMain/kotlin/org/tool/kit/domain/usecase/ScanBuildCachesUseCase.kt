package org.tool.kit.domain.usecase

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.flow
import org.tool.kit.domain.cleaner.BuildDirectory
import org.tool.kit.domain.repository.BuildCachesRepository

class ScanBuildCachesUseCase(private val repository: BuildCachesRepository) {
    operator fun invoke(root: String) = repository.scan(root)
}

class DeleteBuildCachesUseCase(private val repository: BuildCachesRepository) {
    operator fun invoke(directories: List<BuildDirectory>): kotlinx.coroutines.flow.Flow<org.tool.kit.domain.cleaner.DeleteBuildCacheResult> {
        val selected = directories.toList()
        return flow {
            for (directory in selected) {
                currentCoroutineContext().ensureActive()
                val result = repository.delete(directory)
                currentCoroutineContext().ensureActive()
                emit(result)
            }
        }
    }
}
