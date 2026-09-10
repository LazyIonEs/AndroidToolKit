package org.tool.kit.tests.support

import org.tool.kit.data.generator.JunkSizePredictor
import org.tool.kit.domain.repository.JunkTokenGenerator
import org.tool.kit.domain.preferences.PreferencesRepository
import org.tool.kit.domain.repository.JunkCodeRepository
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.domain.usecase.EstimateJunkSizeUseCase
import org.tool.kit.domain.usecase.GenerateJunkCodeUseCase
import org.tool.kit.feature.app.AppEffectSink
import org.tool.kit.feature.junk.JunkCodeViewModel

internal fun junkViewModel(preferences: PreferencesRepository, storage: StorageRepository, effects: AppEffectSink,
    repository: JunkCodeRepository = JunkCodeRepository { error("Unexpected generation") },
    tokens: JunkTokenGenerator = JunkTokenGenerator { _, _ -> "fixture" }) = JunkCodeViewModel(GenerateJunkCodeUseCase(repository),
        EstimateJunkSizeUseCase(JunkSizePredictor::estimateAarSize), tokens, preferences, storage, effects)
