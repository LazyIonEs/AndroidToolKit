package org.tool.kit.utils

import org.tool.kit.model.FileSelectorType

val String.isApk: Boolean
    get() = this.endsWith(".apk")

val String.isKey: Boolean
    get() = this.endsWith(".jks") || this.endsWith(".keystore")

val String.isImage: Boolean
    get() = this.endsWith(".png") || this.endsWith(".jpg") || this.endsWith(".jpeg")

val String.isPng: Boolean
    get() = this.endsWith(".png")

val String.isJPG: Boolean
    get() = this.endsWith(".jpg")

val String.isJPEG: Boolean
    get() = this.endsWith(".jpeg")

fun <T> Array<out T>.toFileExtensions(): List<String> {
    val list = mutableListOf<String>()
    for (type in this) {
        when (type) {
            FileSelectorType.APK -> list.add("apk")
            FileSelectorType.KEY -> {
                list.add("jks")
                list.add("keystore")
            }

            FileSelectorType.EXECUTE -> list.add("exe")
            FileSelectorType.IMAGE -> {
                list.add("png")
                list.add("jpg")
                list.add("jpeg")
                list.add("webp")
            }
        }
    }
    return list
}


expect fun Long.formatFileSize(scale: Int = 2, withUnit: Boolean = true, withInterval: Boolean = false): String
expect fun Long.formatFileUnit(): String

expect fun formatStoragePercentage(bytes: Long, totalBytes: Long): String
expect fun formatModifiedTime(epochMillis: Long, pattern: String): String
