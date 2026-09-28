import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import java.util.Properties

// أداة تحويل SVG إلى VectorDrawable الرسمية من أندرويد (نفس أداة Vector Asset في
// Android Studio). تُستخدم في مهمة generateLauncherIcons أدناه وقت البناء.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:sdk-common:31.7.3")
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { localProps.load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY") ?: ""

// توقيع نسخة Release: من متغيرات البيئة (GitHub Secrets في CI) أو local.properties.
// بدون مفتاح تُوقَّع بمفتاح الـ debug حتى يبقى assembleRelease قابلاً للتثبيت للتجربة.
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingValue("RELEASE_KEYSTORE_PATH")
val hasReleaseKeystore = releaseKeystorePath != null && rootProject.file(releaseKeystorePath).exists()

android {
    namespace = "com.marketmaps.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marketmaps.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        // مفتاح خرائط جوجل من local.properties (يُحقن في CI من Secret)
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey}\"")
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // بدون minify لتسريع البناء أثناء الاختبار
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("لا يوجد مفتاح توقيع Release — سيتم التوقيع بمفتاح debug (للتجربة فقط)")
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // التقرير يُرفع من CI؛ لا نُفشل البناء على تحذيرات قديمة موجودة مسبقاً
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/armeabi/**",
                "**/armeabi-v7a/**",
                "**/x86/**",
                "**/x86_64/**",
                "**/mips/**"
            )
        }
        resources {
            excludes += setOf(
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1"
            )
        }
    }
}

// kotlinOptions { jvmTarget } قديم في Kotlin 2.x
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// ===== أيقونات المشغّل من ملفات SVG =====
// أي ملف SVG يوضع في app/src/main/assets/launcher يُحوَّل وقت البناء إلى
// VectorDrawable، وتُولَّد منه أيقونة تكيفية لكل لون خلفية في القائمة داخل المهمة.
// إضافة شعار جديد أو لون جديد = ملف/سطر واحد، بدون أي تعديل في الكود.
// (لا يمكن لأيقونة المشغّل أن تأخذ لوناً حراً: أندرويد يقرؤها من موارد مجمّعة
//  وقت البناء، لذا الألوان قائمة جاهزة تُبنى مسبقاً لكل تركيبة.)
//
// ملاحظة: منطق التوليد مكتوب بالكامل داخل المهمة (بلا استدعاء من نطاق السكربت)
// لتبقى متوافقة مع org.gradle.configuration-cache المفعّلة في المشروع.

val launcherSvgDir = layout.projectDirectory.dir("src/main/assets/launcher")
val launcherIconsOutDir = layout.buildDirectory.dir("generated/launcherIcons").get().asFile

android {
    sourceSets.getByName("main") {
        // موارد مولّدة: VectorDrawable + أيقونات تكيفية
        res.srcDir(File(launcherIconsOutDir, "res"))
        // أصول مولّدة: فهرس الشعارات والألوان لواجهة الإعدادات لاحقاً
        assets.srcDir(File(launcherIconsOutDir, "assets"))
    }
}

val generateLauncherIcons = tasks.register("generateLauncherIcons") {
    group = "build"
    description = "يولّد أيقونات المشغّل من ملفات SVG في assets/launcher لكل لون خلفية"
    inputs.dir(launcherSvgDir).withPropertyName("launcherSvg")
    outputs.dir(launcherIconsOutDir).withPropertyName("launcherIcons")

    doLast {
        // مسارات من مدخلات/مخرجات المهمة نفسها (متوافق مع ذاكرة الإعدادات)
        val svgDir = inputs.files.singleFile
        val outRoot = outputs.files.singleFile

        // ألوان خلفية الأيقونة: key للأيقونة، تسمية عربية، وHEX
        val backgrounds = listOf(
            Triple("blue", "أزرق", "#024EE7"),
            Triple("teal", "أخضر مزرق", "#00897B"),
            Triple("green", "أخضر", "#43A047"),
            Triple("orange", "برتقالي", "#FB8C00"),
            Triple("red", "أحمر", "#E53935"),
            Triple("purple", "بنفسجي", "#8E24AA"),
            Triple("indigo", "نيلي", "#3949AB"),
            Triple("dark", "أسود", "#111111")
        )
        // منطقة الأمان للأيقونة التكيفية: يُصغَّر الشعار داخل الأيقونة
        val iconScale = 0.72f

        /** يُغلّف محتوى VectorDrawable بمجموعة تصغير حول مركز الأيقونة */
        fun withSafeZone(vectorXml: String, scale: Float): String {
            val vpW = Regex("android:viewportWidth=\"([0-9.]+)\"").find(vectorXml)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: return vectorXml
            val vpH = Regex("android:viewportHeight=\"([0-9.]+)\"").find(vectorXml)
                ?.groupValues?.get(1)?.toFloatOrNull() ?: return vectorXml
            val open = vectorXml.indexOf('>')
            val close = vectorXml.lastIndexOf("</vector>")
            if (open < 0 || close <= open) return vectorXml
            return vectorXml.substring(0, open + 1) +
                "\n<group android:pivotX=\"" + (vpW / 2f) + "\" android:pivotY=\"" + (vpH / 2f) +
                "\" android:scaleX=\"" + scale + "\" android:scaleY=\"" + scale + "\">" +
                vectorXml.substring(open + 1, close) + "</group>\n" + vectorXml.substring(close)
        }

        /** تحويل SVG إلى VectorDrawable بالمحوّل الرسمي (انعكاس: التوقيع يختلف بين إصدارات الأدوات) */
        fun convertSvg(svg: File): String {
            val className = "com.android.ide.common.vectordrawable.Svg2Vector"
            val cls = try {
                Class.forName(className)
            } catch (_: ClassNotFoundException) {
                Class.forName(className, true, Thread.currentThread().contextClassLoader)
            }
            val candidates = cls.methods.filter { it.name == "parseSvgToXml" && it.parameterCount == 2 }
            val method = candidates.firstOrNull { it.parameterTypes[0] == Path::class.java }
                ?: candidates.firstOrNull { it.parameterTypes[0] == File::class.java }
                ?: error("لم أجد دالة التحويل parseSvgToXml في Svg2Vector")
            val argument: Any = if (method.parameterTypes[0] == Path::class.java) svg.toPath() else svg
            val out = ByteArrayOutputStream()
            val errorLog = try {
                method.invoke(null, argument, out) as? String ?: ""
            } catch (e: Exception) {
                e.cause?.message ?: e.message ?: "فشل استدعاء المحوّل"
            }
            val xml = out.toString("UTF-8")
            // المحوّل يكتب الملف الناتج حتى مع وجود تنبيهات (سمات غير مدعومة مثلاً)،
            // فالفشل الحقيقي هو ألا يُكتب شيء، لا وجود تنبيهات.
            if (xml.isBlank()) error("تعذّر التحويل: ${errorLog.ifBlank { "سبب غير معروف" }}")
            if (errorLog.isNotBlank()) logger.lifecycle("MARKETMAPS_NOTE=${svg.name}: $errorLog")
            return xml
        }

        val resRoot = File(outRoot, "res")
        val assetsRoot = File(outRoot, "assets")
        resRoot.deleteRecursively()
        assetsRoot.deleteRecursively()
        val drawableDir = File(resRoot, "drawable").apply { mkdirs() }
        val mipmapDir = File(resRoot, "mipmap-anydpi-v26").apply { mkdirs() }
        assetsRoot.mkdirs()

        // ملف خلفية لكل لون
        backgrounds.forEach { (colorKey, _, hex) ->
            File(drawableDir, "launcher_bg_$colorKey.xml").writeText(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                    "android:shape=\"rectangle\">\n" +
                    "    <solid android:color=\"$hex\" />\n</shape>\n"
            )
        }

        val logoKeys = mutableListOf<String>()
        val iconNames = mutableListOf<String>()
        val svgFiles = svgDir.listFiles { f -> f.isFile && f.extension.equals("svg", true) }
            ?.sortedBy { it.name }.orEmpty()
        logger.lifecycle("MARKETMAPS_SVG_FOUND=${svgFiles.size} dir=${svgDir.absolutePath}")
        svgFiles
            .forEach { svg ->
                val key = svg.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                    .let { if (it.isEmpty() || it[0].isDigit()) "logo_$it" else it }
                val vector = try {
                    withSafeZone(convertSvg(svg), iconScale)
                } catch (e: Exception) {
                    logger.lifecycle("MARKETMAPS_SKIP=${svg.name}: ${e.message}")
                    null
                } ?: return@forEach

                File(drawableDir, "logo_${key}_foreground.xml").writeText(vector)
                logoKeys += key

                backgrounds.forEach { (colorKey, _, _) ->
                    val iconName = "ic_launcher_logo_${key}_$colorKey"
                    File(mipmapDir, "$iconName.xml").writeText(
                        "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                            "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                            "    <background android:drawable=\"@drawable/launcher_bg_$colorKey\" />\n" +
                            "    <foreground android:drawable=\"@drawable/logo_${key}_foreground\" />\n" +
                            "    <monochrome android:drawable=\"@drawable/logo_${key}_foreground\" />\n" +
                            "</adaptive-icon>\n"
                    )
                    iconNames += iconName
                }
            }

        // فهرس يقرأه التطبيق لاحقاً في الإعدادات (الشعارات + الألوان المتاحة)
        val json = buildString {
            append("{\n  \"logos\": [")
            append(logoKeys.joinToString(", ") { "{\"key\": \"$it\"}" })
            append("],\n  \"backgrounds\": [")
            append(backgrounds.joinToString(", ") { (k, label, hex) ->
                "{\"key\": \"$k\", \"label\": \"$label\", \"hex\": \"$hex\"}"
            })
            append("],\n  \"icons\": [")
            append(iconNames.joinToString(", ") { "\"$it\"" })
            append("]\n}\n")
        }
        File(assetsRoot, "launcher_icons.json").writeText(json)

        logger.lifecycle("MARKETMAPS_LOGOS=${logoKeys.joinToString(",")}")
        logger.lifecycle(
            "أيقونات الشعارات: ${logoKeys.size} شعاراً × ${backgrounds.size} لوناً = " +
                "${iconNames.size} أيقونة مكتملة"
        )
    }
}

// التوليد قبل دمج الموارد والأصول في كل بناء
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(generateLauncherIcons) }
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Resources") }
    .configureEach { dependsOn(generateLauncherIcons) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // أيقونات أساسية فقط (أصغر من extended)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.bundles.mapsforge)
    implementation(libs.androidsvg)

    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    implementation(platform(libs.firebase.bom))
    // وحدات -ktx أُزيلت من Firebase BoM 34؛ نفس الـ API موجود في الوحدة الرئيسية
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.storage)
    implementation(libs.coil.compose)

    implementation(libs.okhttp)

    // تحميل الخرائط في الخلفية
    implementation(libs.androidx.work.runtime.ktx)

    // يطبّق Baseline Profiles المضمّنة في مكتبات Compose عند التثبيت خارج المتجر
    // (APK من GitHub) => فتح وتمرير أسرع بدون انتظار تجميع ART لاحقاً
    implementation(libs.androidx.profileinstaller)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
