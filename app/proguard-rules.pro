# ProGuard / R8 optimization rules for Darkside Widget

# OkHttp rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Google Code Scanner
-keep class com.google.android.gms.code.scanner.** { *; }

# AndroidX and Material
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Data classes and JSON parsing models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
