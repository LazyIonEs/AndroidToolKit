package model

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

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
    private var _keyStorePath: String = "", // 密钥
    var keyStorePassword: String = "", // 密钥密码
    var keyStoreAlisaList: ArrayList<String>? = null,  // 别名列表
    var keyStoreAlisaIndex: Int = 0, // 别名选中下标
    var keyStoreAlisaPassword: String = "" // 别名密码
) {
    var keyStorePath: String
        get() = _keyStorePath
        set(value) {
            if (_keyStorePath != value) {
                this.keyStorePassword = ""
                this.keyStoreAlisaList = null
                this.keyStoreAlisaIndex = 0
                this.keyStoreAlisaPassword = ""
            }
            _keyStorePath = value
        }
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
) {
    fun isBlank(): Boolean {
        return label.isBlank() && packageName.isBlank() && versionCode.isBlank() && versionName.isBlank()
    }
}

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
)

/**
 * 垃圾代码生成信息，存储页面信息，viewModel中
 */
data class JunkCodeInfo(
    var outputPath: String = "", // 输出路径
    var aarName: String = "junk_com_dev_junk_plugin_TT2.0.0.aar", // aar名称
    private var _packageName: String = "com.dev.junk", // 包名
    private var _suffix: String = "plugin", // 后缀
    var packageCount: String = "50", // 包数量
    var activityCountPerPackage: String = "30", // 每个包里 activity 的数量
    var resPrefix: String = "junk_", // 资源前缀
) {
    var packageName: String
        get() = _packageName
        set(value) {
            _packageName = value
            aarName = "junk_" + packageName.replace(".", "_") + "_" + this.suffix + "_TT2.0.0.aar"
        }

    var suffix: String
        get() = _suffix
        set(value) {
            _suffix = value
            aarName = "junk_" + packageName.replace(".", "_") + "_" + this.suffix + "_TT2.0.0.aar"
        }
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

/**
 * 图标工厂信息，存储页面信息，viewModel中
 */
data class IconFactoryInfo(
    var icon: File? = null, // 图标文件
    var outputPath: String = "", // 输出路径
    var fileDir: String = "res", // 顶级目录
    var iconDir: String = "mipmap", // Android目录
    var iconName: String = "ic_launcher", // icon名称
    var result: MutableList<File>? = null // 生成的结果
)

/**
 * Snackbar信息
 * @param action 需要执行的操作
 */
data class SnackbarVisualsData(
    override var message: String = "",
    override var actionLabel: String? = null,
    override var withDismissAction: Boolean = false,
    override var duration: SnackbarDuration = SnackbarDuration.Short,
    var timestamp: Long = System.currentTimeMillis(),
    var action: (() -> Unit)? = null
) : SnackbarVisuals {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SnackbarVisualsData

        if (message != other.message) return false
        if (actionLabel != other.actionLabel) return false
        if (withDismissAction != other.withDismissAction) return false
        if (duration != other.duration) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + actionLabel.hashCode()
        result = 31 * result + withDismissAction.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + action.hashCode()
        return result
    }

    fun reset(): SnackbarVisualsData {
        actionLabel = null
        withDismissAction = false
        duration = SnackbarDuration.Short
        action = null
        timestamp = System.currentTimeMillis()
        return this
    }
}