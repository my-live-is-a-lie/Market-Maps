#!/usr/bin/env python3
"""Ensure a stable debug keystore exists (generate once, then keep in git)."""
import subprocess
from pathlib import Path

ks = Path("app/debug.keystore")
if ks.exists() and ks.stat().st_size > 100:
    print("keystore already exists", ks.stat().st_size)
else:
    ks.parent.mkdir(parents=True, exist_ok=True)
    subprocess.check_call([
        "keytool", "-genkeypair", "-v",
        "-keystore", str(ks),
        "-storepass", "android",
        "-alias", "androiddebugkey",
        "-keypass", "android",
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-validity", "10000",
        "-dname", "CN=MarketMaps Debug,O=MarketMaps,C=EG",
    ])
    print("generated", ks)

r = subprocess.run(
    ["keytool", "-list", "-v", "-keystore", str(ks),
     "-storepass", "android", "-alias", "androiddebugkey"],
    capture_output=True, text=True,
)
for line in r.stdout.splitlines():
    if "SHA1:" in line or "SHA256:" in line:
        print(line.strip())
