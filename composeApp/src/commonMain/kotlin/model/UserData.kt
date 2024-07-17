package model

data class UserData(
    val darkThemeConfig: DarkThemeConfig, // 主题配置
    val defaultOutputPath: String, // 默认输出路径
    val duplicateFileRemoval: Boolean, // 重复文件删除
    val defaultSignerSuffix: String, // 默认签名后缀
    val alignFileSize: Boolean, // 文件对齐
    val destStoreType: DestStoreType, // 目标密钥类型
    val destStoreSize: DestStoreSize, // 目标密钥大小
)