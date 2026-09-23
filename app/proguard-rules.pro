# Market Maps — قواعد ProGuard / R8

# Mapsforge
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# AndroidSVG
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# Firebase / Firestore
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Kotlin / Coroutines
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Compose — إبقاء أسماء التركيب الأساسية
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# منع إزالة نماذج البيانات المستخدمة مع Firestore
-keep class com.marketmaps.app.data.** { *; }
