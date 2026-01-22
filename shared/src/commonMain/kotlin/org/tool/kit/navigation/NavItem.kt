package org.tool.kit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DesignServices
import androidx.compose.material.icons.rounded.FolderDelete
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.tool.kit.feature.apk.ApkInformationNavKey
import org.tool.kit.feature.apk.ApkToolNavKey
import org.tool.kit.feature.cleaner.CleanerNavKey
import org.tool.kit.feature.iconfactory.IconFactoryNavKey
import org.tool.kit.feature.junk.JunkCodeNavKey
import org.tool.kit.feature.setting.SettingNavKey
import org.tool.kit.feature.signature.ApkSignatureNavKey
import org.tool.kit.feature.signature.SignatureGenerationNavKey
import org.tool.kit.feature.signature.SignatureInformationNavKey
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.apk_information_rail
import org.tool.kit.shared.generated.resources.apk_information_tooltip
import org.tool.kit.shared.generated.resources.apk_signature_rail
import org.tool.kit.shared.generated.resources.apk_signature_tooltip
import org.tool.kit.shared.generated.resources.apk_tool_rail
import org.tool.kit.shared.generated.resources.apk_tool_tooltip
import org.tool.kit.shared.generated.resources.cache_cleanup_rail
import org.tool.kit.shared.generated.resources.cache_cleanup_tooltip
import org.tool.kit.shared.generated.resources.garbage_code_rail
import org.tool.kit.shared.generated.resources.garbage_code_tooltip
import org.tool.kit.shared.generated.resources.icon_generation_rail
import org.tool.kit.shared.generated.resources.icon_generation_tooltip
import org.tool.kit.shared.generated.resources.setting_rail
import org.tool.kit.shared.generated.resources.setting_tooltip
import org.tool.kit.shared.generated.resources.signature_generation_rail
import org.tool.kit.shared.generated.resources.signature_generation_tooltip
import org.tool.kit.shared.generated.resources.signature_information_rail
import org.tool.kit.shared.generated.resources.signature_information_tooltip

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 16:35
 */
data class NavItem(
    val title: StringResource,
    val tooltip: StringResource,
    val selectedIcon: ImageVector,
    val unSelectedIcon: ImageVector
)

val SIGNATURE_INFORMATION = NavItem(
    Res.string.signature_information_rail,
    Res.string.signature_information_tooltip,
    Icons.AutoMirrored.Rounded.Article,
    Icons.AutoMirrored.Outlined.Article
)

val APK_INFORMATION = NavItem(
    Res.string.apk_information_rail,
    Res.string.apk_information_tooltip,
    Icons.Rounded.Assessment,
    Icons.Outlined.Assessment
)

val APK_SIGNATURE = NavItem(
    Res.string.apk_signature_rail,
    Res.string.apk_signature_tooltip,
    Icons.Rounded.VpnKey,
    Icons.Outlined.VpnKey
)

val SIGNATURE_GENERATION = NavItem(
    Res.string.signature_generation_rail,
    Res.string.signature_generation_tooltip,
    Icons.Rounded.Verified,
    Icons.Outlined.Verified
)

val APK_TOOL = NavItem(
    Res.string.apk_tool_rail,
    Res.string.apk_tool_tooltip,
    Icons.Rounded.BrightnessAuto,
    Icons.Outlined.BrightnessAuto
)

val JUNK_CODE = NavItem(
    Res.string.garbage_code_rail,
    Res.string.garbage_code_tooltip,
    Icons.Rounded.Cookie,
    Icons.Outlined.Cookie
)

val ICON_FACTORY = NavItem(
    Res.string.icon_generation_rail,
    Res.string.icon_generation_tooltip,
    Icons.Rounded.DesignServices,
    Icons.Outlined.DesignServices
)

val CLEANER = NavItem(
    Res.string.cache_cleanup_rail,
    Res.string.cache_cleanup_tooltip,
    Icons.Rounded.FolderDelete,
    Icons.Outlined.FolderDelete
)

val SET_UP = NavItem(
    Res.string.setting_rail,
    Res.string.setting_tooltip,
    Icons.Rounded.Settings,
    Icons.Outlined.Settings
)

val TOP_LEVEL_NAV_ITEMS = mapOf(
    SignatureInformationNavKey to SIGNATURE_INFORMATION,
    ApkInformationNavKey to APK_INFORMATION,
    ApkSignatureNavKey to APK_SIGNATURE,
    SignatureGenerationNavKey to SIGNATURE_GENERATION,
    ApkToolNavKey to APK_TOOL,
    JunkCodeNavKey to JUNK_CODE,
    IconFactoryNavKey to ICON_FACTORY,
    CleanerNavKey to CLEANER,
    SettingNavKey to SET_UP
)