package org.tool.kit.model

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.dark_mode
import org.tool.kit.shared.generated.resources.follow_the_system
import org.tool.kit.shared.generated.resources.light_mode

@Serializable
data class UserData(
    val defaultOutputPath: String, // 默认输出路径
    val duplicateFileRemoval: Boolean, // 重复文件删除
    val defaultSignerSuffix: String, // 默认签名后缀
    val alignFileSize: Boolean, // 文件对齐
    val destStoreType: DestStoreType, // 目标密钥类型
    val destStoreSize: DestStoreSize, // 目标密钥大小
)

enum class DarkThemeConfig(val resource: StringResource) {
    FOLLOW_SYSTEM(Res.string.follow_the_system),
    LIGHT(Res.string.light_mode),
    DARK(Res.string.dark_mode)
}

enum class DestStoreSize(val size: Int) {
    ONE_THOUSAND_TWENTY_FOUR(1024), TWO_THOUSAND_FORTY_EIGHT(2048)
}

enum class DestStoreType {
    JKS, PKCS12
}