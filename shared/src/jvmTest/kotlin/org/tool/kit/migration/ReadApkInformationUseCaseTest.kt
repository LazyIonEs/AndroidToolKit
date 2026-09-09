package org.tool.kit.migration

import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.*
import org.junit.Test
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkInformationRepository
import org.tool.kit.domain.usecase.ReadApkInformationUseCase
import org.tool.kit.data.source.*
import org.tool.kit.core.process.*
import kotlin.test.*
import java.nio.file.Files

internal val apkBadgingFixture = """
package: name='org.fixture.apk' versionCode='42' versionName='1.2 测试' compileSdkVersion='35'
sdkVersion:'23'
targetSdkVersion:'35'
uses-permission: name='android.permission.INTERNET'
uses-permission: name='android.permission.CAMERA'
application-icon-640:'res/icon.png'
application: label='测试 APK' icon='fallback.png'
native-code: 'arm64-v8a' 'armeabi-v7a'
""".trimIndent()
internal val apkManifestFixture = """
A: http://schemas.android.com/apk/res/android:name(0x01010003)="UMENG_CHANNEL" (Raw: "UMENG_CHANNEL")
A: http://schemas.android.com/apk/res/android:value(0x01010024)="测试渠道"
""".trimIndent()
internal open class FixtureApkRepository(
    var output: String = apkBadgingFixture,
    var xml: String? = apkManifestFixture,
) : ApkInformationRepository {
    val iconRequests = mutableListOf<String>()
    var images = mapOf<String, ApkIconSource>()
    override suspend fun badging(path: String) = output
    override suspend fun metadata(path: String) = ApkFileMetadata(123456, "0123456789abcdef0123456789abcdef")
    override suspend fun manifest(path: String) = xml
    override suspend fun icon(path: String, manifest: String?, iconPath: String): ApkIconSource? {
        iconRequests += iconPath
        return images[iconPath]
    }
}
class ReadApkInformationUseCaseTest {
    @Test fun fieldsChannelPermissionsAndSeparators() = runBlocking {
        val repository = FixtureApkRepository()
        val expected = ApkInformationData(label = "测试 APK", size = 123456, md5 = "0123456789abcdef0123456789abcdef",
            packageName = "org.fixture.apk", versionCode = "42", versionName = "1.2 测试", compileSdkVersion = "35",
            minSdkVersion = "23", targetSdkVersion = "35", usesPermissionList = listOf("android.permission.INTERNET", "android.permission.CAMERA"),
            nativeCode = "arm64-v8a armeabi-v7a", channel = "测试渠道")
        for (separator in listOf("\n", "\r\n", "\r")) {
            repository.output = apkBadgingFixture.replace("\n", separator)
            assertEquals(expected, ReadApkInformationUseCase(repository)("/中文 空格 ' APK.apk").getOrThrow())
        }
        // Characterize IntelliJ's legacy line utility, including whitespace-only and leading-space lines.
        val text = "\r\n \r\n\t\rpackage: name='a'\n application: label='ignored'\r"
        assertEquals(StringUtil.split(StringUtil.convertLineSeparators(text), "\n", true, true),
            text.replace("\r\n", "\n").replace('\r', '\n').split('\n').filter { it.isNotEmpty() })
    }
    @Test fun preservesSequentialIconFallbackAndXmlExclusion() = runBlocking {
        val source = ApkIconSource(byteArrayOf(1))
        val repo = FixtureApkRepository(xml = null).apply { images = mapOf("fallback.png" to source) }
        assertSame(source, ReadApkInformationUseCase(repo)("a").getOrThrow().icon)
        assertEquals(listOf("res/icon.png", "fallback.png"), repo.iconRequests)
        repo.output = "application: label='A' icon='fallback.png'\napplication-icon-640:'missing.png'"
        repo.iconRequests.clear()
        assertNull(ReadApkInformationUseCase(repo)("a").getOrThrow().icon) // Later 640 failure overwrites earlier fallback.
        assertEquals(listOf("fallback.png", "missing.png"), repo.iconRequests)
        repo.output = "application: label='A' icon='adaptive.xml'"
        repo.iconRequests.clear()
        val result = ReadApkInformationUseCase(repo)("a").getOrThrow()
        assertNull(result.icon); assertNull(result.channel); assertNull(result.usesPermissionList)
        assertTrue(repo.iconRequests.isEmpty())
        repo.output = apkBadgingFixture
        repo.images = mapOf("res/icon.png" to source)
        assertSame(source, ReadApkInformationUseCase(repo)("a").getOrThrow().icon)
        assertEquals(listOf("res/icon.png"), repo.iconRequests)
    }
    @Test fun fourFieldBlankRuleAndPermissionDuplicates() = runBlocking {
        val repo = FixtureApkRepository(output = "sdkVersion:'23'")
        assertIs<EmptyApkInformation>(ReadApkInformationUseCase(repo)("a").exceptionOrNull())
        for (line in listOf("application: label='a'", "package: name='a'", "package: versionCode='1'", "package: versionName='a'")) {
            repo.output = "$line\nuses-permission: name='x'\nuses-permission: name='x'\nuses-permission: wrong='y'"
            assertEquals(listOf("x", "x", ""), ReadApkInformationUseCase(repo)("a").getOrThrow().usesPermissionList)
        }
    }
    @Test fun sourceBytesCannotBeMutatedExternally() {
        val bytes = byteArrayOf(1, 2); val source = ApkIconSource(bytes)
        bytes[0] = 8; source.bytes()[1] = 9
        assertContentEquals(byteArrayOf(1, 2), source.bytes())
    }
    @Test fun commandArgumentsExitFallbackTimeoutAndCancellation() = runBlocking {
        val dir = Files.createTempDirectory("aapt 中文 ").toFile()
        try {
            val requests = mutableListOf<ProcessRequest>()
            var result = ProcessResult(0, "  result  ", "warning")
            val source = Aapt2DataSource(Aapt2Locator({ dir.path }, false), ProcessRunner { requests += it; result }, Dispatchers.Unconfined)
            assertEquals("  result  ", source.badging("中文 ' file.apk"))
            assertEquals(listOf("dump", "badging", "中文 ' file.apk"), requests.last().arguments)
            assertEquals(60_000, requests.last().timeoutMillis)
            assertEquals("result  ", source.manifest("x.apk"))
            assertEquals(listOf("dump", "xmltree", "x.apk", "--file", "AndroidManifest.xml"), requests.last().arguments)
            result = ProcessResult(7, "partial", "bad")
            assertFailsWith<ApkCommandFailed> { source.badging("x") }; assertNull(source.manifest("x"))
            result = ProcessResult(255, "", "", timedOut = true)
            assertFailsWith<ApkCommandFailed> { source.badging("x") }
            assertEquals("aapt2.exe", Aapt2Locator({ dir.path }, true).locate().name)
            val cancelled = Aapt2DataSource(Aapt2Locator({ dir.path }), ProcessRunner { throw CancellationException() }, Dispatchers.Unconfined)
            assertFailsWith<CancellationException> { cancelled.manifest("x") }
            val repo = object : FixtureApkRepository() { override suspend fun badging(path: String): String = throw CancellationException() }
            assertFailsWith<CancellationException> { ReadApkInformationUseCase(repo)("x") }
        } finally { dir.deleteRecursively() }
        Unit
    }
}
