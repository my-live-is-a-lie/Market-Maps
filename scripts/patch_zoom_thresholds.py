from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

old = """    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode = when {
        zoom >= 17 -> DisplayMode.BUBBLE_LARGE   // حوالي 50 متر
        zoom >= 16 -> DisplayMode.BUBBLE_MEDIUM  // حوالي 100 متر
        zoom >= 15 -> DisplayMode.CIRCLE         // حوالي 200 متر / 500 قدم
        else -> DisplayMode.HIDDEN               // أبعد من 200م (لا دوائر)
    }"""

new = """    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode = when {
        // أقرب من ~200م/500قدم: فقاعات كاملة
        zoom >= 18 -> DisplayMode.BUBBLE_LARGE
        zoom >= 17 -> DisplayMode.BUBBLE_MEDIUM
        // عند ~200م / 500 قدم فقط: نقاط دائرية
        zoom >= 16 -> DisplayMode.CIRCLE
        // أبعد (مثل 200م/1000قدم فأكثر): لا أيقونات محلات
        else -> DisplayMode.HIDDEN
    }"""

if old not in c:
    # try without Arabic comments variation
    old2 = """    fun displayModeForZoom(zoom: Int, latitude: Double = 30.0): DisplayMode = when {
        zoom >= 17 -> DisplayMode.BUBBLE_LARGE
        zoom >= 16 -> DisplayMode.BUBBLE_MEDIUM
        zoom >= 15 -> DisplayMode.CIRCLE
        else -> DisplayMode.HIDDEN
    }"""
    if old2 in c:
        c = c.replace(old2, new, 1)
        print("replaced compact")
    elif "zoom >= 18 -> DisplayMode.BUBBLE_LARGE" in c and "zoom >= 16 -> DisplayMode.CIRCLE" in c:
        print("already patched")
    else:
        raise SystemExit("displayModeForZoom block not found")
else:
    c = c.replace(old, new, 1)
    print("replaced with comments")

p.write_text(c, encoding="utf-8")
print("OK")
