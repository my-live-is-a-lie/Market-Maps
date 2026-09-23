#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
B64="$ROOT/scripts/launcher_icons.tar.gz.b64"
TMP=$(mktemp -d)
base64 -d < "$B64" > "$TMP/icons.tar.gz"
tar -xzf "$TMP/icons.tar.gz" -C "$TMP"
for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p "$ROOT/app/src/main/res/mipmap-${dens}"
  for name in ic_launcher ic_launcher_round; do
    if [ -f "$TMP/mipmap-${dens}/${name}.png" ]; then
      cp "$TMP/mipmap-${dens}/${name}.png" "$ROOT/app/src/main/res/mipmap-${dens}/${name}.png"
      echo "Wrote ${name} ${dens} ($(wc -c < "$ROOT/app/src/main/res/mipmap-${dens}/${name}.png") bytes)"
    fi
  done
done
rm -rf "$TMP"
