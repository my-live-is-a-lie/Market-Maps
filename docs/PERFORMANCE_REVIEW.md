# الجولة الثانية: أخطاء إضافية + الأداء والذاكرة وجودة الكود

> هذا التقرير مكمّل لـ `docs/CODE_REVIEW.md`. كل النقاط هنا جديدة ولم تُذكر هناك.
> النقاط المتعلقة بمكتبة **Mapsforge 0.25.0** تحققت منها من الكود المصدري للمكتبة نفسها (`DisplayModel`، `AndroidUtil`، `Layers`، `Job`، `TileDownloadLayer`، `MapViewPosition`)، ولم أعتمد فيها على التخمين.

---

## 🧠 الملخص: أين تذهب الذاكرة؟

| المصدر | الاستهلاك الحالي (تقريبي) | بعد الإصلاح |
|---|---|---|
| ذاكرة البلاطات المؤقتة (tile cache) في Mapsforge | **135–158 ميجا** | **28–34 ميجا** |
| البلاطات المسرّبة عند التبديل إلى جوجل ثم العودة | **+135 ميجا مع كل تبديل** | 0 |
| أيقونات العلامات (تُعاد كلها مع كل تغيير) | ضغط مستمر على جامع القمامة (GC) | صور مخزنة مرة واحدة لكل تصنيف |
| خرائط جوجل: إعادة تركيب مع كل إطار أثناء تحريك الكاميرا | مئات العمليات في الثانية | فقط عند تغيير مستوى العرض |

> الأرقام محسوبة بصيغ Mapsforge نفسها لشاشة 1080×2340، بكثافات 2.0 و2.75 و3.0.

---

## 🟠 أولاً: أخطاء جديدة

### 1. خرائط جوجل تفتح دائماً على القاهرة وتمسح آخر موقع محفوظ
**المسار:**
1. `MapScreen` يقرأ `savedLocation` عبر `collectAsState(initial = null)`، فتكون القيمة **null دائماً في أول إطار**، ولذلك `initialLat` يساوي إحداثيات القاهرة الافتراضية.
2. `rememberCameraPositionState { ... }` داخل `GoogleMapContent` لا يُنفَّذ إلا مرة واحدة، أي أن الكاميرا تثبت على القاهرة.
3. `LaunchedEffect(cameraPositionState.isMoving)` يعمل فوراً عند البداية (القيمة `isMoving = false`) فيستدعي `onCameraIdle(القاهرة)`، ثم `saveLastLocation(القاهرة)`.

**النتيجة:** في وضع خرائط جوجل **يضيع الموقع الذي اختاره المستخدم عند أول استخدام** (مثلاً بنها أو GPS) من أول فتح، ويُستبدل بالقاهرة. بعد ذلك حتى وضع Mapsforge سيفتح على القاهرة.

**الحل:** لا تعرض الخريطة قبل قراءة الموقع المحفوظ:
```kotlin
val savedLocation by appPreferences.lastLocation.collectAsState(initial = LOADING)
if (savedLocation === LOADING) { /* مؤشر تحميل */ return }
```
أو اقرأ الموقع مرة واحدة في `MainActivity` (أنت تقرأ `onboardingDone` هناك أصلاً) ومرّره كمعامل. وتجاهل أول `onCameraIdle` قبل `onMapLoaded`.

### 2. بلاطات مختلطة بعد التبديل بين أونلاين وأوفلاين
- نفس `TileCache` مستخدم لطبقة الإنترنت (`TileDownloadLayer`) ولطبقة الأوفلاين (`TileRendererLayer`).
- في Mapsforge مفتاح البلاطة هو `zoom/x/y` فقط (`Job.getKey()`) ولا يتضمن مصدرها. لذلك بعد التبديل **تظهر بلاطات OSM القديمة داخل خريطة الأوفلاين والعكس**، إلى أن تمتلئ الذاكرة المؤقتة ببلاطات جديدة.
- **الحل:** ذاكرة مؤقتة منفصلة لكل طبقة (`"mapcache_online"` و`"mapcache_offline"`)، أو استدعاء `tileCache.purge()` عند كل تبديل.

### 3. تسريب ذاكرة ضخم عند التبديل إلى خرائط جوجل (مرتبط بالنقطة 4 في التقرير الأول)
- `MapLayerHelper.destroy()` يستدعي `downloadLayer.onPause()` فقط. في المكتبة، **`onDestroy()` هي الوحيدة التي تنهي خيوط التحميل** (`tileDownloadThread.finish()`)، وحذف الطبقة من القائمة لا ينهيها (`Layers.remove` يستدعي `unassign()` فقط).
- الخيوط المتوقفة تبقى حية للأبد وتحتفظ بمرجع إلى `TileCache` بكل البلاطات التي فيه (حوالي 135 ميجا).
- `tileCache.destroy()` لا يُستدعى أبداً.
- **النتيجة:** كل تبديل من OSM إلى جوجل ثم العودة يسرّب ذاكرة البلاطات كاملة، إلى أن يغلق النظام التطبيق.
```kotlin
fun destroy(bundle: LayerBundle) {
    bundle.downloadLayer?.onDestroy()      // بدلاً من onPause
    bundle.rendererLayer?.onDestroy()
    bundle.mapFile?.close()
    bundle.tileCache.destroy()             // جديد
    ...
}
// وفي onRelease استخدم mapView.destroyAll() بدلاً من destroy()
```

### 4. أحجام العلامات بالبكسل وليست بالـ dp
- `MarkerIconHelper.sizeForZoom()` يُرجع 88 و80 و72… **بكسل خام** بدون ضرب في كثافة الشاشة، وكذلك حجم خط التسميات `34f` في `getAndroidMarkerBitmapWithLabel`.
- على هاتف بكثافة 3x تظهر الأيقونة بحوالي 29dp (صغيرة جداً)، وعلى هاتف بكثافة 1.5x بحوالي 58dp (ضخمة). أي أن **شكل الخريطة يختلف من جهاز لآخر**.
- **الحل:** `sizePx = (sizeDp * resources.displayMetrics.density).roundToInt()`.

### 5. الأيقونات تُرسم بحجم ثم تُكبَّر إلى حجم آخر، فتصبح ضبابية
- `addMarkersToMap` يطلب الأيقونة بحجم `sizeForMode` (80 أو 56) ثم يكبّرها بـ `createScaledBitmap` إلى `sizeForZoom` (مثلاً من 80 إلى 88، ومن 56 إلى 64).
- النتيجة: **أيقونة ضبابية**، واستهلاك ذاكرة لصورتين بدل صورة واحدة.
- المصدر ملف SVG، أي رسومات متجهية يمكن رسمها بأي حجم، فارسمها مباشرة بالحجم النهائي.

### 6. تكبير Mapsforge مضروب في كثافة الشاشة مرتين
```kotlin
model.displayModel.setUserScaleFactor((density * 1.15f).coerceIn(1.0f, 2.2f))
```
- المكتبة تضبط `deviceScaleFactor = density` تلقائياً داخل `AndroidGraphicFactory`، وحجم البلاطة يُحسب هكذا: `256 × deviceScaleFactor × userScaleFactor`.
- أي أن الكثافة **تُضرب مرتين**. على هاتف كثافته 2.75 يكون حجم البلاطة **1536 بكسل بدلاً من 704**، فتستهلك كل بلاطة **9.4 ميجا بدلاً من 2 ميجا**.
- **النتائج:**
  - استهلاك ذاكرة البلاطات أكبر بحوالي 5 مرات (انظر الجدول في الملخص).
  - بلاطات OSM الأونلاين حجمها الأصلي 256px، فتُكبَّر حوالي 6 مرات وتظهر **ضبابية**.
  - ضغط بلاطات بحجم 1536px بصيغة PNG لحفظها على القرص **يستهلك المعالج والبطارية بشدة**.
- **الحل:** `setUserScaleFactor(1.0f)`، وإذا أردت تسميات أكبر في الأوفلاين فاستخدم `DisplayModel.textScale = 1.2f` بدلاً من تكبير الخريطة كلها.

### 7. بلاطات الإنترنت تُحمَّل من جديد في كل مرة يُفتح فيها التطبيق
- `AndroidUtil.createTileCache(context, id, tileSize, ratio, overdraw)` (نسخة المعاملات الخمسة) تنشئ ذاكرة مؤقتة **غير دائمة** (`persistent = false`)، فتُحذف البلاطات المحفوظة مع كل فتح للتطبيق.
- **النتيجة:** استهلاك بيانات الإنترنت وزمن التحميل يتكرران في كل جلسة.
- **الحل:** استخدم النسخة التي تقبل المعامل `persistent = true` للطبقة الأونلاين فقط. تحذّر المكتبة من استخدام الذاكرة الدائمة مع `TileRendererLayer` لأنها قد تقطع التسميات، فاترك طبقة الأوفلاين غير دائمة. خريطة الأوفلاين لا تحتاج ذلك أصلاً لأنها تُرسم محلياً.

### 8. زر «موقعي» يرسل أمري تكبير متعارضين
- `moveToCurrentLocation` يضبط التكبير على 16، ثم الـ callback يستدعي `moveCamera(lat, lon, 18f)`، فتنفَّذ حركتان متتاليتان. اجعلهما أمراً واحداً.

### 9. أخطاء صغيرة
- `StoreDetailsBottomCard`: المتغيران `screenW` و`screenH` يُحسبان ولا يُستخدمان.
- مراقب التكبير المسجّل عبر `mapViewPosition.addObserver` يُستدعى أيضاً من **خيط الأنيميشن الخاص بالمكتبة** (`MapViewPosition.Animator`) كل 15ms أثناء أي حركة. حالياً يخرج مبكراً لأن التكبير لم يتغير، لكن أي عمل ثقيل يُضاف إليه سيعمل خارج الخيط الرئيسي. الأسلم تمرير العمل عبر `mapView.post { ... }`.

---

## ⚡ ثانياً: تحسينات السرعة

### 1. وزّع نسخة Release وليس Debug (أكبر تحسين منفرد)
- حالياً المستخدمون يثبّتون `app-debug.apk`. نسخة الـ Debug **قابلة للتصحيح (debuggable)** وبدون R8، فيعطّل نظام أندرويد جزءاً من تحسينات ART ولا يُطبَّق **Baseline Profile** الخاص بـ Compose. تطبيقات Compose في نسخة الـ Debug أبطأ بشكل ملحوظ في الفتح والتمرير.
- **المطلوب:**
  1. `signingConfigs { release { ... } }` مع مفتاح توقيع يُحفظ في GitHub Secrets.
  2. أمر `assembleRelease` في CI.
  3. إضافة وحدة **Baseline Profile** (بمكتبة `androidx.baselineprofile`) لتسريع فتح التطبيق.
  4. تقليص قواعد ProGuard (انظر التقرير الأول) ليعمل R8 فعلاً.

### 2. شاشة الخريطة كاملة تُعاد مع كل إطار أنيميشن
- `MapScreen.kt:183`: `val highlightScale by highlightScaleAnim.asState()`
- `MapScreen.kt:205`: `cardLiftAnim.value`

قراءة قيم الأنيميشن مباشرة داخل جسم الـ Composable تجعل **دالة `MapScreen` كلها (أكثر من 1000 سطر) تُعاد مع كل إطار**، أي حوالي 60 مرة في الثانية أثناء حركة البطاقة أو التمييز.

**الحل:** أجّل قراءة القيمة إلى مرحلة الرسم أو التخطيط:
```kotlin
// بدلاً من padding(bottom = fabBottomPad)
Modifier.offset { IntOffset(0, -cardLiftAnim.value.roundToInt()) }
// ومرّر المقياس كدالة وليس كقيمة
highlightScale = { highlightScaleAnim.value }
```

### 3. خرائط جوجل: إعادة تركيب مع كل إطار حركة للكاميرا
- `GoogleMapContent.kt:86` و`:98` يقرآن `cameraPositionState.position` داخل التركيب، فتُعاد الدالة كلها **مع كل إطار أثناء تحريك الخريطة**، ومعها حلقة `stores.forEach { Marker(...) }`. وفي كل مرة:
  - `userIcon()` **ترسم صورة دبوس جديدة وتنشئ `BitmapDescriptor` جديداً**، لأنها بدون أي تخزين (السطر 130).
  - `MarkerState(position = ...)` يُنشأ من جديد لكل علامة، فتُحدَّث كل العلامات.
  - يُبنى نص مفتاح الذاكرة المؤقتة `"${store.id}|${mode.name}|..."` لكل محل.

**الحل:**
```kotlin
val mode by remember {
    derivedStateOf {
        MarkerIconHelper.displayModeForGoogleZoom(cameraPositionState.position.zoom)
    }
} // لا تُعاد إلا عند تغيّر الوضع فعلاً

val userIcon = remember(mode, mapReady) { /* ... */ }

stores.forEach { store ->
    key(store.id) {
        val state = rememberMarkerState(position = LatLng(store.latitude, store.longitude))
        Marker(state = state, ...)
    }
}
```
ومع عدد كبير من المحلات استخدم **Clustering** من مكتبة `maps-compose-utils`.

### 4. إضافة وحذف العلامات في Mapsforge بتعقيد O(N²)
- `Layers` في Mapsforge يستخدم `CopyOnWriteArrayList`، أي أن **كل `add` وكل `remove` ينسخ المصفوفة بالكامل ويطلب إعادة رسم**.
- `addMarkersToMap` يحذف ويضيف علامة علامة. مع 500 محل ينتج عن ذلك حوالي 1000 نسخة للمصفوفة وحوالي 1000 طلب إعادة رسم.
```kotlin
val layers = mapView.layerManager.layers
layers.removeAll(layers.filterIsInstance<Marker>(), false)
layers.addAll(newMarkers, false)
mapView.layerManager.redrawLayers()   // مرة واحدة
```
- الأفضل من ذلك ألا تعيد البناء أصلاً: احتفظ بـ `Map<storeId, Marker>` وحدّث فقط ما تغيّر (العلامة المميزة، أو تغيير صورة الأيقونات عند تغيّر مستوى العرض).
- وأنشئ العلامات للمحلات الموجودة داخل حدود الشاشة (مع هامش) فقط.

### 5. نقل رسم الأيقونات إلى خيط خلفي
- تحليل SVG ورسم الصور يحدث على **الخيط الرئيسي** داخل `LaunchedEffect`، وفي خرائط جوجل **داخل التركيب نفسه**. هذا يسبب تقطيعاً عند تجاوز التكبير 12 أو 14 أو 16 لأن كل الأيقونات تُرسم دفعة واحدة.
```kotlin
val bitmaps = withContext(Dispatchers.Default) { buildMarkerBitmaps(...) }
// ثم الإضافة للخريطة على Main
```

### 6. الدالتان `colorForCategory` و`iconNameForCategory` تنشئان كائنات في كل استدعاء
- كل استدعاء ينشئ حوالي 8 قوائم جديدة `listOf(...)` ويحلل ألوان النص بـ `Color.parseColor("#...")`، ويُستدعى ذلك لكل محل ولكل شريحة فلتر في كل إعادة رسم.
- **الحل:** ثوابت للألوان، و`HashMap<String, Int>` يحفظ نتيجة كل تصنيف (عدد التصنيفات محدود).

### 7. البحث
- احسب النص الموحّد لكل محل (`normalize(name + category + description)`) **مرة واحدة** عند تحميل المحلات، بدلاً من تحويل الحقول الثلاثة إلى أحرف صغيرة لكل محل مع كل ضغطة زر.
- أضف `debounce(150ms)` عبر `snapshotFlow { query }`.
- `SearchBar.kt:300`: `items(results)` بدون مفتاح. استخدم `items(results, key = { it.store.id })`.

### 8. الإعدادات
- `MapDownloader.isDownloaded()` (قراءة من القرص) يُستدعى **داخل التركيب** لكل دولة، ويُعاد مع كل تحديث لنسبة التحميل. احسب مجموعة الملفات المحمّلة مرة واحدة واحفظها في الحالة.
- `AppPreferences` يوفّر 20 Flow منفصلاً، و`MapScreen` وحده يراقب 10 منها. **كل كتابة في DataStore تجعلها كلها تعيد الحساب.** اجمعها في `data class AppSettings` داخل Flow واحد مع `distinctUntilChanged()`.

### 9. التحميل
- `FileOutputStream` بدون `BufferedOutputStream`. قارئ OkHttp يُرجع حوالي 8KB في كل مرة، أي أكثر من 20 ألف عملية كتابة صغيرة لملف مصر. البديل الأبسط والأسرع:
```kotlin
temp.sink(append).buffer().use { sink -> /* قراءة body.source() بكتل 64KB */ }
```

---

## 💾 ثالثاً: تقليل استهلاك الذاكرة

| # | الإجراء | الأثر المتوقع |
|---|---|---|
| 1 | `setUserScaleFactor(1f)` (النقطة 6 في أولاً) | من حوالي 140 إلى حوالي 30 ميجا |
| 2 | تقليل `screenRatio` في `createTileCache` من `2.5f` إلى `1.0–1.5f` | 30–45% أقل من ذاكرة البلاطات |
| 3 | إصلاح `destroy()` وتسريب الخيوط (النقطة 3 في أولاً) | منع تسريب حوالي 135 ميجا مع كل تبديل |
| 4 | `LruCache<String, Bitmap>` للأيقونات بمفتاح `icon|color|sizePx` | أيقونة واحدة لكل تصنيف وحجم بدلاً من واحدة لكل محل |
| 5 | الاستجابة لـ `onTrimMemory` / `ComponentCallbacks2` | تفريغ الذاكرة المؤقتة للأيقونات و`tileCache.purge()` عند الخروج للخلفية، فيقل احتمال أن يغلق النظام التطبيق |
| 6 | إيقاف طبقات الخريطة (pause) عند فتح الإعدادات | الخريطة لا ترسم ولا تحمّل بلاطات وهي غير ظاهرة |
| 7 | عدم تضمين محركي خرائط في نفس الـ APK | `play-services-maps` و`maps-compose` وMapsforge وثيماتها معاً. إن كان جوجل اختيارياً فيمكن فصله في Dynamic Feature Module، وإلا فاختر محركاً واحداً |
| 8 | كاش أيقونات جوجل بدون `store.id` عند إخفاء الأسماء | مشاركة الـ `BitmapDescriptor` بين محلات نفس التصنيف |

---

## 🧹 رابعاً: جودة الكود

### 1. 17 موضعاً يبتلع الاستثناءات بصمت، وبدون أي سطر Log
- `catch (_: Exception) {}` في 17 مكاناً، ولا يوجد أي استدعاء لـ `Log` في المشروع كله. عندما لا تظهر علامة أو تفشل خريطة **لا يوجد أي أثر يدل على السبب**.
- **الحل:** على الأقل `Log.w(TAG, "...", e)`، ويُفضّل **Firebase Crashlytics** مع `recordException(e)` بما أن Firebase موجود أصلاً. هذا يغني عن `CrashHandler` اليدوي.

### 2. «أرقام سحرية» ونصوص مكررة
- النص `"الكل"` مكرر **42 مرة** في الكود. أي تعديل عليه (ترجمة أو تغيير كلمة) سيكسر الفلاتر. استخدم `sealed class Filter { object All; data class Type(...) }` أو ثابتاً واحداً.
- المفاتيح `"custom:"` و`"LEFT"` و`"GOOGLE"` نصوص متناثرة. استخدم `enumValueOf<T>(name)` داخل `runCatching`.

### 3. قوائم الكلمات المفتاحية للتصنيفات مكررة في 3 أماكن
- `colorForCategory` و`iconNameForCategory` و`guessIconName` تعيد كتابة نفس الكلمات («ورشة»، «سمكرة»…) كل منها بطريقتها. أي تصنيف جديد يجب إضافته في 4 أماكن، ونسيان أحدها يعطي لوناً أو أيقونة خاطئة.
- **الحل:** اجعل `CategoryData` المصدر الوحيد:
```kotlin
data class Category(val id: String, val name: String, val color: Int,
                    val icon: String, val subCategories: List<SubCategory>)
```
واحفظ في Firestore `categoryId` بدلاً من النص، فتُحل أيضاً مشكلة فلتر «أخرى».

### 4. أنواع بلا أسماء
- `Triple<Double, Double, Double>` للموقع و`Triple<Double, Double, Float>` للكاميرا. عبارة مثل `loc.third` لا توضح هل القيمة تكبير أم شيء آخر. استخدم `data class CameraPos(val lat: Double, val lon: Double, val zoom: Float)`.

### 5. أدوات البناء
- `kotlinOptions { jvmTarget }` أصبح قديماً في Kotlin 2.x. البديل: `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }`.
- `firebase-firestore-ktx` **أُزيلت وحدات KTX منذ Firebase BoM 34**، فتحديث الـ BoM لاحقاً سيكسر البناء. انتقل إلى `firebase-firestore` (نفس الـ API لكن من الحزمة الرئيسية).
- انقل الإصدارات إلى `gradle/libs.versions.toml` (Version Catalog).
- أضف **ktlint** أو **detekt**، والأمر `./gradlew lint` إلى CI.
- `rememberSaveable` غير مستخدم في أي مكان، فكل الحالة تضيع عند تدوير الشاشة أو عندما يغلق النظام التطبيق في الخلفية. الحل الأشمل هو الـ ViewModel الذي اقترحته في التقرير الأول.

---

## 📋 خطة مقترحة (من الأسرع أثراً)

| # | المهمة | الجهد | الأثر |
|---|---|---|---|
| 1 | `userScaleFactor = 1f` و`screenRatio = 1.5f` | سطران | 🔥 ذاكرة ×4 أقل + خريطة أوضح |
| 2 | إصلاح `destroy()` و`applyOnline()` (إنهاء الخيوط + `tileCache.destroy`) | صغير | 🔥 إيقاف تسريب كبير |
| 3 | إصلاح فتح جوجل على القاهرة ومسح الموقع | صغير | 🔥 خطأ يلاحظه كل مستخدم |
| 4 | كاش أيقونات + `addAll/removeAll(redraw=false)` + أحجام بالـ dp | متوسط | سرعة واضحة مع كثرة المحلات |
| 5 | `derivedStateOf` في جوجل + تأجيل قراءة الأنيميشن في `MapScreen` | صغير | سلاسة أثناء الحركة |
| 6 | ذاكرة بلاطات منفصلة لكل طبقة + persistent للأونلاين | صغير | لا بلاطات مختلطة + بيانات أقل |
| 7 | نسخة Release موقّعة + Baseline Profile | متوسط | 🔥 أسرع فتح وتمرير |
| 8 | Crashlytics بدل الاستثناءات الصامتة | صغير | رؤية الأخطاء الحقيقية |
| 9 | توحيد التصنيفات في `CategoryData` + `categoryId` | متوسط | كود أنظف + فلاتر صحيحة |
