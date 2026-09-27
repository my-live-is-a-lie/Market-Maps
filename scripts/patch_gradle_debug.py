from pathlib import Path
import re
gp = Path("app/build.gradle.kts")
gc = gp.read_text(encoding="utf-8")
if "debug.keystore" in gc:
    print("already configured")
else:
    m = re.search(r"signingConfigs\s*\{", gc)
    if not m:
        raise SystemExit("signingConfigs not found")
    insert_at = m.end()
    block = """
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
"""
    gc = gc[:insert_at] + block + gc[insert_at:]
    gp.write_text(gc, encoding="utf-8")
    print("debug signing added")
