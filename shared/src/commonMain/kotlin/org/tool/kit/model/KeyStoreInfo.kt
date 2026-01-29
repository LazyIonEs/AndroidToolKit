package org.tool.kit.model

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:43
 */
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