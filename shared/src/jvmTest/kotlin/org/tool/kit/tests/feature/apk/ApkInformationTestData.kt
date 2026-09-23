package org.tool.kit.tests.feature.apk

import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import org.tool.kit.domain.apk.*
import org.tool.kit.feature.apk.*

internal object ApkInformationTestData {
    val target = object : DragAndDropTarget { override fun onDrop(event: DragAndDropEvent) = false }
    val libraries = listOf(
        ApkNativeLibrary("lib/arm64-v8a/libgraphics.so", "arm64-v8a", 5_000_000, 2_000_000, true, ApkAlignment.Aligned, ApkAlignment.NotApplicable),
        ApkNativeLibrary("lib/arm64-v8a/libbroken.so", "arm64-v8a", 128, 128, false, ApkAlignment.Unknown, ApkAlignment.Unaligned),
        ApkNativeLibrary("lib/x86_64/libgraphics.so", "x86_64", 6_000_000, 6_000_000, false, ApkAlignment.Unaligned, ApkAlignment.Aligned),
    )
    val sample = ApkInformationResultUi(label = "示例应用", packageName = "com.example.app", size = 12_000_384,
        versionName = "2.4.0", versionCode = "20400", minSdkVersion = "23", targetSdkVersion = "35", compileSdkVersion = "35",
        nativeCode = "arm64-v8a, x86_64", launchableActivity = "com.example.app.MainActivity", md5 = "0123456789abcdef0123456789abcdef",
        usesPermissionList = listOf("INTERNET", "ACCESS_NETWORK_STATE", "CAMERA", "POST_NOTIFICATIONS",
            "READ_MEDIA_IMAGES", "VIBRATE", "RECEIVE_BOOT_COMPLETED", "WAKE_LOCK").map { "android.permission.$it" },
        components = listOf(
            ApkComponent("com.example.app.MainActivity", ApkComponentType.Activity, ApkExportedDeclaration.Enabled, "com.example.app"),
            ApkComponent("com.example.app.Shortcut", ApkComponentType.ActivityAlias, ApkExportedDeclaration.Disabled, "com.example.app", "com.example.app.MainActivity"),
            ApkComponent("com.example.app.SyncService", ApkComponentType.Service, ApkExportedDeclaration.Unspecified, "com.example.app:sync"),
            ApkComponent("com.example.app.BootReceiver", ApkComponentType.Receiver, ApkExportedDeclaration.Disabled, "com.example.app"),
            ApkComponent("com.example.app.FileProvider", ApkComponentType.Provider, ApkExportedDeclaration.Disabled, "com.example.app"),
        ),
        archive = ApkArchiveInformation(
            libraries.map { ApkArchiveFile(it.path, it.size, it.compressedSize, ApkFileCategory.Native) } + listOf(
                ApkArchiveFile("classes.dex", 6_000_000, 2_000_000, ApkFileCategory.Dex),
                ApkArchiveFile("resources.arsc", 1_000_000, 1_000_000, ApkFileCategory.Resources),
                ApkArchiveFile("assets/models/model.bin", 3_000_000, 900_000, ApkFileCategory.Assets),
                ApkArchiveFile("AndroidManifest.xml", 30_000, 10_000, ApkFileCategory.Metadata),
                ApkArchiveFile("kotlin/version.txt", 128, 128, ApkFileCategory.Other)), libraries, 90_128)
    )

}
