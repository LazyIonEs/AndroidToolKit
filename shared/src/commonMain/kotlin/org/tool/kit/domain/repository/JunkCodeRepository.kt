package org.tool.kit.domain.repository

import org.tool.kit.domain.junk.*

fun interface JunkCodeRepository { suspend fun generate(request: GenerateJunkCodeRequest): GeneratedJunkCode }
fun interface JunkSizeEstimator { fun bytes(packageCount: Int, activityCount: Int): Long }
fun interface JunkTokenGenerator { fun generate(minimum: Int, maximum: Int): String }
