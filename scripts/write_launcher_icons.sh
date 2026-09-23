#!/bin/bash
# توليد كل أحجام أيقونات التطبيق من المصدر + Adaptive + Monochrome
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$ROOT/scripts/icon_src"
TMP=$(mktemp -d)

if ! command -v convert >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq imagemagick
fi

tr -d '\n' < "$DATA/ic_launcher.png.b64" | base64 -d > "$TMP/ic_launcher.png"
tr -d '\n' < "$DATA/ic_launcher_round.png.b64" | base64 -d > "$TMP/ic_launcher_round.png"

declare -A SIZES=([mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192)
for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  size="${SIZES[$dens]}"
  dir="$ROOT/app/src/main/res/mipmap-${dens}"
  mkdir -p "$dir"
  convert "$TMP/ic_launcher.png" -resize "${size}x${size}" -strip "PNG32:${dir}/ic_launcher.png"
  convert "$TMP/ic_launcher_round.png" -resize "${size}x${size}" -strip "PNG32:${dir}/ic_launcher_round.png"
  echo "Wrote launcher ${dens} ${size}x${size}"
done

declare -A FG=([xhdpi]=216 [xxhdpi]=324 [xxxhdpi]=432)
for dens in xhdpi xxhdpi xxxhdpi; do
  canvas="${FG[$dens]}"
  content=$(( canvas * 72 / 100 ))
  dir="$ROOT/app/src/main/res/mipmap-${dens}"
  mkdir -p "$dir"
  convert "$TMP/ic_launcher.png" -resize "${content}x${content}" \
    -background none -gravity center -extent "${canvas}x${canvas}" \
    -strip "PNG32:${dir}/ic_launcher_foreground.png"
  echo "Wrote foreground ${dens} ${canvas}x${canvas}"
done

mkdir -p "$ROOT/app/src/main/res/drawable"
convert "$TMP/ic_launcher.png" -resize 216x216 \
  -fuzz 25% -transparent '#024EE7' \
  -alpha extract -threshold 30% -transparent black \
  -background none -gravity center -extent 216x216 \
  -strip "PNG32:$ROOT/app/src/main/res/drawable/ic_launcher_monochrome.png"
echo "Wrote monochrome"

rm -rf "$TMP"
echo "=== Icon sizes ==="
find "$ROOT/app/src/main/res" -name "ic_launcher*.png" -exec ls -lh {} \;
echo "Launcher icons installed successfully."
