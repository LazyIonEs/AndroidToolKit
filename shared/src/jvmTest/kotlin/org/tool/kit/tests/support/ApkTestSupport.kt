package org.tool.kit.tests.support

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.tool.kit.domain.apk.*
import org.tool.kit.domain.repository.ApkInformationRepository

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

internal fun apkFixtureIcon(): ApkIconSource {
    val bitmap = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until 64) for (x in 0 until 64) bitmap.setRGB(x, y, if ((x / 8 + y / 8) % 2 == 0) 0xff448aff.toInt() else 0xffeeeeee.toInt())
    return ApkIconSource(ByteArrayOutputStream().also { ImageIO.write(bitmap, "png", it) }.toByteArray())
}
