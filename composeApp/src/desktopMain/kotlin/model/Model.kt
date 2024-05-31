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
    var md5: String,
    var sha1: String,
    var sha256: String
)

/**
 * Apk签名，存储页面信息，viewModel中
 */
data class ApkSignature(
    var apkPath: String = "", // apk路径
    var outputPath: String = "", // apk输出路径
    var keyStorePolicy: SignaturePolicy = SignaturePolicy.V2, // 密钥策略
    var keyStorePath: String = "", // 密钥路径
    var keyStorePassword: String = "", // 密钥密码
    var keyStoreAlisaList: ArrayList<String>? = null,  // 别名列表
    var keyStoreAlisaIndex: Int = 0, // 别名选中下标
    var keyStoreAlisaPassword: String = "" // 别名密码
) {
    constructor(apkSignature: ApkSignature) : this(
        apkSignature.apkPath,
        apkSignature.outputPath,
        apkSignature.keyStorePolicy,
        apkSignature.keyStorePath,
        apkSignature.keyStorePassword,
        apkSignature.keyStoreAlisaList,
        apkSignature.keyStoreAlisaIndex,
        apkSignature.keyStoreAlisaPassword
    )
}

/**
 * Apk签名更新索引
 */
enum class SignatureEnum {
    APK_PATH, OUTPUT_PATH, KEY_STORE_POLICY, KEY_STORE_PATH, KEY_STORE_PASSWORD, KEY_STORE_ALISA_LIST, KEY_STORE_ALISA_INDEX, KEY_STORE_ALISA_PASSWORD,
}

/**
 * APK签名策略
 */
enum class SignaturePolicy(val title: String, val value: String) {
    V1("V1", "APK Signature Scheme V1"), V2("V2", "APK Signature Scheme V2，包含V1"), V2Only(
        "V2 Only",
        "APK Signature Scheme V2，不包含V1"
    ),
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
    KEY_STORE_PATH, KEY_STORE_NAME, KEY_STORE_PASSWORD, KEY_STORE_CONFIRM_PASSWORD, KEY_STORE_ALISA, KEY_STORE_ALISA_PASSWORD, KEY_STORE_ALISA_CONFIRM_PASSWORD, VALIDITY_PERIOD, AUTHOR_NAME, ORGANIZATIONAL_UNIT, ORGANIZATIONAL, CITY, PROVINCE, COUNTRY_CODE
}

/**
 * 垃圾代码生成信息，存储页面信息，viewModel中
 */
data class JunkCodeInfo(
    var outputPath: String = "", // 输出路径
    var aarName: String = "junk_com_dev_junk_plugin_TT2.0.0.aar", // aar名称
    var packageName: String = "com.dev.junk", // 包名
    var suffix: String = "plugin", // 后缀
    var packageCount: String = "50", // 包数量
    var activityCountPerPackage: String = "30", // 每个包里 activity 的数量
    var resPrefix: String = "junk_", // 资源前缀
) {
    constructor(junkCodeInfo: JunkCodeInfo) : this(
        junkCodeInfo.outputPath,
        junkCodeInfo.aarName,
        junkCodeInfo.packageName,
        junkCodeInfo.suffix,
        junkCodeInfo.packageCount,
        junkCodeInfo.activityCountPerPackage,
        junkCodeInfo.resPrefix
    )
}

/**
 * 垃圾代码生成更新索引
 */
enum class JunkCodeEnum {
    OUTPUT_PATH, PACKAGE_NAME, SUFFIX, PACKAGE_COUNT, ACTIVITY_COUNT_PER_PACKAGE, RES_PREFIX
}

enum class StoreType(val value: String) {
    JKS("JKS"), PKCS12("PKCS12")
}

enum class StoreSize(val value: Int) {
    SIZE_1024(1024), SIZE_2048(2048)
}

/**
 * 外观
 */
enum class Exterior(val title: String, val mode: Long) {
    AutoMode("跟随系统", 0), LightMode("浅色模式", 1), DarkMode("暗色模式", 2)
}

/**
 * 未签名APK
 */
data class UnsignedApk(val title: String, val path: String)
