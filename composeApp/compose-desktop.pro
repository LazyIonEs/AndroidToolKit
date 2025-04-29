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

-keep enum org.jetbrains.nav_cupcake.** { *; }
-keep class com.android.ddmlib.** { *; }
-keep class org.lwjgl.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class com.android.apksig.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.slf4j.** { *; }
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
-keepattributes Signature,LineNumberTable,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,InnerClasses
