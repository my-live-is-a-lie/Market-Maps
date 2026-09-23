package com.marketmaps.app.data

/**
 * كتالوج خرائط Mapsforge المجانية (مستوى القارة → الدولة).
 * المصدر لا يوفّر ملفات على مستوى المحافظة حالياً.
 */
object MapCatalog {

    data class MapRegion(
        val id: String,
        val nameAr: String,
        val nameEn: String,
        val continentId: String,
        val continentAr: String,
        val fileName: String,
        val url: String,
        /** حجم تقريبي بالميجابايت (للتوضيح فقط) */
        val approxSizeMb: Int
    )

    data class Continent(
        val id: String,
        val nameAr: String,
        val nameEn: String
    )

    private const val BASE = "https://download.mapsforge.org/maps/v5"

    val continents = listOf(
        Continent("africa", "أفريقيا", "Africa"),
        Continent("asia", "آسيا", "Asia"),
        Continent("europe", "أوروبا", "Europe"),
        Continent("north-america", "أمريكا الشمالية", "North America"),
        Continent("south-america", "أمريكا الجنوبية", "South America"),
        Continent("australia-oceania", "أوقيانوسيا", "Australia & Oceania")
    )

    /** قائمة أولية — أفريقيا كاملة + دول عربية مختارة */
    val regions: List<MapRegion> = listOf(
        // أفريقيا
        r("egypt", "مصر", "Egypt", "africa", "أفريقيا", "egypt.map", 173),
        r("libya", "ليبيا", "Libya", "africa", "أفريقيا", "libya.map", 114),
        r("sudan", "السودان", "Sudan", "africa", "أفريقيا", "sudan.map", 254),
        r("tunisia", "تونس", "Tunisia", "africa", "أفريقيا", "tunisia.map", 66),
        r("algeria", "الجزائر", "Algeria", "africa", "أفريقيا", "algeria.map", 293),
        r("morocco", "المغرب", "Morocco", "africa", "أفريقيا", "morocco.map", 212),
        r("south-africa-and-lesotho", "جنوب أفريقيا", "South Africa", "africa", "أفريقيا", "south-africa-and-lesotho.map", 551),
        r("kenya", "كينيا", "Kenya", "africa", "أفريقيا", "kenya.map", 316),
        r("ethiopia", "إثيوبيا", "Ethiopia", "africa", "أفريقيا", "ethiopia.map", 144),
        r("nigeria", "نيجيريا", "Nigeria", "africa", "أفريقيا", "nigeria.map", 729),
        // آسيا (عربية)
        r("saudi-arabia", "السعودية", "Saudi Arabia", "asia", "آسيا", "saudi-arabia.map", 280),
        r("united-arab-emirates", "الإمارات", "UAE", "asia", "آسيا", "united-arab-emirates.map", 45),
        r("jordan", "الأردن", "Jordan", "asia", "آسيا", "jordan.map", 35),
        r("lebanon", "لبنان", "Lebanon", "asia", "آسيا", "lebanon.map", 25),
        r("iraq", "العراق", "Iraq", "asia", "آسيا", "iraq.map", 90),
        r("syria", "سوريا", "Syria", "asia", "آسيا", "syria.map", 55),
        r("turkey", "تركيا", "Turkey", "asia", "آسيا", "turkey.map", 450),
        r("israel-and-palestine", "فلسطين/إسرائيل", "Israel & Palestine", "asia", "آسيا", "israel-and-palestine.map", 40),
        // أوروبا مختارة
        r("germany", "ألمانيا", "Germany", "europe", "أوروبا", "germany.map", 900),
        r("france", "فرنسا", "France", "europe", "أوروبا", "france.map", 850),
        r("united-kingdom", "المملكة المتحدة", "United Kingdom", "europe", "أوروبا", "united-kingdom.map", 400),
        r("italy", "إيطاليا", "Italy", "europe", "أوروبا", "italy.map", 550),
        r("spain", "إسبانيا", "Spain", "europe", "أوروبا", "spain.map", 500),
        r("greece", "اليونان", "Greece", "europe", "أوروبا", "greece.map", 180)
    )

    private fun r(
        id: String,
        nameAr: String,
        nameEn: String,
        continentId: String,
        continentAr: String,
        fileName: String,
        mb: Int
    ) = MapRegion(
        id = id,
        nameAr = nameAr,
        nameEn = nameEn,
        continentId = continentId,
        continentAr = continentAr,
        fileName = fileName,
        url = "$BASE/$continentId/$fileName",
        approxSizeMb = mb
    )

    fun byContinent(continentId: String): List<MapRegion> =
        regions.filter { it.continentId == continentId }

    fun search(query: String): List<MapRegion> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return regions
        return regions.filter {
            it.nameAr.contains(q, ignoreCase = true) ||
                it.nameEn.lowercase().contains(q) ||
                it.continentAr.contains(q, ignoreCase = true)
        }
    }

    fun findById(id: String): MapRegion? = regions.find { it.id == id }
}
