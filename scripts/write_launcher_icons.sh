#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP=$(mktemp -d)

if ! command -v convert >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq imagemagick
fi

# تجميع أجزاء أيقونة التطبيق
cat "$ROOT"/scripts/ic_launcher_part{0..7}.b64 | tr -d '\n' | base64 -d > "$TMP/ic_launcher.png"
cat "$ROOT"/scripts/ic_launcher_round_part{0..7}.b64 | tr -d '\n' | base64 -d > "$TMP/ic_launcher_round.png"

declare -A SIZES=([mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192)
for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  size="${SIZES[$dens]}"
  dir="$ROOT/app/src/main/res/mipmap-${dens}"
  mkdir -p "$dir"
  for name in ic_launcher ic_launcher_round; do
    convert "$TMP/${name}.png" -resize "${size}x${size}" -strip "PNG32:${dir}/${name}.png"
    echo "Wrote ${name} ${dens} ${size}x${size} $(wc -c < ${dir}/${name}.png) bytes"
  done
done
rm -rf "$TMP"
echo "Launcher icons installed successfully."
