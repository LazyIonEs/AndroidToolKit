package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkInformationRepository

class ReadApkInformationUseCase(private val repository: ApkInformationRepository) {
    /**
     * 合并 aapt badging、清单和文件元信息，得到与平台 UI 无关的 APK 信息。
     * 无法解析出应用名、包名和版本信息时返回空信息错误；取消继续传播。
     */
    suspend operator fun invoke(path: String): Result<ApkInformationData> = try {
        val output = repository.badging(path)
        val metadata = repository.metadata(path)
        val manifest = repository.manifest(path)
        var result = ApkInformationData(size = metadata.size, md5 = metadata.md5)
        // 统一各平台换行符并忽略空行，保留行首缩进以维持下方前缀匹配的含义。
        for (line in output.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotEmpty() }) {
            result = when {
                line.startsWith("application-icon-640:") -> result.copy(icon = repository.icon(path, manifest,
                    (line.split("application-icon-640:").getOrNull(1) ?: "").trim().replace("'", "")))
                line.startsWith("application:") -> {
                    // 已解析的高密度图标优先；普通 application 图标仅作为非 XML 格式的后备来源。
                    val iconPath = extractValue(line, "icon")
                    result.copy(label = extractValue(line, "label"), icon =
                        if (result.icon == null && !iconPath.endsWith(".xml")) repository.icon(path, manifest, iconPath) else result.icon)
                }
                line.startsWith("package:") -> result.copy(packageName = extractValue(line, "name"),
                    versionCode = extractValue(line, "versionCode"), versionName = extractValue(line, "versionName"),
                    compileSdkVersion = extractValue(line, "compileSdkVersion"))
                line.startsWith("targetSdkVersion:") -> result.copy(targetSdkVersion = extractVersion(line, "targetSdkVersion"))
                line.startsWith("sdkVersion:") -> result.copy(minSdkVersion = extractVersion(line, "sdkVersion"))
                line.startsWith("uses-permission:") -> result.copy(usesPermissionList = result.usesPermissionList.orEmpty() + extractValue(line, "name"))
                line.startsWith("native-code:") -> result.copy(nativeCode = (line.split("native-code:").getOrNull(1) ?: "").trim().replace("'", ""))
                else -> result
            }
        }
        result = result.copy(channel = extractChannel(manifest))
        if (result.isBlank()) Result.failure(EmptyApkInformation()) else Result.success(result)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}

/** 提取 badging 中单引号包围的属性值，未匹配时返回空字符串。 */
internal fun extractValue(line: String, attribute: String): String {
    val pattern = Regex("$attribute='([^']*)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

/** 读取 sdkVersion 一类冒号后的纯数字版本，缺失或格式不符时返回空字符串。 */
internal fun extractVersion(line: String, attribute: String): String {
    val pattern = Regex("$attribute:'(\\d+)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

/** 从 aapt 清单转储中提取 UMENG_CHANNEL 对应的值，未配置时返回 null。 */
internal fun extractChannel(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val regex =
        """A: http://schemas\.android\.com/apk/res/android:name\(0x[0-9a-fA-F]{8}\)="UMENG_CHANNEL" \(Raw: "UMENG_CHANNEL"\)\s+A: http://schemas\.android\.com/apk/res/android:value\(0x[0-9a-fA-F]{8}\)="([^"]+)"""".toRegex()
    return regex.find(text)?.groupValues?.getOrNull(1)
}

