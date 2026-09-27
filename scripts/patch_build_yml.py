from pathlib import Path
p = Path(".github/workflows/build.yml")
c = p.read_text(encoding="utf-8")
if "SHA-1 Debug" in c:
    print("already")
else:
    old = """          else
            echo "MAPS_API_KEY=$MAPS_API_KEY" >> local.properties
          fi
"""
    new = """          else
            echo "MAPS_API_KEY=$MAPS_API_KEY" >> local.properties
            echo "تم حقن مفتاح API (الطول: ${#MAPS_API_KEY})"
          fi
          if [ -f app/debug.keystore ]; then
            echo "===== SHA-1 Debug (أضفه في Google Cloud Console) ====="
            keytool -list -v -keystore app/debug.keystore -storepass android -alias androiddebugkey 2>/dev/null | grep -E "SHA1:|SHA256:" || true
            echo "======================================================"
          fi
"""
    if old not in c:
        raise SystemExit("block not found")
    p.write_text(c.replace(old, new, 1), encoding="utf-8")
    print("build.yml patched")
