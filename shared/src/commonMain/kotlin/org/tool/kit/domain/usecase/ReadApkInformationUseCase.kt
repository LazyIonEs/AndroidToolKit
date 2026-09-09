package org.tool.kit.domain.usecase

import kotlinx.coroutines.CancellationException
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkInformationRepository

class ReadApkInformationUseCase(private val repository: ApkInformationRepository) {
    suspend operator fun invoke(path: String): Result<ApkInformationData> = try {
        val output = repository.badging(path)
        val metadata = repository.metadata(path)
        val manifest = repository.manifest(path)
        var result = ApkInformationData(size = metadata.size, md5 = metadata.md5)
        // StringUtil.convertLineSeparators + split(..., true, true): keep non-empty lines without trimming.
        for (line in output.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotEmpty() }) {
            result = when {
                line.startsWith("application-icon-640:") -> result.copy(icon = repository.icon(path, manifest,
                    (line.split("application-icon-640:").getOrNull(1) ?: "").trim().replace("'", "")))
                line.startsWith("application:") -> {
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

internal fun extractValue(line: String, attribute: String): String {
    val pattern = Regex("$attribute='([^']*)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

internal fun extractVersion(line: String, attribute: String): String {
    val pattern = Regex("$attribute:'(\\d+)'")
    val matchResult = pattern.find(line)
    return matchResult?.groups?.get(1)?.value ?: ""
}

internal fun extractChannel(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val regex =
        """A: http://schemas\.android\.com/apk/res/android:name\(0x[0-9a-fA-F]{8}\)="UMENG_CHANNEL" \(Raw: "UMENG_CHANNEL"\)\s+A: http://schemas\.android\.com/apk/res/android:value\(0x[0-9a-fA-F]{8}\)="([^"]+)"""".toRegex()
    return regex.find(text)?.groupValues?.getOrNull(1)
}

