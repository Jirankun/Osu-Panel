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

# ── Retrofit / OkHttp ──
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, InnerClasses, EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ── AppAuth ──
-keep class net.openid.appauth.** { *; }
-dontwarn net.openid.appauth.**

# ── General ──
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keepattributes SourceFile,LineNumberTable
