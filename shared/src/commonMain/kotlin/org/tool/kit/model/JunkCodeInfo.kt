package org.tool.kit.model

import org.tool.kit.utils.JunkSizePredictor
import org.tool.kit.utils.formatFileSize

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

    // 单aar模式使用
    var aarName: String = "junk_com_dev_junk_plugin_TT2.1.0.aar", // aar名称
    private var _packageName: String = "com.dev.junk", // 包名
    private var _suffix: String = "plugin", // 后缀
    var packageCount: String = "50", // 包数量
    var activityCountPerPackage: String = "50", // 每个包里 activity 的数量
    var resPrefix: String = "junk_", // 资源前缀

    // 多aar模式使用
    var outputDir: String = "junk", // 输出文件夹
    var aarCount: String = "50", // aar数量
    var leastPackageCount: String = "5", // 包数量（最小）
    var maximumPackageCount: String = "20", // 包数量（最大）
    var leastActivityCountPerPackage: String = "10", // 每个包里 activity 的数量（最小）
    var maximumActivityCountPerPackage: String = "40", // 每个包里 activity 的数量（最大）
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

    fun estimateAarSize(junkMode: JunkMode): String {
        return when(junkMode) {
            JunkMode.SINGLE -> {
                val packageCount = packageCount.toIntOrNull() ?: 0
                val activityCountPerPackage = activityCountPerPackage.toIntOrNull() ?: 0
                JunkSizePredictor.estimateAarSize(packageCount, activityCountPerPackage).formatFileSize(scale = 1)
            }
            JunkMode.MULTI -> {
                val aarCount = aarCount.toIntOrNull() ?: 0
                val leastPackageCount = leastPackageCount.toIntOrNull() ?: 0
                val maximumPackageCount = maximumPackageCount.toIntOrNull() ?: 0
                val leastActivityCountPerPackage = leastActivityCountPerPackage.toIntOrNull() ?: 0
                val maximumActivityCountPerPackage = maximumActivityCountPerPackage.toIntOrNull() ?: 0
                var leastSize = 0L
                var maximumSize = 0L
                for (i in 0 until aarCount) {
                    leastSize += JunkSizePredictor.estimateAarSize(leastPackageCount, leastActivityCountPerPackage)
                    maximumSize += JunkSizePredictor.estimateAarSize(maximumPackageCount, maximumActivityCountPerPackage)
                }
                "${leastSize.formatFileSize(scale = 1)} ~ ${maximumSize.formatFileSize(scale = 1)}"
            }
        }
    }
}

enum class JunkMode(val title: String) {
    SINGLE("单AAR模式"),
    MULTI("多AAR模式")
}