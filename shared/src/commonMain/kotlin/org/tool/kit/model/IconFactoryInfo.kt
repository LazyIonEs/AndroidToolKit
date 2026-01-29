package org.tool.kit.model

import java.io.File

/**
 * @author      : LazyIonEs
 * @description : 描述
 * @createDate  : 2026/1/29 09:44
 */
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