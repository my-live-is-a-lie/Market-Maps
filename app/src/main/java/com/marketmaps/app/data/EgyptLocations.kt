package com.marketmaps.app.data

/**
 * قائمة مبسطة للمحافظات والمناطق في مصر مع إحداثيات تقريبية لمركز كل منطقة.
 * يمكن توسيعها لاحقاً أو نقلها إلى Firebase.
 */
object EgyptLocations {

    data class Area(val name: String, val latitude: Double, val longitude: Double)
    data class Governorate(val name: String, val areas: List<Area>)

    val governorates = listOf(
        Governorate(
            "القاهرة",
            listOf(
                Area("وسط البلد", 30.0444, 31.2357),
                Area("مدينة نصر", 30.0500, 31.3400),
                Area("المعادي", 29.9600, 31.2600),
                Area("مصر الجديدة", 30.0900, 31.3200),
                Area("شبرا", 30.1000, 31.2400),
                Area("حلوان", 29.8500, 31.3300)
            )
        ),
        Governorate(
            "الجيزة",
            listOf(
                Area("الدقي", 30.0380, 31.2110),
                Area("المهندسين", 30.0500, 31.2000),
                Area("الهرم", 29.9800, 31.1300),
                Area("6 أكتوبر", 29.9300, 30.9100),
                Area("الشيخ زايد", 30.0200, 30.9800)
            )
        ),
        Governorate(
            "الإسكندرية",
            listOf(
                Area("وسط الإسكندرية", 31.2001, 29.9187),
                Area("سيدي جابر", 31.2200, 29.9400),
                Area("سموحة", 31.2100, 29.9600),
                Area("العجمي", 31.1000, 29.7800)
            )
        ),
        Governorate(
            "القليوبية",
            listOf(
                Area("بنها", 30.4660, 31.1850),
                Area("شبرا الخيمة", 30.1300, 31.2400),
                Area("قليوب", 30.1800, 31.2100)
            )
        ),
        Governorate(
            "الشرقية",
            listOf(
                Area("الزقازيق", 30.5870, 31.5020),
                Area("العاشر من رمضان", 30.3000, 31.7500),
                Area("بلبيس", 30.4200, 31.5600)
            )
        ),
        Governorate(
            "الدقهلية",
            listOf(
                Area("المنصورة", 31.0409, 31.3785),
                Area("طلخا", 31.0500, 31.3700),
                Area("ميت غمر", 30.7200, 31.2600)
            )
        ),
        Governorate(
            "الغربية",
            listOf(
                Area("طنطا", 30.7865, 31.0004),
                Area("المحلة الكبرى", 30.9700, 31.1700)
            )
        ),
        Governorate(
            "المنوفية",
            listOf(
                Area("شبين الكوم", 30.5520, 31.0090),
                Area("منوف", 30.4700, 30.9300)
            )
        ),
        Governorate(
            "البحيرة",
            listOf(
                Area("دمنهور", 31.0340, 30.4680),
                Area("كفر الدوار", 31.1300, 30.1300)
            )
        ),
        Governorate(
            "بورسعيد",
            listOf(Area("بورسعيد", 31.2650, 32.3020))
        ),
        Governorate(
            "الإسماعيلية",
            listOf(Area("الإسماعيلية", 30.5965, 32.2715))
        ),
        Governorate(
            "السويس",
            listOf(Area("السويس", 29.9668, 32.5498))
        ),
        Governorate(
            "دمياط",
            listOf(Area("دمياط", 31.4165, 31.8133))
        ),
        Governorate(
            "كفر الشيخ",
            listOf(Area("كفر الشيخ", 31.1107, 30.9388))
        ),
        Governorate(
            "أسيوط",
            listOf(Area("أسيوط", 27.1800, 31.1800))
        ),
        Governorate(
            "سوهاج",
            listOf(Area("سوهاج", 26.5600, 31.7000))
        ),
        Governorate(
            "قنا",
            listOf(Area("قنا", 26.1600, 32.7200))
        ),
        Governorate(
            "الأقصر",
            listOf(Area("الأقصر", 25.6872, 32.6396))
        ),
        Governorate(
            "أسوان",
            listOf(Area("أسوان", 24.0889, 32.8997))
        ),
        Governorate(
            "الفيوم",
            listOf(Area("الفيوم", 29.3080, 30.8440))
        ),
        Governorate(
            "بني سويف",
            listOf(Area("بني سويف", 29.0700, 31.0900))
        ),
        Governorate(
            "المنيا",
            listOf(Area("المنيا", 28.1100, 30.7500))
        ),
        Governorate(
            "البحر الأحمر",
            listOf(
                Area("الغردقة", 27.2579, 33.8116),
                Area("سفاجا", 26.7500, 33.9500)
            )
        ),
        Governorate(
            "جنوب سيناء",
            listOf(
                Area("شرم الشيخ", 27.9158, 34.3300),
                Area("دهب", 28.5000, 34.5200)
            )
        ),
        Governorate(
            "شمال سيناء",
            listOf(Area("العريش", 31.1300, 33.8000))
        ),
        Governorate(
            "مطروح",
            listOf(Area("مرسى مطروح", 31.3500, 27.2400))
        )
    )
}
