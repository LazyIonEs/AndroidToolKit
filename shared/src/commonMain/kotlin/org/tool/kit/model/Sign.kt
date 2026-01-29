package org.tool.kit.model

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:45
 */
open class Sign(
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