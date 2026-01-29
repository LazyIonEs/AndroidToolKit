-ignorewarnings
# -verbose

-dontnote java.awt.*
-dontnote java.nio.*
-dontnote java.util.*
-dontnote com.intellij.**
-dontnote org.jdom.**
-dontnote com.google.**
-dontnote org.apache.**
-dontnote com.sun.**
-dontnote javax.activation.**
-dontnote org.intellij.**
-dontnote org.jetbrains.**

-dontwarn java.awt.*
-dontwarn java.nio.*
-dontwarn java.util.*
-dontwarn com.intellij.**
-dontwarn org.jdom.**
-dontwarn com.google.**
-dontwarn org.apache.**
-dontwarn ch.qos.logback.**
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn org.jetbrains.**
-dontwarn kotlinx.coroutines.**

-keep class androidx.compose.** { *; }
-keep enum org.jetbrains.nav_cupcake.** { *; }
-keep class com.android.ddmlib.** { *; }
-keep class org.lwjgl.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.android.apksig.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
#-keep class java.nio.** { *; }
#-keep class java.util.concurrent.** { *; }
-keep class uniffi.toolkit.** { *; }
-keep class androidx.datastore.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }
-keepclassmembers class androidx.datastore.** { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory
-keep class androidx.datastore.preferences.protobuf.** { *; }
-keepclassmembers class androidx.datastore.preferences.protobuf.** { *; }
-keepattributes Signature,LineNumberTable,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,InnerClasses,EnclosingMethod

# coil3
-keep class okio.** { *; }
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

# apktool
-keep class brut.apktool.** { *; }
-keep class brut.androlib.** { *; }

# 保留 YAML 解析库
-keep class org.yaml.snakeyaml.** { *; }

# 保留枚举的 values()/valueOf()
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# 保留 Kotlin 元数据（若使用 Kotlin）
-keep class kotlin.Metadata { *; }

# 忽略第三方库警告
-dontwarn com.google.common.**
-dontwarn javax.xml.xpath.**

-keep class model.** { *; }

# ktor
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keep class io.ktor.client.engine.cio.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**
-dontnote io.ktor.**
-dontnote org.slf4j.**
-dontnote kotlinx.serialization.**