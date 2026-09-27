# Market Maps — قواعد ProGuard / R8
#
# سابقاً: -keep class androidx.compose.** و com.google.firebase.** و com.google.android.gms.**
# و com.marketmaps.app.data.** بالكامل — وهذا يمنع R8 من تحسين وتصغير Compose
# (أكبر مكتبة في التطبيق)، وهي أهم ما يستفيد من R8. مكتبات AndroidX و Firebase و
# Play Services و WorkManager و DataStore و OkHttp تشحن قواعدها بنفسها
# (consumer rules)، والتطبيق لا يستخدم الانعكاس (reflection) ولا toObject() —
# كائنات Store تُبنى يدوياً في StoreRepository.

# Mapsforge: الثيمات تُحمَّل كموارد (XML) عبر أسماء الأصناف، والمكتبة لا تشحن قواعد R8
-keep class org.mapsforge.** { *; }
-dontwarn org.mapsforge.**

# AndroidSVG: بعض العناصر تُنشأ حسب اسم الوسم؛ مكتبة صغيرة فالإبقاء عليها آمن
-keep class com.caverock.androidsvg.** { *; }
-dontwarn com.caverock.androidsvg.**

# OkHttp/Okio: تحذيرات لأصناف اختيارية (Conscrypt/BouncyCastle/OpenJSSE) غير موجودة
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# إبقاء أسماء الأسطر في تقارير الأعطال (CrashHandler) مع إخفاء اسم الملف الأصلي
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
