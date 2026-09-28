from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

# 1) user pin sizes
old_user = """    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> 146   // ~50م / 100قدم — ×2
        DisplayMode.BUBBLE_MEDIUM -> 104  // ~100م / 200قدم — ×2
        DisplayMode.CIRCLE -> 32          // ~200م / 500قدم — ×1.5
        DisplayMode.HIDDEN -> 52          // أبعد: يبقى ظاهراً بحجم متوسط
    }"""

new_user = """    private fun userPinSizePx(mode: DisplayMode): Int = when (mode) {
        DisplayMode.BUBBLE_LARGE -> 88    // ~50م / 100قدم — أصغر 40٪ من 146
        DisplayMode.BUBBLE_MEDIUM -> 104  // ~100م / 200قدم
        DisplayMode.CIRCLE -> 52          // ~200م / 500قدم = نفس حجم 200م/1000قدم
        DisplayMode.HIDDEN -> 52          // ~200م / 1000قدم فأكثر
    }"""

if old_user not in c:
    if "DisplayMode.BUBBLE_LARGE -> 88" in c:
        print("user pin already updated")
    else:
        raise SystemExit("userPinSizePx block not found")
else:
    c = c.replace(old_user, new_user, 1)
    print("user pin updated")

# 2) circle sizes -30%
old_mode = "DisplayMode.CIRCLE -> 16         // 200م نقطة"
new_mode = "DisplayMode.CIRCLE -> 11         // 200م نقطة (−30٪)"
if old_mode in c:
    c = c.replace(old_mode, new_mode, 1)
    print("mode circle size updated")
elif "DisplayMode.CIRCLE -> 11" in c:
    print("mode circle already")
else:
    print("WARN mode circle not found")

# zoom table: sizes used when circles show (zoom 15-16)
# zoom >= 16 was 34 (too large for circle mode) → 11
# zoom >= 15 was 16 → 11
for a, b in [
    ("zoom >= 16 -> 34   // ~100م", "zoom >= 16 -> 11   // ~200م / 500قدم — دائرة (−30٪)"),
    ("zoom >= 15 -> 16   // ~200م / 500 قدم — دائرة فقط", "zoom >= 15 -> 11   // دائرة (−30٪)"),
]:
    if a in c:
        c = c.replace(a, b, 1)
        print("replaced", a[:20])
    else:
        print("skip", a[:30])

p.write_text(c, encoding="utf-8")
print("OK")
