package org.tool.kit.domain.junk

/** 区分单个 AAR 的确定配置和批量生成时的数量范围。 */
sealed interface JunkConfiguration {
    data class Single(val appPackageName: String, val packageCount: Int, val activityCount: Int, val resPrefix: String) : JunkConfiguration
    data class Multi(val outputDir: String, val aarCount: Int, val leastPackages: Int, val maximumPackages: Int,
        val leastActivities: Int, val maximumActivities: Int) : JunkConfiguration
}

data class GenerateJunkCodeRequest(val outputPath: String, val configuration: JunkConfiguration)
data class GeneratedJunkCode(val outputPath: String, val archivePaths: List<String>, val totalBytes: Long)
sealed interface GenerateJunkCodeOutcome {
    data class Success(val result: GeneratedJunkCode) : GenerateJunkCodeOutcome
    data class Failure(val message: String?) : GenerateJunkCodeOutcome
}

/** 预计输出字节数；maximum 为 null 表示单个估计值，否则表示大小区间。 */
data class JunkSizeEstimate(val minimum: Long, val maximum: Long? = null)
