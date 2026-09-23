#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ICON_DIR="$ROOT/scripts/icons"
for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  mkdir -p "$ROOT/app/src/main/res/mipmap-${dens}"
  for name in ic_launcher ic_launcher_round; do
    b64="$ICON_DIR/${name}_${dens}.b64"
    if [ -f "$b64" ]; then
      base64 -d < "$b64" > "$ROOT/app/src/main/res/mipmap-${dens}/${name}.png"
      echo "Wrote ${name} ${dens} ($(wc -c < "$ROOT/app/src/main/res/mipmap-${dens}/${name}.png") bytes)"
    fi
  done
done
