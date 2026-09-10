package org.tool.kit.domain.repository

import org.tool.kit.domain.junk.*

/** 执行单个或批量 AAR 生成，并汇总实际输出路径及大小。 */
fun interface JunkCodeRepository { suspend fun generate(request: GenerateJunkCodeRequest): GeneratedJunkCode }
/** 以包数和每包 Activity 数估算字节数，不触发文件生成。 */
fun interface JunkSizeEstimator { fun bytes(packageCount: Int, activityCount: Int): Long }
/** 为随机命名提供可替换的字符串来源，便于测试固定生成结果。 */
fun interface JunkTokenGenerator { fun generate(minimum: Int, maximum: Int): String }
