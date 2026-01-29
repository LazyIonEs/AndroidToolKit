package org.tool.kit.model

import org.jetbrains.compose.resources.StringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_signature_v1
import org.tool.kit.shared.generated.resources.apk_signature_v2
import org.tool.kit.shared.generated.resources.apk_signature_v2_only
import org.tool.kit.shared.generated.resources.apk_signature_v3
import org.tool.kit.shared.generated.resources.apk_signature_v4
import java.io.File

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:42
 */

/**
 * Apk签名，存储页面信息，viewModel中
 */
data class ApkSignature(
    private var _apkPath: String = "", // apk路径
    var outputPath: String = "", // apk输出路径
    private var _outputPrefix: String = "", // 输出文件前缀
    override var keyStorePolicy: SignaturePolicy = SignaturePolicy.V2, // 密钥策略
    override var _keyStorePath: String = "", // 密钥
    override var keyStorePassword: String = "", // 密钥密码
    override var keyStoreAlisaList: ArrayList<String>? = null,  // 别名列表
    override var keyStoreAlisaIndex: Int = 0, // 别名选中下标
    override var keyStoreAlisaPassword: String = "", // 别名密码
    override var v4SignatureOutputFileName: String = "apk-name.apk.idsig", // V4签名输出文件名称
) : Sign() {
    var apkPath: String
        get() = _apkPath
        set(value) {
            if (_apkPath != value) {
                if (value.isNotBlank()) {
                    val apkFile = File(value)
                    if (apkFile.exists()) {
                        this.v4SignatureOutputFileName = if (_outputPrefix.isNotBlank()) {
                            _outputPrefix + "-" + apkFile.name + ".idsig"
                        } else {
                            apkFile.name + ".idsig"
                        }
                    }
                } else {
                    if (_outputPrefix.isNotBlank()) {
                        this.v4SignatureOutputFileName = "$value-apk-name.apk.idsig"
                    } else {
                        this.v4SignatureOutputFileName = "apk-name.apk.idsig"
                    }
                }
            }
            _apkPath = value
        }
    var outputPrefix: String
        get() = _outputPrefix
        set(value) {
            if (_outputPrefix != value) {
                if (_apkPath.isNotBlank()) {
                    val apkFile = File(_apkPath)
                    if (apkFile.exists()) {
                        this.v4SignatureOutputFileName = if (value.isNotBlank()) {
                            value + "-" + apkFile.name + ".idsig"
                        } else {
                            apkFile.name + ".idsig"
                        }
                    }
                } else {
                    if (value.isNotBlank()) {
                        this.v4SignatureOutputFileName = "$value-apk-name.apk.idsig"
                    } else {
                        this.v4SignatureOutputFileName = "apk-name.apk.idsig"
                    }
                }
            }
            _outputPrefix = value
        }
}

/**
 * APK签名策略
 */
enum class SignaturePolicy(val title: String, val value: StringResource) {
    V1("V1", Res.string.apk_signature_v1),
    V2("V2", Res.string.apk_signature_v2),
    V2Only("V2 Only", Res.string.apk_signature_v2_only),
    V3("V3", Res.string.apk_signature_v3),
    V4("V4", Res.string.apk_signature_v4)
}