package org.tool.kit.tests.data

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Test
import org.slf4j.LoggerFactory
import org.tool.kit.data.generator.JunkGenerationReport
import org.tool.kit.data.generator.formatJunkArchiveReport
import org.tool.kit.data.generator.formatJunkBatchReport
import org.tool.kit.data.generator.logJunkArchiveReport
import org.tool.kit.data.generator.logJunkBatchReport
import kotlin.test.*

class JunkGenerationLoggingTest {
    @Test fun archiveSummaryUsesMeasuredCountsAllRequestsAndLosslessReadableJson() {
        val report = archiveReport()
        val file = File("/tmp/中文输出/模块.aar")
        val text = formatJunkArchiveReport(file, report)
        assertTrue(text.contains("输出文件：${file.absolutePath}"))
        assertTrue(text.contains("随机种子：57"))
        assertTrue(text.contains("代码：类 4；方法 24；字段 8"))
        assertTrue(text.contains("Activity 2；独立 layout 2"))
        assertTrue(text.contains("实际条目 2 / 总上限 7"))
        assertTrue(text.contains("drawable=4"))
        assertTrue(text.contains("复用次数 / 全部资源请求）：30.00%；请求 10，复用 3"))
        assertTrue(text.contains("1048576 B (1.049 MB / 1.000 MiB)"))
        assertTrue(text.contains("总耗时：12 ms"))
        assertTrue(text.contains("代码生成=3 ms"))
        assertTrue(text.contains("layout 与必要 id 不占附加资源预算"))
        assertTrue(text.contains("DEX/APK：未实测"))
        assertTrue(text.contains("请验证宿主 multidex"))
        assertCompleteJsonEquals(report, text)
    }

    @Test fun missingStatisticsAreNotInventedAsMeasuredZero() {
        val report = JunkGenerationReport(mapOf("seed" to 9, "dexOrApkMeasured" to false))
        val text = formatJunkArchiveReport(File("/tmp/missing.aar"), report)
        val summary = text.substringBefore(JSON_MARKER)
        assertTrue(summary.contains("代码：类 未统计；方法 未统计；字段 未统计"))
        assertTrue(summary.contains("实际条目 未统计 / 总上限 未统计"))
        assertTrue(summary.contains("总耗时：未统计"))
        assertFalse(summary.contains("0.00%"))
        assertFalse(summary.contains("：0 B"))
        assertCompleteJsonEquals(report, text)
    }

    @Test fun batchSummaryUsesWeightedRequestRateAndStatesPartialCoverage() {
        val report = batchReport(measured = 2)
        val directory = File("/tmp/批量结果")
        val text = formatJunkBatchReport(directory, report)
        assertTrue(text.contains("输出目录：${directory.absolutePath}"))
        assertTrue(text.contains("批次随机种子：77；AAR 数量：3"))
        assertTrue(text.contains("已测 AAR 2；未统计 AAR 1"))
        assertTrue(text.contains("仅覆盖已测模块"))
        assertTrue(text.contains("整批附加资源总上限：11"))
        assertTrue(text.contains("累计复用次数 / 累计全部请求）：30.00%"))
        assertFalse(text.substringBefore(JSON_MARKER).contains("75.00%"))
        assertCompleteJsonEquals(report, text)
    }

    @Test fun batchWithNoMeasuredModulesLabelsAggregateZerosAsPlaceholders() {
        val report = batchReport(measured = 0)
        val text = formatJunkBatchReport(File("/tmp/unmeasured"), report)
        val summary = text.substringBefore(JSON_MARKER)
        assertTrue(summary.contains("已测 AAR 0；未统计 AAR 3"))
        assertTrue(summary.contains("占位值不视为实测"))
        assertFalse(summary.contains("代码：类 0"))
        assertFalse(summary.contains("实际条目：0"))
        assertFalse(summary.contains("0.00%"))
        assertCompleteJsonEquals(report, text)
    }

    @Test fun eachReportIsOneInfoEventOnExistingGenerationLogger() {
        val logger = LoggerFactory.getLogger("JunkGeneration") as Logger
        val previousLevel = logger.level
        val previousAdditive = logger.isAdditive
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.INFO
        logger.isAdditive = false
        logger.addAppender(appender)
        try {
            val archive = archiveReport()
            val batch = batchReport(measured = 2)
            logJunkArchiveReport(File("/tmp/logged.aar"), archive)
            logJunkBatchReport(File("/tmp/logged-batch"), batch)
            assertEquals(2, appender.list.size)
            appender.list.forEach {
                assertEquals(Level.INFO, it.level)
                assertEquals("JunkGeneration", it.loggerName)
                assertEquals(1, it.formattedMessage.split(JSON_MARKER).size - 1)
            }
            assertCompleteJsonEquals(archive, appender.list[0].formattedMessage)
            assertCompleteJsonEquals(batch, appender.list[1].formattedMessage)
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            logger.isAdditive = previousAdditive
            appender.stop()
        }
    }

    private fun archiveReport(): JunkGenerationReport = JunkGenerationReport(linkedMapOf(
        "schema" to 1, "seed" to 57, "codePackagePrefix" to "com.示例", "resourceNamespace" to "com.fixture.res",
        "activities" to 2,
        "policy" to mapOf("resources" to mapOf("total" to 7, "drawable" to 4, "mipmap" to 1, "anim" to 1, "string" to 1, "assets" to 0)),
        "code" to mapOf("classes" to 4, "methods" to 24, "fields" to 8, "classBytes" to 9000),
        "layouts" to mapOf("count" to 2, "bytes" to 1200),
        "resources" to mapOf("generated" to mapOf("drawable" to 2), "requests" to mapOf("drawable" to 10), "reused" to mapOf("drawable" to 3),
            "reuseRate" to 0.3, "optionalFiles" to 2, "optionalBytes" to 300, "valuesEntries" to 0, "ids" to 1, "assets" to 0, "assetBytes" to 0),
        "sizes" to mapOf("aarBytes" to 1048576, "classesJarBytes" to 7000),
        "totalMs" to 12, "phasesMs" to mapOf("classes" to 3), "dexOrApkMeasured" to false,
        "warnings" to listOf("DEX references exceed a single-dex planning threshold; verify host multidex and Release output"),
        "unknownFutureField" to listOf("完整保留中文、引号\"和换行\n", null, mapOf("nested" to true)),
    ))

    private fun batchReport(measured: Int): JunkGenerationReport = JunkGenerationReport(linkedMapOf(
        "seed" to 77, "aarCount" to 3, "archiveBytes" to 2000, "optionalResourceBudget" to 11,
        "aggregate" to mapOf(
            "measuredAars" to measured, "unmeasuredAars" to 3 - measured,
            "code" to mapOf("classes" to if (measured == 0) 0 else 4, "methods" to 24, "fields" to 8, "classBytes" to 9000),
            "layouts" to mapOf("count" to if (measured == 0) 0 else 2, "bytes" to 1200),
            "resources" to mapOf("generated" to mapOf("drawable" to 2), "optionalGeneratedCount" to if (measured == 0) 0 else 2,
                "requests" to mapOf("drawable" to 30), "reused" to mapOf("drawable" to 9),
                "requestReuseRate" to if (measured == 0) 0.0 else 0.3, "successfulRequestReuseRate" to 0.75),
            "sizes" to mapOf("classesJarBytes" to 7000), "dexOrApkMeasured" to false,
        ),
        "archives" to listOf(mapOf("file" to "module.aar", "seed" to 123, "bytes" to 2000, "optionalResourceLimit" to 4)),
    ))

    private fun assertCompleteJsonEquals(report: JunkGenerationReport, text: String) {
        val json = text.substringAfter(JSON_MARKER)
        assertTrue(json.startsWith("{\n"))
        assertEquals(Json.parseToJsonElement(report.toJson()), Json.parseToJsonElement(json))
    }

    private companion object {
        const val JSON_MARKER = "完整统计 JSON：\n"
    }
}
