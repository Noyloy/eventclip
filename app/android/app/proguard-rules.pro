# EventClip spike — keep Expo modules, Media3, FileProvider, JSON

-keep class expo.modules.** { *; }
-keep class expo.modules.eventcliprender.** { *; }
-keepclassmembers class expo.modules.eventcliprender.** { *; }

-keep class com.facebook.react.** { *; }
-keep class com.facebook.hermes.** { *; }
-keep class com.facebook.jni.** { *; }

-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.core.content.FileProvider.** { *; }

# Records / serialization used by Expo modules
-keepclassmembers class * {
  @expo.modules.kotlin.records.Field <fields>;
}
-keepclassmembers class * implements expo.modules.kotlin.records.Record { *; }

# Missing optional interfaces from unused filesystem packages (safe dontwarn)
-dontwarn expo.modules.interfaces.filesystem.**
-dontwarn expo.modules.kotlin.RuntimeContext
-dontwarn expo.modules.kotlin.types.AnyTypeProvider
-dontwarn expo.modules.kotlin.types.LazyKType

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp / Kotlin
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlin.**
