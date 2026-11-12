# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in getDefaultProguardFile("proguard-android-optimize.txt").

# Keep line numbers and source file attributes for useful stack traces in crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep BuildConfig fields
-keep class com.hzyazilim.betalink.BuildConfig { *; }

# Keep data models and repositories used with reflection/JSON serialization
-keep class com.hzyazilim.betalink.core.model.** { *; }
-keep class com.hzyazilim.betalink.core.auth.** { *; }
-keep class com.hzyazilim.betalink.core.data.** { *; }
-keep class com.hzyazilim.betalink.core.admin.** { *; }
-keep class com.hzyazilim.betalink.core.support.** { *; }

# Keep Compose annotations and elements
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}
