from pathlib import Path

mp = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
mc = mp.read_text(encoding="utf-8")
replacements = [
    ("zoom >= 18 -> 88", "zoom >= 18 -> 52"),
    ("zoom >= 17 -> 80", "zoom >= 17 -> 48"),
    ("zoom >= 16 -> 72", "zoom >= 16 -> 44"),
    ("zoom >= 15 -> 64", "zoom >= 15 -> 40"),
    ("zoom >= 14 -> 56", "zoom >= 14 -> 36"),
    ("zoom >= 13 -> 44", "zoom >= 13 -> 30"),
    ("zoom >= 12 -> 32", "zoom >= 12 -> 24"),
    ("zoom >= 11 -> 22", "zoom >= 11 -> 18"),
    ("DisplayMode.BUBBLE_LARGE -> 80", "DisplayMode.BUBBLE_LARGE -> 48"),
    ("DisplayMode.BUBBLE_MEDIUM -> 56", "DisplayMode.BUBBLE_MEDIUM -> 36"),
    ("DisplayMode.CIRCLE -> 28", "DisplayMode.CIRCLE -> 18"),
]
n = 0
for a, b in replacements:
    if a in mc:
        mc = mc.replace(a, b)
        n += 1
        print("replaced", a, "->", b)
for a, b in [
    ("DisplayMode.HIDDEN, DisplayMode.CIRCLE -> 28", "DisplayMode.HIDDEN, DisplayMode.CIRCLE -> 20"),
    ("DisplayMode.BUBBLE_MEDIUM -> 80", "DisplayMode.BUBBLE_MEDIUM -> 48"),
    ("DisplayMode.BUBBLE_LARGE -> 96", "DisplayMode.BUBBLE_LARGE -> 56"),
]:
    if a in mc and mc.count(a) == 1:
        mc = mc.replace(a, b)
        n += 1
        print("pin", a, "->", b)
    elif a in mc:
        print("skip multi", a, mc.count(a))
mp.write_text(mc, encoding="utf-8")
print("total", n)
