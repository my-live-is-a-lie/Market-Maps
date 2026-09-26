from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

# استبدال بسيط للقيم
replacements = [
    ("DisplayMode.BUBBLE_MEDIUM -> 64", "DisplayMode.BUBBLE_MEDIUM -> 80"),
    ("DisplayMode.BUBBLE_LARGE -> 88", "DisplayMode.BUBBLE_LARGE -> 96"),
]

changed = 0
for old, new in replacements:
    count = c.count(old)
    if count:
        c = c.replace(old, new)
        changed += count
        print(f"Replaced {count}x: {old} -> {new}")

if changed == 0:
    raise SystemExit("No size values found to replace")

p.write_text(c, encoding="utf-8")
print(f"Done, {changed} replacements")
