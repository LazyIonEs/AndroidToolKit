package org.tool.kit.model

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:44
 */
/**
 * 空包生成信息，存储页面信息，viewModel中
 */
data class ApkToolInfo(
    var outputPath: String = "", // 输出路径
    var icon: String = "", // 图标
    var packageName: String = "org.apk.tool", // 包名
    var targetSdkVersion: String = "30", // 目标Sdk版本
    var minSdkVersion: String = "21", // 最小Sdk版本
    var versionCode: String = "1", // 版本号
    var versionName: String = "1.0", // 版本名称
    var appName: String = "HelloAndroid", // 应用名称
    var enableSign: Boolean = false, // 启用签名
    override var _keyStorePath: String = "", // 密钥
    override var keyStorePolicy: SignaturePolicy = SignaturePolicy.V3, // 密钥策略
    override var keyStorePassword: String = "", // 密钥密码
    override var keyStoreAlisaList: ArrayList<String>? = null,  // 别名列表
    override var keyStoreAlisaIndex: Int = 0, // 别名选中下标
    override var keyStoreAlisaPassword: String = "", // 别名密码
) : Sign()