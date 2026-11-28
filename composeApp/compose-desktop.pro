# ProGuard rules for Compose Desktop release builds.
# Note: compose-gradle-plugin automatically appends default-compose-desktop-rules.pro,
# which already covers kotlin.**, Skiko, kotlinx.coroutines volatile fields /
# MainDispatcherFactory / SwingDispatcherFactory, kotlinx.serialization serializers,
# Compose ComposerKt source info removal, and -dontnote for androidx/coroutines/serialization.

# Preserve stack trace metadata, generic signatures, annotations, and inner-class info.
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

# Preserve standard enum reflection methods.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Compose runtime/UI/Material classes (see commit 78c9351), while allowing
# unused material-icons-extended classes to be shrunk.
-keep class !androidx.compose.material.icons.**, androidx.compose.** { *; }

# Plain -keep still permits method-body optimization. ProGuard 7.10.0 produces
# invalid stack-map frames in Material3's InteractiveListItem without includecode.
-keep,includecode class androidx.compose.material3.ListItemKt { *; }

# Navigation 3 DesktopNavKeySerializer resolves concrete NavKey classes via Class.forName.
-keep class androidx.navigation3.runtime.NavKey
-keep class * implements androidx.navigation3.runtime.NavKey { *; }

# Rust UniFFI generated bindings.
-keep class uniffi.toolkit.** { *; }

# JNA native mappings, structs, and callbacks (used by UniFFI and FileKit).
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure {
    <fields>;
    <init>(...);
}
-keep class * extends com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.** {
    public *;
}

# FileKit Linux XDG Portal & DBus transport SPI.
-keep class org.freedesktop.dbus.** { *; }
-keep class io.github.vinceglb.filekit.dialogs.platform.xdg.** { *; }

# SLF4J & Logback (instantiated via ServiceLoader and logback-release.xml reflection).
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }

# Coil 3 ServiceLoader targets (META-INF/proguard rules inside JARs are not auto-loaded on Desktop).
-keep class coil3.util.DecoderServiceLoaderTarget { *; }
-keep class coil3.util.FetcherServiceLoaderTarget { *; }
-keep class coil3.util.ServiceLoaderComponentRegistry { *; }
-keep class * implements coil3.util.DecoderServiceLoaderTarget { *; }
-keep class * implements coil3.util.FetcherServiceLoaderTarget { *; }

# Ktor Apache5 HTTP client engine and atomic/volatile fields.
-keep class io.ktor.client.HttpClientEngineContainer { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-keep class io.ktor.client.engine.apache5.** { *; }
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# APK signing, verification, and Apktool internals.
-keep class org.bouncycastle.** { *; }
-keep class com.android.apksig.** { *; }
-keep class brut.androlib.** { *; }

# ---------------------------------------------------------------------------
# Targeted warning/note suppressions for optional & excluded third-party APIs
# ---------------------------------------------------------------------------

# Excluded transitive dependencies of com.jetbrains.intellij.platform:util
# (see shared/build.gradle.kts) and optional IntelliJ/Fleet references.
-dontwarn com.intellij.**
-dontwarn org.jdom.**
-dontwarn it.unimi.dsi.fastutil.**
-dontwarn com.fasterxml.aalto.**
-dontwarn net.jpountz.lz4.**
-dontwarn org.apache.log4j.**
-dontwarn org.apache.oro.**
-dontwarn gnu.trove.**
-dontwarn fleet.util.multiplatform.**
-dontwarn kotlinx.coroutines.internal.intellij.**
-dontwarn kotlinx.coroutines.debug.**

# Excluded kxml2 (see composeApp/build.gradle.kts) and optional Android tools deps.
-dontwarn org.kxml2.io.**
-dontwarn com.google.archivepatcher.**
-dontwarn com.android.tools.apk.analyzer.internal.ApkFileByFileDiffParser$ListeningDiffGenerator
-dontwarn com.google.auto.service.AutoService

# Optional Logback & Jakarta/Javax Servlet/Mail/Activation integrations.
-dontwarn jakarta.servlet.**
-dontwarn javax.servlet.**
-dontwarn jakarta.mail.**
-dontwarn ch.qos.logback.classic.servlet.**
-dontwarn ch.qos.logback.classic.selector.servlet.**
-dontwarn ch.qos.logback.classic.helpers.MDCInsertingServletFilter
-dontwarn ch.qos.logback.core.net.LoginAuthenticator
-dontwarn ch.qos.logback.core.status.ViewStatusMessagesServletBase
-dontwarn org.apache.commons.logging.impl.ServletContextCleaner
-dontwarn com.sun.activation.registries.**
-dontwarn org.apache.avalon.**
-dontwarn org.apache.log.**
-dontwarn org.codehaus.stax2.**
-dontwarn org.jline.jansi.**

# Optional compression, TLS, GraalVM SVM, and ICU4J integrations in third-party libs.
-dontwarn org.tukaani.xz.**
-dontwarn com.aayushatharva.brotli4j.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.apache.hc.client5.http.async.methods.DeflatingZstdEntityProducer*
-dontwarn org.brotli.dec.**
-dontwarn org.conscrypt.**
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn com.ibm.icu.**

# ProGuard does not resolve signature-polymorphic MethodHandle/VarHandle calls in Guava/ddmlib.
-dontwarn java.lang.invoke.MethodHandle
-dontwarn java.lang.invoke.VarHandle

# Suppress noisy harmless reflection/dynamic-class notes in third-party libraries.
-dontnote com.google.**
-dontnote com.intellij.**
-dontnote com.sun.**
-dontnote org.apache.**
-dontnote org.bouncycastle.**
-dontnote com.beust.**
-dontnote javax.xml.**
-dontnote ch.qos.logback.**
-dontnote io.ktor.**
-dontnote org.freedesktop.dbus.**
