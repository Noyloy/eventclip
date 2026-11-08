# Exact optional compile-time annotations referenced by cached androidx.test code, no runtime API suppression.
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
-keep class com.eventclip.spike.ProductM06InstrumentedTest { *; }
-keep class com.eventclip.spike.NativePcmEvidence { *; }
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
