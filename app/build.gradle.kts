import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileFilter
import java.nio.file.Path
import java.util.Locale
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
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

/** ترتيب الشعارات كما تظهر في الإعدادات (الشعار 1، الشعار 2، ...) — أي ملف جديد يُضاف في الآخر */
val launcherLogoSvgOrder = listOf("market", "google-maps", "location", "location_pin")
val launcherIconsOutDir = layout.buildDirectory.dir("generated/launcherIcons").get().asFile

/**
 * يولّد ملف بيان يضم أيقونة بديلة (activity-alias) لكل تركيبة شعار × لون،
 * ويُربط بـ AGP عبر addGeneratedManifestFile (أعلى أولوية في دمج البيان).
 * الطريقة الوحيدة لتبديل أيقونة الشاشة الرئيسية: مكوّنات ثابتة في البيان
 * يفعّل التطبيق واحداً منها ويُعطّل الباقي (قيود نظام أندرويد).
 */
abstract class GenerateLauncherAliasManifestTask : DefaultTask() {

    @get:InputDirectory
    abstract val svgDir: DirectoryProperty

    /** مفاتيح الألوان الجاهزة بالترتيب (الأول هو الافتراضي المفعّل عند التثبيت) */
    @get:Input
    abstract val backgroundKeys: ListProperty<String>

    /** ترتيب ملفات الشعارات (نفس ترتيب واجهة الإعدادات، ليبقى الافتراضي ثابتاً) */
    @get:Input
    abstract val logoSvgOrder: ListProperty<String>

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun generate() {
        // أنواع صريحة بالكامل: داخل مهام Gradle لا يُستنتج نوع lambda هنا
        val svgFolder: File = svgDir.get().asFile
        val svgFilter = FileFilter { file: File -> file.isFile && file.extension.equals("svg", true) }
        val order = logoSvgOrder.get()
        val svgFiles: List<File> =
            (svgFolder.listFiles(svgFilter)?.toList() ?: emptyList()).sortedWith(
                compareBy(
                    { file: File -> order.indexOf(file.nameWithoutExtension).let { i: Int -> if (i < 0) order.size else i } },
                    { file: File -> file.name }
                )
            )
        val colors = backgroundKeys.get()
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        builder.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n")
        builder.append("    <application>\n")
        svgFiles.forEachIndexed { index, svg ->
            val logoKey = svg.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                .let { normalized: String ->
                    if (normalized.isEmpty() || normalized[0].isDigit()) "logo_$normalized" else normalized
                }
            colors.forEachIndexed { colorIndex, colorKey ->
                val iconName = "ic_launcher_logo_${logoKey}_$colorKey"
                // أول تركيبة فقط مفعّلة افتراضياً حتى لا يظهر أكثر من أيقونة
                val enabled = index == 0 && colorIndex == 0
                builder.append("        <activity-alias\n")
                builder.append("            android:name=\"com.marketmaps.app.LauncherIcon_${logoKey}_$colorKey\"\n")
                builder.append("            android:targetActivity=\".MainActivity\"\n")
                builder.append("            android:enabled=\"$enabled\"\n")
                builder.append("            android:exported=\"true\"\n")
                builder.append("            android:icon=\"@mipmap/$iconName\"\n")
                builder.append("            android:roundIcon=\"@mipmap/$iconName\"\n")
                builder.append("            android:label=\"@string/app_name\">\n")
                builder.append("            <intent-filter>\n")
                builder.append("                <action android:name=\"android.intent.action.MAIN\" />\n")
                builder.append("                <category android:name=\"android.intent.category.LAUNCHER\" />\n")
                builder.append("            </intent-filter>\n")
                builder.append("        </activity-alias>\n")
            }
        }
        builder.append("    </application>\n")
        builder.append("</manifest>\n")

        val out = manifest.get().asFile
        out.parentFile.mkdirs()
        out.writeText(builder.toString())
        logger.lifecycle(
            "MARKETMAPS_ALIASES=${svgFiles.size * colors.size} أيقونة بديلة " +
                "(${svgFiles.size} شعاراً × ${colors.size} لوناً)"
        )
    }
}

val launcherAliasManifest = tasks.register<GenerateLauncherAliasManifestTask>("generateLauncherAliasManifest") {
    group = "build"
    description = "يولّد بيان أيقونات المشغّل البديلة لكل شعار/لون"
    svgDir.set(launcherSvgDir)
    logoSvgOrder.set(launcherLogoSvgOrder)
    backgroundKeys.set(listOf("blue", "teal", "green", "orange", "red", "purple", "indigo", "dark"))
    manifest.set(layout.buildDirectory.file("generated/launcherAliasManifest/AndroidManifest.xml"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.manifests.addGeneratedManifestFile(
            launcherAliasManifest,
            GenerateLauncherAliasManifestTask::manifest
        )
    }
}

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
    // المساران كخصائص نصية: inputs.files يعيد محتوى المجلد لا المجلد نفسه،
    // وهذه الطريقة تبقى صحيحة مع ذاكرة الإعدادات.
    inputs.property("svgDirPath", launcherSvgDir.asFile.absolutePath)
    inputs.property("logoSvgOrder", launcherLogoSvgOrder)
    inputs.property("outDirPath", launcherIconsOutDir.absolutePath)

    doLast {
        val svgDir = File(inputs.properties.getValue("svgDirPath") as String)
        val outRoot = File(inputs.properties.getValue("outDirPath") as String)
        val svgFilter = FileFilter { file: File -> file.isFile && file.extension.equals("svg", true) }

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
        // نسبة أكبر بُعد للرسم من عرض اللوحة: 38% (≈41dp من 108dp) — مطابقة
        // لحجم أيقونات التطبيقات المعتاد في المشغّل (المنطقة المرئية 72dp من 108dp)
        val logoSvgFraction = 0.38f

        /** تنسيق رقم لملف SVG/VectorDrawable (فاصلة عشرية نقطية دائماً) */
        fun formatSvgNumber(value: Float): String {
            if (value == value.toInt().toFloat()) return value.toInt().toString()
            return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }

        /**
         * تطبيع هندسة الشعار: ملفات SVG المتتبَّعة (VTracer) تأتي بلا viewBox
         * وإحداثياتها مُزاحة بـ transform=translate، فتُرسم أكبر من مساحتها وتنزاح
         * للزاوية ويختلف حجمها بين شعار وآخر. هنا:
         * 1) تُخبز الإزاحة داخل مسار الرسم (تُحذف الحاجة إلى transform).
         * 2) تُكتب لوحة مربّعة حول الرسم نفسه بحشو ثابت (الرسم = 58% من اللوحة)
         *    فتظهر كل الشعارات بنفس الحجم — مطابقة لشعار التطبيق الأصلي.
         * التطبيع متكرّر الأثر (idempotent): تشغيله على ملف مُطبَّع لا يغيّره.
         */
        fun normalizeSvgText(svgText: String): String {
            val numberRegex = Regex("-?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
            val allowedCommands = "MmLlCcSsQqTtZz"
            var result = svgText
            var skipBake = false
            val xs = mutableListOf<Float>()
            val ys = mutableListOf<Float>()

            Regex("<path\\b[^>]*?/?>", RegexOption.DOT_MATCHES_ALL).findAll(svgText).forEach { match ->
                val element = match.value
                val d = Regex("\\bd=\"([^\"]*)\"").find(element)?.groupValues?.get(1)
                if (d != null) {
                    val tr = Regex("translate\\(\\s*([-0-9.]+)(?:[ ,]+([-0-9.]+))?\\s*\\)").find(element)
                    val tx = tr?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val tyRaw = tr?.groupValues?.getOrNull(2)
                    val ty = if (tyRaw.isNullOrBlank()) 0f else tyRaw.toFloatOrNull() ?: 0f

                    if (Regex("[A-Za-z]").findAll(d).any { !allowedCommands.contains(it.value) }) {
                        skipBake = true
                    }
                    val numbers = numberRegex.findAll(d).map { it.value.toFloat() }.toList()
                    var i = 0
                    while (i + 1 < numbers.size) {
                        xs += numbers[i] + tx
                        ys += numbers[i + 1] + ty
                        i += 2
                    }

                    if (!skipBake && (tx != 0f || ty != 0f)) {
                        var index = 0
                        val baked = numberRegex.replace(d) { found ->
                            val shifted = found.value.toFloat() + if (index % 2 == 0) tx else ty
                            index++
                            formatSvgNumber(shifted)
                        }
                        val elementNew = element.replace(d, baked)
                            .replace(Regex("\\s*transform=\"[^\"]*\""), "")
                        result = result.replace(element, elementNew)
                    }
                }
            }

            if (xs.isEmpty()) return svgText
            val minX = xs.min(); val maxX = xs.max()
            val minY = ys.min(); val maxY = ys.max()
            val artwork = maxOf(maxX - minX, maxY - minY)
            if (artwork <= 0f) return svgText
            val side = artwork / logoSvgFraction
            val centerX = (minX + maxX) / 2f
            val centerY = (minY + maxY) / 2f
            val viewX = centerX - side / 2f
            val viewY = centerY - side / 2f
            val tag = "<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" " +
                "width=\"" + formatSvgNumber(side) + "\" height=\"" + formatSvgNumber(side) + "\" " +
                "viewBox=\"" + formatSvgNumber(viewX) + " " + formatSvgNumber(viewY) + " " +
                formatSvgNumber(side) + " " + formatSvgNumber(side) + "\">"
            logger.lifecycle(
                "MARKETMAPS_GEO=رسم=" + formatSvgNumber(artwork) + " لوحة=" + formatSvgNumber(side) +
                    " نسبة=" + (100f * artwork / side).toInt() + "% مخبوز=" + if (skipBake) "لا" else "نعم"
            )
            return Regex("<svg\\b[^>]*>").replaceFirst(result, tag)
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
        val logoJson = mutableListOf<String>()
        val iconNames = mutableListOf<String>()
        // الترتيب حسب القائمة المعلنة أولاً، ثم أي شعار جديد أبجدياً في الآخر
        val logoSvgOrder = inputs.properties.getValue("logoSvgOrder") as List<*>
        val svgFiles: List<File> =
            (svgDir.listFiles(svgFilter)?.toList() ?: emptyList()).sortedWith(
                compareBy(
                    { file: File -> logoSvgOrder.indexOf(file.nameWithoutExtension).let { i: Int -> if (i < 0) logoSvgOrder.size else i } },
                    { file: File -> file.name }
                )
            )
        logger.lifecycle("MARKETMAPS_SVG_FOUND=${svgFiles.size} dir=${svgDir.absolutePath}")
        svgFiles
            .forEachIndexed { logoIndex: Int, svg: File ->
                val key = svg.nameWithoutExtension.lowercase().replace(Regex("[^a-z0-9_]"), "_")
                    .let { normalized: String ->
                        if (normalized.isEmpty() || normalized[0].isDigit()) {
                            "logo_$normalized"
                        } else {
                            normalized
                        }
                    }
                val vector = try {
                    // التطبيع ثم التحويل: هندسة موحّدة لكل شعار
                    val normalizedDir = File(outRoot, "normalized").apply { mkdirs() }
                    val normalizedFile = File(normalizedDir, svg.name)
                    normalizedFile.writeText(normalizeSvgText(svg.readText()))
                    convertSvg(normalizedFile)
                } catch (e: Exception) {
                    logger.lifecycle("MARKETMAPS_SKIP=${svg.name}: ${e.message}")
                    null
                } ?: return@forEachIndexed

                File(drawableDir, "logo_${key}_foreground.xml").writeText(vector)
                logoKeys += key
                logoJson += "{\"key\": \"$key\", \"file\": \"${svg.name}\", " +
                    "\"label\": \"الشعار ${logoIndex + 1}\"}"

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
            append(logoJson.joinToString(", "))
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
