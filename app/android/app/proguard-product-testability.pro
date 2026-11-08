# Retain own native public model APIs so SAME minified release can be instrumented.
# No SDK expansion. Existing diagnostic build keeps its original rules.
-keep class expo.modules.eventcliprender.realmedia.** { *; }

-keep class com.eventclip.spike.ProductSurfaceSmokeActivity { *; }
