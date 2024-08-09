package model

import kotlinx.serialization.Serializable

@Serializable
data class UserData(
    val defaultOutputPath: String, // 默认输出路径
    val duplicateFileRemoval: Boolean, // 重复文件删除
    val defaultSignerSuffix: String, // 默认签名后缀
    val alignFileSize: Boolean, // 文件对齐
    val destStoreType: DestStoreType, // 目标密钥类型
    val destStoreSize: DestStoreSize, // 目标密钥大小
)

enum class DarkThemeConfig(val value: String) {
    FOLLOW_SYSTEM("跟随系统"), LIGHT("亮色模式"), DARK("暗色模式"),
}

enum class DestStoreSize(val size: Int) {
    ONE_THOUSAND_TWENTY_FOUR(1024), TWO_THOUSAND_FORTY_EIGHT(2048)
}

enum class DestStoreType {
    JKS, PKCS12
}