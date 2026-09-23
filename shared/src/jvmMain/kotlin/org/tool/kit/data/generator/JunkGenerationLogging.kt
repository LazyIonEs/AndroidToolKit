package org.tool.kit.data.generator

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.*

private val junkGenerationLogger = KotlinLogging.logger("JunkGeneration")
private val readableReportJson = Json { prettyPrint = true }

/** Emit each complete report as one log event, so concurrent AAR summaries cannot interleave. */
internal fun logJunkArchiveReport(file: File, report: JunkGenerationReport) {
    junkGenerationLogger.info { formatJunkArchiveReport(file, report) }
}

internal fun logJunkBatchReport(directory: File, report: JunkGenerationReport) {
    junkGenerationLogger.info { formatJunkBatchReport(directory, report) }
}

internal fun formatJunkArchiveReport(file: File, report: JunkGenerationReport): String {
    val root = Json.parseToJsonElement(report.toJson()).jsonObject
    val resources = root.section("resources")
    val budget = root.section("policy").section("resources")
    return buildString {
        appendLine("Android AAR 生成完成")
        appendLine("输出文件：${file.absolutePath}")
        appendLine("随机种子：${root.value("seed")}")
        appendLine("代码包名前缀：${root.value("codePackagePrefix")}；资源 namespace：${root.value("resourceNamespace")}")
        appendMeasuredSections(root, root.value("activities"))
        appendLine("附加资源：实际条目 ${resources.counterSum("generated").display()} / 总上限 ${budget.value("total")}")
        appendLine("分类实际条目：${resources.section("generated").counters()}")
        appendLine("分类上限：${budget.counters(listOf("drawable", "mipmap", "anim", "string", "assets"))}")
        appendLine("新增资源概率：${budget.percentage("newResourceProbability")}（每次资源请求、配额有余量时尝试新增；否则优先复用同类型资源，空池省略可选引用）")
        appendLine("资源复用率（复用次数 / 全部资源请求）：${resources.percentage("reuseRate")}；请求 ${resources.counterSum("requests").display()}，复用 ${resources.counterSum("reused").display()}")
        appendResourceDetails(resources)
        appendLine("最终 AAR：${root.section("sizes").byteCount("aarBytes")}")
        appendTiming(root)
        appendWarnings(root)
        appendMeasurementStatus(root)
        appendCompleteJson(root)
    }
}

internal fun formatJunkBatchReport(directory: File, report: JunkGenerationReport): String {
    val root = Json.parseToJsonElement(report.toJson()).jsonObject
    val aggregate = root.section("aggregate")
    val resources = aggregate.section("resources")
    val measured = aggregate.number("measuredAars")
    return buildString {
        appendLine("Android AAR 批量生成完成")
        appendLine("输出目录：${directory.absolutePath}")
        appendLine("批次随机种子：${root.value("seed")}；AAR 数量：${root.value("aarCount")}")
        appendLine("统计覆盖：已测 AAR ${measured.display()}；未统计 AAR ${aggregate.value("unmeasuredAars")}")
        appendLine("全部 AAR 压缩字节合计：${root.byteCount("archiveBytes")}")
        appendLine("整批附加资源总上限：${root.value("optionalResourceBudget")}")
        if (measured != null && measured > 0) {
            appendLine("以下代码、布局及资源汇总仅覆盖已测模块：")
            appendMeasuredSections(aggregate, null)
            val generated = resources.number("optionalGeneratedCount") ?: resources.counterSum("generated")
            appendLine("附加资源实际条目：${generated.display()}；分类实际条目：${resources.section("generated").counters()}")
            appendLine("资源复用率（累计复用次数 / 累计全部请求）：${resources.percentage("requestReuseRate")}；请求 ${resources.counterSum("requests").display()}，复用 ${resources.counterSum("reused").display()}")
            appendResourceDetails(resources)
        } else {
            appendLine("代码、布局及附加资源实际数量/复用率：未统计（没有可用的模块统计），汇总中的占位值不视为实测。")
            appendBudgetExplanation()
        }
        appendTiming(root)
        appendWarnings(root)
        appendMeasurementStatus(aggregate)
        appendCompleteJson(root)
    }
}

private fun StringBuilder.appendMeasuredSections(report: JsonObject?, activities: String?) {
    val code = report.section("code")
    val layouts = report.section("layouts")
    val sizes = report.section("sizes")
    appendLine("代码：类 ${code.value("classes")}；方法 ${code.value("methods")}；字段 ${code.value("fields")}")
    appendLine("class 原始字节：${code.byteCount("classBytes")}；classes.jar 归档字节：${sizes.byteCount("classesJarBytes")}")
    appendLine("布局：${activities?.let { "Activity $it；" }.orEmpty()}独立 layout ${layouts.value("count")}；XML 原始字节 ${layouts.byteCount("bytes")}")
}

private fun StringBuilder.appendResourceDetails(resources: JsonObject?) {
    appendLine("资源明细：附加资源文件 ${resources.value("optionalFiles")}（含 values XML）；values 条目 ${resources.value("valuesEntries")}；id ${resources.value("ids")}；assets ${resources.value("assets")}")
    appendLine("附加资源原始字节：${resources.byteCount("optionalBytes")}；assets 原始字节：${resources.byteCount("assetBytes")}")
    appendBudgetExplanation()
}

private fun StringBuilder.appendBudgetExplanation() {
    appendLine("预算口径：layout 与必要 id 不占附加资源预算，仍单独统计；每个 Activity 保留独立 layout，附加资源按类型在当前 AAR 内复用。")
}

private fun StringBuilder.appendTiming(report: JsonObject?) {
    appendLine("总耗时：${report.number("totalMs")?.let { "$it ms" } ?: "未统计"}")
    val phases = report.section("phasesMs")
    val names = mapOf("classes" to "代码生成", "layoutsAndResources" to "布局及资源", "metadata" to "清单与元数据", "classesJar" to "classes.jar 封装", "aar" to "AAR 封装")
    appendLine("阶段耗时：" + (phases?.entries?.joinToString("；") { (phase, value) ->
        "${names[phase] ?: phase}=${(value as? JsonPrimitive)?.longOrNull?.let { "$it ms" } ?: "未统计"}"
    }?.ifEmpty { "未统计" } ?: "未统计"))
}

private fun StringBuilder.appendWarnings(report: JsonObject?) {
    val warnings = report?.get("warnings") as? JsonArray
    appendLine("警告：" + when {
        warnings == null -> "未提供"
        warnings.isEmpty() -> "报告未列出警告"
        else -> warnings.joinToString("；") { warning ->
            when (val text = (warning as? JsonPrimitive)?.contentOrNull ?: warning.toString()) {
                "DEX references exceed a single-dex planning threshold; verify host multidex and Release output" -> "方法或字段数量达到单 DEX 规划阈值，请验证宿主 multidex 及 Release 产物"
                "Layouts still grow with Activities; validate AAPT2/host resource pressure" -> "layout 仍随 Activity 增长，请验证 AAPT2 及宿主资源压力"
                else -> text
            }
        }
    })
}

private fun StringBuilder.appendMeasurementStatus(report: JsonObject?) {
    appendLine("DEX/APK：" + when ((report?.get("dexOrApkMeasured") as? JsonPrimitive)?.booleanOrNull) {
        false -> "未实测；AAR/class/XML 统计不代表 DEX 或 APK 的实际增量。"
        true -> "报告标记为已实测，具体测量范围和结果以完整统计为准。"
        null -> "未提供实测状态，不能据此报告 DEX 或 APK 增量。"
    })
    appendLine("字节单位：B = 字节；MB = 1,000,000 B；MiB = 1,048,576 B。")
}

private fun StringBuilder.appendCompleteJson(root: JsonObject) {
    appendLine("完整统计 JSON：")
    append(readableReportJson.encodeToString(JsonElement.serializer(), root))
}

private fun JsonObject?.section(key: String): JsonObject? = this?.get(key) as? JsonObject
private fun JsonObject?.value(key: String): String = (this?.get(key) as? JsonPrimitive)?.contentOrNull ?: "未统计"
private fun JsonObject?.number(key: String): Long? = (this?.get(key) as? JsonPrimitive)?.longOrNull
private fun Long?.display(): String = this?.toString() ?: "未统计"

private fun JsonObject?.counterSum(key: String): Long? {
    val counters = section(key) ?: return null
    val values = counters.values.map { (it as? JsonPrimitive)?.longOrNull ?: return null }
    return values.sum()
}

private fun JsonObject?.counters(keys: List<String>? = null): String {
    if (this == null) return "未统计"
    val selected = keys ?: this.keys.sorted()
    return selected.joinToString("、") { "$it=${value(it)}" }.ifEmpty { "无条目" }
}

private fun JsonObject?.percentage(key: String): String =
    (this?.get(key) as? JsonPrimitive)?.doubleOrNull?.let { String.format(Locale.ROOT, "%.2f%%", it * 100.0) } ?: "未统计"

private fun JsonObject?.byteCount(key: String): String = number(key)?.let {
    String.format(Locale.ROOT, "%d B (%.3f MB / %.3f MiB)", it, it / 1_000_000.0, it / 1_048_576.0)
} ?: "未统计"
