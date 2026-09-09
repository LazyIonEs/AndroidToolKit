package org.tool.kit.migration

import java.io.File
import org.tool.kit.model.SignaturePolicy

/** Frozen pre-Phase-5 setters: test oracle only. */
data class LegacySigningForm(
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
) : LegacySigningCredentials() {
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


open class LegacySigningCredentials(
    protected open var _keyStorePath: String = "", // 密钥
    open var keyStorePolicy: SignaturePolicy = SignaturePolicy.V2, // 密钥策略
    open var keyStorePassword: String = "", // 密钥密码
    open var keyStoreAlisaList: ArrayList<String>? = null,  // 别名列表
    open var keyStoreAlisaIndex: Int = 0, // 别名选中下标
    open var keyStoreAlisaPassword: String = "", // 别名密码
    open var v4SignatureOutputFileName: String = "apk-name.apk.idsig", // V4签名输出文件名称
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