# تقرير مراجعة الكود — Market Maps

> **النطاق:** كل ملفات Kotlin (24 ملفاً، حوالي 6350 سطراً) + ملفات Gradle و Manifest و ProGuard و GitHub Actions.
> **الطريقة:** قراءة الكود فقط. لم يُبنَ المشروع لأن البيئة لا تحتوي على Java أو Android SDK.
> **الترتيب:** 🔴 حرج · 🟠 خطأ فعلي (Bug) · 🟡 أداء أو ثبات · 🔵 تحسين أو تنظيف

---

## 🔴 أولاً: مشاكل حرجة (أمان وبيانات)

### 1. ملف `google-services.json` مرفوع للمستودع رغم وجوده في `.gitignore`
- الملف `app/google-services.json` **ضمن الملفات التي يتتبعها Git**، أي أن `.gitignore` لا يؤثر عليه (أُضيف قبل قاعدة التجاهل أو أُجبر على الإضافة).
- مفتاح Firebase في حد ذاته ليس سراً خطيراً، **لكن** بما أن التطبيق يسمح لأي شخص بالإضافة والتعديل ولا يوجد تسجيل دخول، فغالباً قواعد Firestore مفتوحة بالكامل. أي شخص يأخذ المفتاح يستطيع **حذف أو تخريب قاعدة البيانات كلها** بطلب REST بسيط.
- **الحل:**
  1. اكتب قواعد أمان Firestore. أقل شيء: منع `delete`، والتحقق من الحقول وأطوالها وأن الإحداثيات أرقام صحيحة.
  2. فعّل **Firebase App Check** (Play Integrity).
  3. في Google Cloud Console قيّد مفتاح الـ API على اسم الحزمة `com.marketmaps.app` وبصمة SHA-1.
  4. أضف تسجيل دخول مجهول (Anonymous Auth) على الأقل، واحفظ `createdBy = uid` لتربط كل تعديل بصاحبه.
  5. إن أردت إخراج الملف من المستودع: `git rm --cached app/google-services.json` ثم احقنه في CI من Secret كما تفعل مع `MAPS_API_KEY`.

### 2. لا توجد أي حماية من التخريب أو الطلبات المكررة
- `StoreRepository.addStore/updateStore` بدون مصادقة ولا حد لعدد الطلبات.
- `createdAt` يُحسب من ساعة الهاتف (`System.currentTimeMillis()`) ويمكن التلاعب به. استخدم `FieldValue.serverTimestamp()`.

---

## 🟠 ثانياً: أخطاء فعلية (Bugs)

### 3. الوضع بدون إنترنت يعمل مع خريطة مصر فقط
- `MapLayerHelper.kt:70` يستخدم دائماً `MapDownloader.egyptMapFile(context)`.
- `MapScreen.kt:296` يتحقق من `isEgyptMapDownloaded` فقط.
- **النتيجة:** لو حمّل المستخدم خريطة السعودية مثلاً، يظهر له مفتاح «وضع بدون إنترنت» في الإعدادات (لأن `hasAny = true`)، لكن الخريطة **تبقى أونلاين بدون أي رسالة**.
- الإعداد `mapFileName` / `setMapFileName` موجود في `AppPreferences` لكنه **غير مستخدم إطلاقاً**.
- **الحل:** اسمح للمستخدم باختيار الخريطة النشطة واحفظها في `mapFileName`، ومرّر الملف إلى `applyOffline`. وإن أمكن، ادمج أكثر من ملف (`MultiMapDataStore` في Mapsforge).

### 4. تسريب خيوط تحميل البلاطات (Thread leak) في `applyOnline`
- `MapLayerHelper.applyOnline` يحذف الطبقة القديمة من القائمة بواسطة `clearBaseLayers`، **لكن لا يستدعي `onPause()` ولا `onDestroy()`** على `bundle.downloadLayer` القديم قبل استبداله.
- `applyOnline` يُستدعى مرتين عند بدء التشغيل (`MapScreen.kt:377` داخل `factory` ثم `MapScreen.kt:297` داخل `LaunchedEffect`)، ومرة أخرى مع كل تبديل بين أونلاين وأوفلاين.
- **النتيجة:** خيوط `TileDownloadThread` قديمة تبقى تعمل وتستهلك الذاكرة والشبكة.
- **الحل:**
```kotlin
fun applyOnline(mapView: MapView, bundle: LayerBundle) {
    bundle.downloadLayer?.let { it.onPause(); it.onDestroy() }
    bundle.downloadLayer = null
    clearBaseLayers(mapView.layerManager.layers)
    ...
}
```

### 5. ميزة «حذف المحل» غير موجودة في الواجهة
- ملف README يذكر «تعديل وحذف المحل»، والدالة `StoreRepository.deleteStore` موجودة، **لكنها لا تُستدعى من أي مكان**، ولا يوجد زر حذف في `StoreDetailsBottomCard`.

### 6. فلتر «أخرى» يعرض نتائج خاطئة
- `SearchBar.kt:367-372` يطابق التصنيف بـ `contains`. التصنيف يُحفظ كنص واحد مثل `"محل أخرى"`.
- عند اختيار النوع **«أخرى»** تظهر كل المحلات التي تصنيفها الفرعي «أخرى»: محل أخرى، ورشة أخرى، مصنع أخرى…
- وعند اختيار **«ملابس»** كتصنيف فرعي تحت «محل»، الفلتر يعمل فقط لأن النوع يُفحص أولاً. أي تغيير بسيط في ترتيب الفحص سيكسره.
- **الحل الجذري:** احفظ التصنيف في حقول منفصلة (`type`, `sub`, `detail`) بدلاً من نص واحد، وقارن بالتساوي. وكحل سريع: استخدم `startsWith(filterType)` للنوع.

### 7. في خرائط جوجل، اختيار نفس النتيجة مرتين لا يحرّك الكاميرا
- `GoogleMapContent.kt:76` يستخدم `LaunchedEffect(cameraTarget)`، و`cameraTarget` من نوع `Triple`، والـ Triple المتساوية لا تعيد تشغيل الـ effect.
- **السيناريو:** تختار محلاً، تحرك الخريطة بعيداً، ثم تختار نفس المحل من البحث مرة أخرى، فلا يحدث شيء.
- **الحل:** أضف عداداً للطلبات:
```kotlin
data class CameraRequest(val lat: Double, val lon: Double, val zoom: Float, val id: Long = System.nanoTime())
```

### 8. صلاحية الموقع تُقرأ مرة واحدة فقط في خرائط جوجل
- `GoogleMapContent.kt:59`: `val hasLocationPermission = remember { ... }` بدون مفتاح.
- بعد أن يمنح المستخدم الصلاحية من زر «موقعي»، تبقى `isMyLocationEnabled = false` حتى يُعاد تشغيل الشاشة.
- إضافة إلى ذلك، عند وجود الصلاحية تظهر **النقطة الزرقاء الأصلية ومعها الدبوس الأحمر المخصص** في نفس الوقت، أي أن موقع المستخدم يُعرض مرتين.

### 9. اكتشاف الضغط على المحل في Mapsforge بمسافة ثابتة 80 متراً
- `MapScreen.kt:400`: `findNearestStore(..., 80.0)`.
- عند تكبير 18، مسافة 80 متراً تغطي عدة أيقونات، فيُختار محل غير المقصود. وعند تكبير 12 الأيقونة الواحدة تمثل مئات الأمتار، فيصعب إصابتها.
- عند تكبير أقل من 12 تكون الأيقونات **مخفية**، ومع ذلك الضغط على الخريطة **يفتح بطاقة محل غير مرئي**.
- **الحل:** احسب المسافة بالبكسل عبر `mapViewProjection.toPixels()` (مثلاً أقل من 24dp)، وتجاهل الضغط عندما يكون `DisplayMode.HIDDEN`.

### 10. التمييز يضيع عند تغيير التكبير
- مراقب التكبير في `MapScreen.kt:388` يستدعي `addMarkersToMap(..., null, 1f)`، فيختفي تمييز المحل المحدد بعد أي تغيير في التكبير حتى تحدث إعادة تركيب أخرى.
- استخدم `highlightedStoreIdRef` كما فعلت مع `storesRef`.

### 11. تسجيل `CrashHandler` داخل الـ Activity
- `MainActivity.kt:36` يستدعي `CrashHandler.install(this)` في كل `onCreate`. مع كل تدوير للشاشة أو تغيير للنمط يُلفّ المعالج فوق نفسه، فتتكوّن سلسلة معالجات متداخلة، ويُكتب التقرير عدة مرات.
- الأخطاء التي تقع قبل إنشاء الـ Activity لا تُلتقط.
- **الحل:** أنشئ `class MarketMapsApp : Application()` وسجّل المعالج في `onCreate` الخاص بها مرة واحدة.

### 12. `runBlocking` على الخيط الرئيسي
- `MapScreen.kt:418` داخل `onRelease` يستدعي `runBlocking { appPreferences.saveLastLocation(...) }`. هذا يحجب واجهة المستخدم أثناء الكتابة على القرص وقد يسبب ANR على الأجهزة البطيئة.
- الموقع يُحفظ أصلاً في `ON_PAUSE` وفي `onDispose`، فهذا الاستدعاء زائد. احذفه أو استخدم scope على مستوى التطبيق.

### 13. زر «حفظ» بدون حالة تحميل يسبب محلات مكررة
- في `AddStoreDialog` يمكن الضغط على «حفظ» عدة مرات قبل أن يرد Firestore، فيُنشأ **عدة نسخ من نفس المحل**.
- **الحل:** أضف `isSaving` لتعطيل الزر وإظهار مؤشر تحميل.

### 14. التحميل يُلغى بصمت عند مغادرة شاشة الإعدادات
- `OfflineMapsSettingsScreen` يشغّل `MapDownloader.download` داخل `rememberCoroutineScope()` الخاص بشاشة الإعدادات. عند الرجوع للخريطة يُلغى الـ scope فيتوقف تحميل خريطة حجمها مئات الميجابايت **بدون أي إشعار**.
- **الحل:** استخدم **WorkManager** (مع `setForeground` وإشعار يعرض التقدم). هذا يحل أيضاً مشكلة إغلاق التطبيق أثناء التحميل.
- مشاكل أخرى في `MapDownloader`:
  - الإيقاف المؤقت يستخدم `Thread.sleep(200)` مع إبقاء اتصال HTTP مفتوحاً. لو طال الإيقاف سيغلق السيرفر الاتصال ويفشل التحميل. الأفضل أن يغلق الإيقاف المؤقت الاتصال، ثم يعتمد الاستئناف على Range، وهذا مدعوم لديك أصلاً.
  - لا يوجد تعامل مع رمز الاستجابة **416** (عندما يكون الملف المؤقت مكتملاً)، فيظهر «فشل التحميل: 416».
  - لا يوجد تحقق من أن الحجم النهائي يساوي `total` قبل إعادة التسمية.
  - `@Volatile` على `AtomicBoolean` زائد، والأصح جعلهما `val`.
  - الأعلام (flags) مشتركة في `object` واحد، فتحميلان متزامنان سيتداخلان.

### 15. `allowBackup="true"` مع ملفات خرائط ضخمة
- النسخ الاحتياطي التلقائي في أندرويد يشمل `filesDir`، ومنه `maps/` بمئات الميجابايت، والحد الأقصى 25MB. عند تجاوز الحد **يفشل النسخ الاحتياطي بالكامل**، فيضيع حتى ملف الإعدادات.
- **الحل:** أضف `dataExtractionRules` / `fullBackupContent` لاستثناء `maps/` و`mapcache`، أو ضع الخرائط في `noBackupFilesDir`.

---

## 🟡 ثالثاً: الأداء والثبات

### 16. إعادة رسم كل العلامات من ملفات SVG مع كل تغيير
- `addMarkersToMap` يحذف كل العلامات ويعيد إنشاءها، و`composeBubble` في `MarkerIconHelper.kt:305,318` **يقرأ ملف SVG من الـ assets ويحلله** لكل محل في كل مرة.
- هذا يتكرر عند كل تغيير في التكبير، وكل تحديث للموقع، وكل خطوة من أنيميشن التمييز (`highlightScaleBucket`).
- مع 500 محل مثلاً يعني ذلك 1000 عملية تحليل SVG في كل إطار من الأنيميشن.
- **الحل:**
  - أضف `LruCache<String, Bitmap>` بمفتاح `"$iconName|$color|$size"`، وحلّل كل SVG مرة واحدة فقط.
  - حدّث العلامة المميزة فقط بدلاً من إعادة بناء الكل.
  - احذف العلامات دفعة واحدة مع `redraw=false`، ثم استدعِ `requestRedraw()` مرة واحدة.

### 17. العلامات في خرائط جوجل تُنشأ من جديد مع كل إعادة تركيب
- `GoogleMapContent.kt:168`: `MarkerState(position = ...)` يُنشأ مباشرة داخل التركيب. استخدم `key(store.id) { rememberMarkerState(...) }`.
- مفتاح كاش الأيقونات يتضمن `store.id` و`store.name`، فلا تُشارك الأيقونة بين المحلات من نفس التصنيف. هذا مقبول عند عرض الأسماء، لكنه يستهلك ذاكرة بدونها.

### 18. تحميل كل المحلات دفعة واحدة
- `StoreRepository.getAllStores()` يستدعي `collection.get()` بدون أي تقييد، ويتكرر ذلك مع كل تدوير للشاشة لأنه موجود في `LaunchedEffect(Unit)` داخل الـ Composable.
- مع نمو البيانات يزيد الاستهلاك والتكلفة، لأن Firestore يحاسب على كل مستند يُقرأ.
- **الحل:**
  - استعلام جغرافي عبر **geohash** (مكتبة `geofire-android-common`) لجلب المحلات القريبة من مركز الخريطة فقط.
  - أو على الأقل `addSnapshotListener` للمزامنة الحية بدلاً من `refreshStores()` اليدوي.
  - فشل التحميل الأول يُتجاهل بصمت بدون أي رسالة للمستخدم.

### 19. كتابة على القرص مع كل حركة للشريط المنزلق
- `SettingsScreen.kt:1306`: المعامل `onValueChange` للـ Slider يكتب في DataStore مع كل بكسل حركة، أي عشرات الكتابات في الثانية.
- **الحل:** احفظ القيمة في حالة محلية، واكتب في DataStore داخل `onValueChangeFinished` فقط.

### 20. الموقع لا يتحدث أثناء الحركة
- `tryGetLastLocation` يُستدعى مرة واحدة عند الفتح. مسافات «يبعد X متر» تبقى محسوبة من موقع قديم حتى يضغط المستخدم زر «موقعي».
- **الحل:** `requestLocationUpdates` بأولوية `BALANCED_POWER_ACCURACY` وفترة معقولة، تعمل فقط والشاشة في حالة RESUMED.

---

## 🔵 رابعاً: تحسينات في البنية والتجربة

### 21. البنية العامة
- `MapScreen.kt` ملف واحد بأكثر من 1000 سطر، وكل الحالة فيه `remember` بدون `rememberSaveable` وبدون **ViewModel**. النتيجة: عند تدوير الشاشة تضيع نتائج البحث والمحل المحدد، ويُعاد تحميل Firestore.
  - **الاقتراح:** `MapViewModel` مع `StateFlow<MapUiState>`، والمستودع يُحقن فيه، وتقسيم الملف إلى مكونات أصغر (FAB menu، side menu، …).
- `SettingsScreen.kt` يتكون من 1400 سطر: قسّمه إلى ملف لكل قسم.
- كلٌّ من `MainActivity` و`MapScreen` و`SettingsScreen` و`OnboardingScreen` ينشئ `AppPreferences(context)` خاصاً به. يعمل ذلك لأن DataStore مفرد، لكن الأنظف مثيل واحد يُحقن.

### 22. زر الرجوع يغلق التطبيق مباشرة
- في شاشة الخريطة لا يوجد `BackHandler` لإغلاق القائمة الجانبية أو البحث المفتوح أو بطاقة المحل أو وضع الإضافة، فالضغط على «رجوع» يخرج من التطبيق.

### 23. تعارض السحب من الحافة مع إيماءات أندرويد
- في أندرويد 10 وما بعده، السحب من حافتي الشاشة هو **إيماءة الرجوع في النظام**. شرائط السحب في `MapSideMenuOverlay` تتعارض معها، وتمنع أيضاً تحريك الخريطة من الأطراف.
- **الاقتراح:** جعل الإعداد معطلاً افتراضياً، أو استخدام `Modifier.systemGestureExclusion()` لمنطقة محدودة فقط.

### 24. البحث لا يوحّد أشكال الحروف العربية
- `MapCatalog.normalize()` يوحّد الحروف (أ/إ/آ ← ا، ة ← ه، ى ← ي)، لكن **بحث المحلات** في `filterAndSortStores` لا يستخدمه. البحث عن «مكتبه» لن يجد «مكتبة».
- **الحل:** انقل `normalize` إلى ملف أدوات مشترك واستخدمه في الموضعين، ويمكن إضافة إزالة التشكيل والتطويل (ـ).

### 25. التعديل يسمح بتصنيف نصي حر
- في وضع التعديل يُعرض حقل نصي حر للتصنيف بدلاً من القوائم المنسدلة. أي خطأ إملائي يكسر الفلاتر والألوان والأيقونات. استخدم نفس القوائم المستخدمة عند الإضافة.

### 26. ترخيص OpenStreetMap وسياسة استخدام البلاطات
- لا يظهر في التطبيق نص «© OpenStreetMap contributors»، وهو **شرط في ترخيص ODbL**.
- سياسة خوادم `tile.openstreetmap.org` تمنع التطبيقات ذات الاستخدام الكثيف، وتطلب User-Agent يعرّف التطبيق مع وسيلة تواصل (حالياً `"MarketMaps/1.0"` فقط).
- إن كبر عدد المستخدمين فانتقل إلى مزود مثل MapTiler أو Stadia أو Thunderforest، أو شغّل خادم بلاطات خاصاً بك.

### 27. وميض أبيض عند الفتح في الوضع الغامق
- `themes.xml` يرث من `android:Theme.Material.Light.NoActionBar`، و`collectAsState(initial = LIGHT)` يرسم النمط الفاتح أولاً. مستخدمو الوضع الغامق يرون **وميضاً أبيض** عند كل فتح.
- **الحل:** استخدم `Theme.Material3.DayNight.NoActionBar` مع Splash Screen API، وأخّر رسم المحتوى حتى تُقرأ الإعدادات.

### 28. تفاصيل أصغر
- `recentSearches` تُحفظ كنص واحد يُقسّم بالفاصل `"||"`، فأي بحث يحتوي `||` يكسر القائمة. استخدم `stringSetPreferencesKey` أو JSON.
- `String.format` بدون `Locale` في `formatDistance` و`AddStoreDialog` و`MapDownloader`. على بعض الأجهزة يصبح الفاصل العشري فاصلة أو تتغير الأرقام. حدد `Locale` صراحةً.
- `moveCamera` يستدعي `animateTo` ثم يغيّر التكبير فوراً، فتقفز الخريطة بدل أن تتحرك بسلاسة. استخدم `animateTo` مع `setZoomLevel(zoom, true)`.
- لون خلفية القائمة الجانبية ثابت `Color(0xFF121212)` ولا يتبع النمط الفاتح.
- تكوين `MapScreen` يستمر تحت شاشة الإعدادات، فالخريطة ترسم وهي غير ظاهرة. يمكن إيقاف الطبقات مؤقتاً عند فتح الإعدادات.
- `CancellationTokenSource()` في طلبات الموقع لا يُلغى أبداً.

### 29. كود ميت واستيرادات زائدة
| العنصر | الموقع |
|---|---|
| `deleteStore` (بدون واجهة) | `StoreRepository` |
| `getMarkerBitmap` (الدالتان)، `metersPerPixel`، معامل `latitude` في `displayModeForZoom` | `MarkerIconHelper` |
| `downloadEgyptMap`، `deleteEgyptMap` | `MapDownloader` |
| `mapFileName`، `areaLabel` (يُكتب ولا يُقرأ) | `AppPreferences` |
| `guessIconName` لا يُصل إليه أبداً (`iconNameForCategory` لا تُرجع null، فـ `?:` بلا فائدة) | `StoreDetailsDialog.kt:465` |
| دالتا haversine مكررتان (`haversine` و`haversineMeters`) | `SearchBar` / `MapScreen` |
| ملف `markers/home.svg` غير مستخدم | assets |
| عشرات الاستيرادات غير المستخدمة (`luminance`، `pointerInput`، `offset`، `slideInHorizontally`…) | `MapScreen` وغيره |
| مجلد `scripts/` بالكامل: ملفات base64 للأيقونات وسكربتات patch لمرة واحدة (165KB) | الجذر |

---

## ⚙️ خامساً: البناء و CI

1. **README لا يطابق الـ workflow:** يقول README إن «البناء يبدأ تلقائياً بعد أي رفع على main»، لكن `build.yml` يحتوي `workflow_dispatch` فقط. واسم الـ artifact الفعلي `MarketMaps-debug-arm64` وليس `MarketMaps-debug`، والملف `MarketMaps-debug-arm64.apk` وليس `app-debug.apk`.
2. **ملف `gradlew` مبسّط ومعدّل يدوياً** (47 سطراً بدلاً من السكربت الرسمي، مع `-Xmx2048m` ثابت). يُفضّل إعادة توليده بالأمر `gradle wrapper --gradle-version 8.11.1` لتجنب مشاكل المسارات والمتغيرات.
3. **قواعد ProGuard واسعة جداً:** السطور `-keep class androidx.compose.** { *; }` و`com.google.android.gms.**` و`com.google.firebase.**` تلغي معظم فائدة R8. هذه المكتبات تأتي بقواعدها الخاصة (consumer rules). يكفي إبقاء `com.marketmaps.app.data.**` وقواعد Mapsforge.
4. **لا يوجد `signingConfig` لنسخة الإصدار (release)**، ولا يوجد أمر لبنائها في CI.
5. `abiFilters += "arm64-v8a"`: لا توجد مكتبات native في الاعتماديات الحالية، فالإعداد بلا أثر حالياً. لكن لو أُضيفت مكتبة native لاحقاً فسيُستبعد تلقائياً عدد كبير من الأجهزة الرخيصة بمعمارية 32-bit، وهي منتشرة في السوق المستهدف.
6. **لا توجد اختبارات إطلاقاً.** دوال مثل `filterAndSortStores` و`haversine` و`MapCatalog.search` و`colorForCategory` و`iconNameForCategory` نقية وسهلة الاختبار بـ JUnit. ويمكن إضافة `./gradlew lint testDebugUnitTest` في CI.
7. **النصوص مكتوبة داخل الكود مباشرة** بدل `strings.xml`، مما يصعّب الترجمة لاحقاً.

---

## 📋 ترتيب مقترح للإصلاح

| الأولوية | المهمة | الجهد |
|---|---|---|
| 1 | قواعد أمان Firestore + App Check + تقييد المفتاح | متوسط |
| 2 | تسريب الخيوط في `applyOnline` (#4) | صغير |
| 3 | الوضع بدون إنترنت لكل الخرائط (#3) | متوسط |
| 4 | زر الحفظ المزدوج (#13) + `runBlocking` (#12) + CrashHandler (#11) | صغير |
| 5 | كاش أيقونات SVG (#16) | صغير |
| 6 | ضغط المحل بالبكسل (#9) + كاميرا جوجل (#7) + صلاحية الموقع (#8) | صغير |
| 7 | فلتر «أخرى» وحقول التصنيف المنفصلة (#6) | متوسط |
| 8 | استثناء الخرائط من النسخ الاحتياطي (#15) | صغير |
| 9 | التحميل عبر WorkManager (#14) | متوسط |
| 10 | ViewModel وتقسيم الملفات الكبيرة (#21) | كبير |
