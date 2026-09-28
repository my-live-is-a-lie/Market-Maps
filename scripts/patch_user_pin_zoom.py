from pathlib import Path

# ── MarkerIconHelper: أحجام علامة موقعي ──
p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

old = """    /** أيقونة موقعي — أكبر بنسبة 30٪ عن السابق */
    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.HIDDEN -> 52         // يبقى ظاهراً عند التصغير
        DisplayMode.CIRCLE -> 21         // 16×1.3
        DisplayMode.BUBBLE_MEDIUM -> 52  // 40×1.3
        DisplayMode.BUBBLE_LARGE -> 73   // 56×1.3
    }"""

new = """    /**
     * أيقونة موقعي حسب مقياس الشارع:
     * ~50م و~100م: أكبر 100٪، ~200م/500قدم: أكبر 50٪.
     */
    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> 146   // ~50م / 100قدم — ×2
        DisplayMode.BUBBLE_MEDIUM -> 104  // ~100م / 200قدم — ×2
        DisplayMode.CIRCLE -> 32          // ~200م / 500قدم — ×1.5
        DisplayMode.HIDDEN -> 52          // أبعد: يبقى ظاهراً بحجم متوسط
    }"""

if old not in c:
    if "DisplayMode.BUBBLE_LARGE -> 146" in c:
        print("sizes already updated")
    else:
        raise SystemExit("userPinSizePx block not found")
else:
    c = c.replace(old, new, 1)
    p.write_text(c, encoding="utf-8")
    print("userPinSizePx updated")

# ── MapScreen: استخدم mode الحالي بدل BUBBLE_MEDIUM الثابت ──
p2 = Path("app/src/main/java/com/marketmaps/app/ui/map/MapScreen.kt")
c2 = p2.read_text(encoding="utf-8")
old2 = "val userBmp = MarkerIconHelper.getUserLocationBitmap(MarkerIconHelper.DisplayMode.BUBBLE_MEDIUM)"
new2 = "val userBmp = MarkerIconHelper.getUserLocationBitmap(mode)"
if old2 not in c2:
    if "getUserLocationBitmap(mode)" in c2:
        print("MapScreen already uses mode")
    else:
        raise SystemExit("userBmp line not found")
else:
    c2 = c2.replace(old2, new2, 1)
    p2.write_text(c2, encoding="utf-8")
    print("MapScreen uses zoom mode for user pin")

print("OK")
