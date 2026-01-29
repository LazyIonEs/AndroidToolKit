package org.tool.kit.model

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:44
 */
/**
 * 垃圾代码生成信息，存储页面信息，viewModel中
 */
data class JunkCodeInfo(
    var outputPath: String = "", // 输出路径
    var aarName: String = "junk_com_dev_junk_plugin_TT2.1.0.aar", // aar名称
    private var _packageName: String = "com.dev.junk", // 包名
    private var _suffix: String = "plugin", // 后缀
    var packageCount: String = "50", // 包数量
    var activityCountPerPackage: String = "50", // 每个包里 activity 的数量
    var resPrefix: String = "junk_", // 资源前缀
) {
    var packageName: String
        get() = _packageName
        set(value) {
            _packageName = value
            aarName = "junk_" + packageName.replace(".", "_") + "_" + this.suffix + "_TT2.1.0.aar"
        }

    var suffix: String
        get() = _suffix
        set(value) {
            _suffix = value
            aarName = "junk_" + packageName.replace(".", "_") + "_" + this.suffix + "_TT2.1.0.aar"
        }
}