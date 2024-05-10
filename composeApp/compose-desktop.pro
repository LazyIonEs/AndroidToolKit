-keepclasseswithmembers public class MainKt {
    public static void main(java.lang.String[]);
}

-keep class * implements kotlinx.coroutines.internal.MainDispatcherFactory
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.swing.SwingDispatcherFactory { *; }

-keep class org.tool.kit.** { *; }

-dontwarn kotlinx.coroutines.debug.*

-keep class org.bouncycastle.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class com.android.apksig.** { *; }
-keep class org.sqlite.** { *; }

-assumenosideeffects public class androidx.compose.runtime.ComposerKt {
    void sourceInformation(androidx.compose.runtime.Composer,java.lang.String);
    void sourceInformationMarkerStart(androidx.compose.runtime.Composer,int,java.lang.String);
    void sourceInformationMarkerEnd(androidx.compose.runtime.Composer);
}

# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
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

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt # core serialization annotations
-dontnote kotlinx.serialization.SerializationKt

# When kotlinx.serialization.json.JsonObjectSerializer occurs

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JSR 305 annotations are for embedding nullability information.
-dontwarn javax.annotation.**
-dontwarn org.objectweb.**
-dontwarn javax.servlet.**
-dontwarn org.xml.**
-dontwarn org.w3c.**
-dontwarn java.xml.**
-dontwarn javax.xml.**
-dontwarn java.lang.**
-dontwarn com.ibm.**
-dontwarn kotlinx.serialization.**
-dontwarn com.google.**
-dontwarn org.tukaani.**
-dontwarn org.brotli.**
-dontwarn com.github.**
-dontwarn org.apache.**
-dontwarn javax.activation.**
-dontnote javax.activation.**

-dontwarn kotlinx.datetime.**
-dontnote kotlinx.datetime.**

-dontwarn org.apache.batik.**
-dontwarn jdk.xml.**
-dontwarn org.w3c.dom.**

-dontwarn org.apache.tika.**
-dontwarn org.slf4j.**
-dontnote org.slf4j.**
-keep class org.slf4j.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.* { *; }

# A resource is loaded with a relative path so the package of this class must be preserved.
-adaptresourcefilenames okhttp3/internal/publicsuffix/PublicSuffixDatabase.gz

# Animal Sniffer compileOnly dependency to ensure APIs are compatible with older versions of Java.
-dontwarn org.codehaus.mojo.animal_sniffer.*

# OkHttp platform used only on JVM and when Conscrypt and other security providers are available.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
#################################### SLF4J #####################################
-dontwarn org.slf4j.**

# Prevent runtime crashes from use of class.java.getName()
-dontwarn javax.naming.**

# Ignore warnings and Don't obfuscate for now
-dontobfuscate
-ignorewarnings

-keep class org.ocpsoft.prettytime.i18n**

-keepattributes Signature,LineNumberTable