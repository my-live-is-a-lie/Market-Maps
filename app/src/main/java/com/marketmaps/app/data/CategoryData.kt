package com.marketmaps.app.data

/**
 * بيانات التصنيفات الهرمية المؤقتة.
 * لاحقاً سننقلها إلى Firebase ليتم تعديلها بدون تحديث التطبيق.
 */
object CategoryData {

    data class Category(
        val name: String,
        val subCategories: List<SubCategory> = emptyList()
    )

    data class SubCategory(
        val name: String,
        val thirdLevel: List<String> = emptyList()
    )

    val categories = listOf(
        Category(
            name = "محل",
            subCategories = listOf(
                SubCategory("بقالة"),
                SubCategory("عطارة"),
                SubCategory("أدوات منزلية"),
                SubCategory("مكتبة"),
                SubCategory("صيدلية"),
                SubCategory("سباكة"),
                SubCategory("أجهزة كهربائية"),
                SubCategory("مواد بناء"),
                SubCategory("ملابس"),
                SubCategory("أحذية"),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "ورشة",
            subCategories = listOf(
                SubCategory("سمكرة"),
                SubCategory("نجارة"),
                SubCategory("حدادة"),
                SubCategory("ميكانيكا"),
                SubCategory("كهرباء"),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "مصنع",
            subCategories = listOf(
                SubCategory("ملابس"),
                SubCategory("أغذية"),
                SubCategory("بلاستيك"),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "مدرسة",
            subCategories = listOf(
                SubCategory("ابتدائية", listOf("بنين", "بنات", "مختلط")),
                SubCategory("إعدادية", listOf("بنين", "بنات", "مختلط")),
                SubCategory("ثانوية", listOf("بنين", "بنات", "مختلط")),
                SubCategory("لغات", listOf("بنين", "بنات", "مختلط")),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "مستشفى / عيادة",
            subCategories = listOf(
                SubCategory("مستشفى"),
                SubCategory("عيادة"),
                SubCategory("مختبر"),
                SubCategory("أسنان"),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "مطعم / مقهى",
            subCategories = listOf(
                SubCategory("وجبات سريعة"),
                SubCategory("شعبي"),
                SubCategory("كافي شوب"),
                SubCategory("أخرى")
            )
        ),
        Category(
            name = "أخرى",
            subCategories = listOf(
                SubCategory("أخرى")
            )
        )
    )
}
