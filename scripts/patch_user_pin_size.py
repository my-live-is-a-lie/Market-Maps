from pathlib import Path

p = Path("app/src/main/java/com/marketmaps/app/ui/map/MarkerIconHelper.kt")
c = p.read_text(encoding="utf-8")

# تكبير علامة الموقع الحالي (MEDIUM و LARGE)
old = """    fun getAndroidUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): AndroidBitmap? {
        if (appContext == null) return null
        val size = when (mode) {
            DisplayMode.CIRCLE -> 28
            DisplayMode.BUBBLE_MEDIUM -> 64
            DisplayMode.BUBBLE_LARGE -> 88
            DisplayMode.HIDDEN -> 0
        }
        return composeGoogleUserPin(size)
    }

    fun getUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): Bitmap {
        val size = when (mode) {
            DisplayMode.CIRCLE -> 28
            DisplayMode.BUBBLE_MEDIUM -> 64
            DisplayMode.BUBBLE_LARGE -> 88
            DisplayMode.HIDDEN -> 0
        }
        return MapsforgeAndroidBitmap(composeGoogleUserPin(size))
    }"""

new = """    fun getAndroidUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): AndroidBitmap? {
        if (appContext == null) return null
        val size = when (mode) {
            DisplayMode.CIRCLE -> 36
            DisplayMode.BUBBLE_MEDIUM -> 80
            DisplayMode.BUBBLE_LARGE -> 96
            DisplayMode.HIDDEN -> 0
        }
        return composeGoogleUserPin(size)
    }

    fun getUserLocationBitmap(mode: DisplayMode = DisplayMode.BUBBLE_MEDIUM): Bitmap {
        val size = when (mode) {
            DisplayMode.CIRCLE -> 36
            DisplayMode.BUBBLE_MEDIUM -> 80
            DisplayMode.BUBBLE_LARGE -> 96
            DisplayMode.HIDDEN -> 0
        }
        return MapsforgeAndroidBitmap(composeGoogleUserPin(size))
    }"""

if old not in c:
    raise SystemExit("user pin size block not found")
c = c.replace(old, new, 1)
p.write_text(c, encoding="utf-8")
print("User pin size updated: MEDIUM 64→80, LARGE 88→96")
