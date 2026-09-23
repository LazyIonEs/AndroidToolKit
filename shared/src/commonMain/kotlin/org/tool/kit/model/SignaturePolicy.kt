package org.tool.kit.model

import org.jetbrains.compose.resources.StringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_signature_v1
import org.tool.kit.shared.generated.resources.apk_signature_v2
import org.tool.kit.shared.generated.resources.apk_signature_v2_only
import org.tool.kit.shared.generated.resources.apk_signature_v3
import org.tool.kit.shared.generated.resources.apk_signature_v4

/**
 * APK签名策略
 */
enum class SignaturePolicy(val title: String, val value: StringResource) {
    V1("V1", Res.string.apk_signature_v1),
    V2("V2", Res.string.apk_signature_v2),
    V2Only("V2 Only", Res.string.apk_signature_v2_only),
    V3("V3", Res.string.apk_signature_v3),
    V4("V4", Res.string.apk_signature_v4)
}