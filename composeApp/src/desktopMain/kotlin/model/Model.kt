package model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/1 20:23
 * @Description : 描述
 * @Version     : 1.0
 */
/**
 * Apk签名信息结果
 */
data class VerifierResult(
    val isSuccess: Boolean, // 验证是否成功
    val isApk: Boolean, // 是否是apk验证
    val path: String, // 文件路径
    val name: String, // 文件名称
    val data: ArrayList<Verifier> // 验证信息
)

/**
 * 签名信息结果
 */
data class Verifier(
    val version: Int,
    val subject: String,
    val validFrom: String,
    val validUntil: String,
    val publicKeyType: String,
    val modulus: String,
    val signatureType: String,
    val md5: String,
    val sha1: String,
    val sha256: String
)

/**
 * Apk签名，存储页面信息，viewModel中
 */
data class ApkSignature(
    var apkPath: String = "", // apk路径
    var outPutPath: String = "", // apk输出路径
    var keyStorePolicy: SignaturePolicy = SignaturePolicy.V2, // 密钥策略
    var keyStorePath: String = "", // 密钥路径
    var keyStorePassword: String = "", // 密钥密码
    var keyStoreAlisa: String = "", // 别名
    var keyStoreAlisaPassword: String = "" // 别名密码
) {
    constructor(apkSignature: ApkSignature) : this(
        apkSignature.apkPath,
        apkSignature.outPutPath,
        apkSignature.keyStorePolicy,
        apkSignature.keyStorePath,
        apkSignature.keyStorePassword,
        apkSignature.keyStoreAlisa,
        apkSignature.keyStoreAlisaPassword
    )
}

/**
 * Apk签名更新索引
 */
enum class SignatureEnum {
    APK_PATH,
    OUT_PUT_PATH,
    KEY_STORE_POLICY,
    KEY_STORE_PATH,
    KEY_STORE_PASSWORD,
    KEY_STORE_ALISA,
    KEY_STORE_ALISA_PASSWORD,
}

/**
 * APK签名策略
 */
enum class SignaturePolicy(val title: String, val value: String) {
    V1("V1", "APK Signature Scheme V1"),
    V2("V2", "APK Signature Scheme V2，包含V1"),
    V2Only("V2 Only", "APK Signature Scheme V2，不包含V1"),
    V3("V3", "APK Signature Scheme V3，包含V1和V2")
}

/**
 * Apk信息
 */
data class ApkInformation(
    var label: String = "", // 名称
    var icon: ImageBitmap? = null, // 图标
    var size: Long = 0L, // 大小
    var md5: String = "", // 文件md5
    var packageName: String = "", // 包名
    var versionCode: String = "", // 版本号
    var versionName: String = "", // 版本
    var compileSdkVersion: String = "", // 编译版本
    var minSdkVersion: String = "", // 最小版本
    var targetSdkVersion: String = "", // 目标版本
    var usesPermissionList: ArrayList<String>? = null, // 权限列表
    var nativeCode: String = "" // 架构
)

/**
 * 签名信息，存储页面信息，viewModel中
 */
data class KeyStoreInfo(
    var keyStorePath: String = "", // 密钥路径
    var keyStoreName: String = "sign.jks", // 密钥名称
    var keyStorePassword: String = "", // 密钥密码
    var keyStoreConfirmPassword: String = "", // 密钥确认密码
    var keyStoreAlisa: String = "", // 别名
    var keyStoreAlisaPassword: String = "", // 别名密码
    var keyStoreAlisaConfirmPassword: String = "", // 别名确认密码
    var validityPeriod: String = "25", // 密码有效期
    var authorName: String = "", // 作者名称
    var organizationalUnit: String = "", // 组织单位
    var organizational: String = "", // 组织
    var city: String = "", // 城市
    var province: String = "", // 省份
    var countryCode: String = "" // 国家编码
) {
    constructor(keyStore: KeyStoreInfo) : this(
        keyStore.keyStorePath,
        keyStore.keyStoreName,
        keyStore.keyStorePassword,
        keyStore.keyStoreConfirmPassword,
        keyStore.keyStoreAlisa,
        keyStore.keyStoreAlisaPassword,
        keyStore.keyStoreAlisaConfirmPassword,
        keyStore.validityPeriod,
        keyStore.authorName,
        keyStore.organizationalUnit,
        keyStore.organizational,
        keyStore.city,
        keyStore.province,
        keyStore.countryCode
    )
}

/**
 * 签名生成更新索引
 */
enum class KeyStoreEnum {
    KEY_STORE_PATH,
    KEY_STORE_NAME,
    KEY_STORE_PASSWORD,
    KEY_STORE_CONFIRM_PASSWORD,
    KEY_STORE_ALISA,
    KEY_STORE_ALISA_PASSWORD,
    KEY_STORE_ALISA_CONFIRM_PASSWORD,
    VALIDITY_PERIOD,
    AUTHOR_NAME,
    ORGANIZATIONAL_UNIT,
    ORGANIZATIONAL,
    CITY,
    PROVINCE,
    COUNTRY_CODE
}

enum class StoreType(val value: String) {
    JKS("JKS"),
    PKCS12("PKCS12")
}

/**
 * 外观
 */
enum class Exterior(val title: String, val mode: Long) {
    AutoMode("跟随系统", 0), LightMode("浅色模式", 1), DarkMode("暗色模式", 2)
}
