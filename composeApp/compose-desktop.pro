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

# coil3
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$* Companion;
}

# Keep names for named companion object from obfuscation
# Names of a class and of a field are important in lookup of named companion in runtime
-keepnames @kotlinx.serialization.internal.NamedCompanion class *
-if @kotlinx.serialization.internal.NamedCompanion class *
-keepclassmembernames class * {
    static <1> *;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Don't print notes about potential mistakes or omissions in the configuration for kotlinx-serialization classes
# See also https://github.com/Kotlin/kotlinx.serialization/issues/1900
-dontnote kotlinx.serialization.**

# Serialization core uses `java.lang.ClassValue` for caching inside these specified classes.
# If there is no `java.lang.ClassValue` (for example, in Android), then R8/ProGuard will print a warning.
# However, since in this case they will not be used, we can disable these warnings
-dontwarn kotlinx.serialization.internal.ClassValueReferences

# disable optimisation for descriptor field because in some versions of ProGuard, optimization generates incorrect bytecode that causes a verification error
# see https://github.com/Kotlin/kotlinx.serialization/issues/2719
-keepclassmembers public class **$$serializer {
    private ** descriptor;
}