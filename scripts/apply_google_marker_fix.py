import base64
from pathlib import Path

# Will be filled - use direct file write approach with smaller MarkerIconHelper patch only
from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

old = """    private fun legacyPx(px: Int): Int =
        (px * (density / 2.75f)).roundToInt().coerceAtLeast(1)

    fun markerSizePxForZoom(zoom: Int): Int = when {
        zoom >= 18 -> legacyPx(88)
        zoom >= 17 -> legacyPx(80)
        zoom >= 16 -> legacyPx(72)
        zoom >= 15 -> legacyPx(64)
        zoom >= 14 -> legacyPx(56)
        zoom >= 13 -> legacyPx(48)
        zoom >= 12 -> legacyPx(36)
        else -> 0
    }

    fun markerSizePxForMode(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> legacyPx(80)
        DisplayMode.BUBBLE_MEDIUM -> legacyPx(56)
        DisplayMode.CIRCLE -> legacyPx(28)
        DisplayMode.HIDDEN -> 0
    }

    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.HIDDEN, DisplayMode.CIRCLE -> legacyPx(28)
        DisplayMode.BUBBLE_MEDIUM -> legacyPx(80)
        DisplayMode.BUBBLE_LARGE -> legacyPx(96)
    }"""

new = """    /** أحجام ثابتة بالبكسل كما النسخة القديمة (بدون ضرب بكثافة الشاشة). */
    private fun legacyPx(px: Int): Int = px.coerceAtLeast(1)

    /** نفس جدول النسخة القديمة: خطوات متقاربة مع الزوم */
    fun markerSizePxForZoom(zoom: Int): Int = when {
        zoom >= 18 -> 88
        zoom >= 17 -> 80
        zoom >= 16 -> 72
        zoom >= 15 -> 64
        zoom >= 14 -> 56
        zoom >= 13 -> 44
        zoom >= 12 -> 32
        zoom >= 11 -> 22
        else -> 0
    }

    fun sizeForZoom(zoom: Int): Int = markerSizePxForZoom(zoom)

    fun markerSizePxForMode(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> 80
        DisplayMode.BUBBLE_MEDIUM -> 56
        DisplayMode.CIRCLE -> 28
        DisplayMode.HIDDEN -> 0
    }

    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.HIDDEN, DisplayMode.CIRCLE -> 28
        DisplayMode.BUBBLE_MEDIUM -> 80
        DisplayMode.BUBBLE_LARGE -> 96
    }"""

if old not in c:
    if "fun sizeForZoom" in c and "density / 2.75f" not in c:
        print("Already patched")
    else:
        raise SystemExit("legacy block not found")
else:
    c = c.replace(old, new, 1)
    p.write_text(c, encoding="utf-8")
    print("MarkerIconHelper patched")
