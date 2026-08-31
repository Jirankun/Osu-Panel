# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class net.aokaze.osupanel.**$$serializer { *; }
-keepclassmembers class net.aokaze.osupanel.** {
    *** Companion;
}
-keepclasseswithmembers class net.aokaze.osupanel.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @SerialName annotations — R8 must not strip them (JSON field mapping)
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.json.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── OkHttp ──
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep class okhttp3.Response { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep class okio.** { *; }

# ── AppAuth ──
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ── General ──
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keepattributes SourceFile,LineNumberTable
