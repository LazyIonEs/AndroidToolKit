-ignorewarnings
-verbose

-keep class org.bouncycastle.** { *; }
-keep class com.android.apksig.** { *; }
-keep class org.sqlite.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes Signature,LineNumberTable,RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,InnerClasses