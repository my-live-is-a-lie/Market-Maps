#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP=$(mktemp -d)

if ! command -v convert >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq imagemagick
fi

# تجميع أيقونات المصدر من الأجزاء
cat "$ROOT"/scripts/icon_src/ic_launcher.png.part* | tr -d '\n' | base64 -d > "$TMP/ic_launcher.png"
cat "$ROOT"/scripts/icon_src/ic_launcher_round.png.part* | tr -d '\n' | base64 -d > "$TMP/ic_launcher_round.png"

declare -A SIZES=([mdpi]=48 [hdpi]=72 [xhdpi]=96 [xxhdpi]=144 [xxxhdpi]=192)
declare -A FG_SIZES=([mdpi]=108 [hdpi]=162 [xhdpi]=216 [xxhdpi]=324 [xxxhdpi]=432)

for dens in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  size="${SIZES[$dens]}"
  fg="${FG_SIZES[$dens]}"
  dir="$ROOT/app/src/main/res/mipmap-${dens}"
  mkdir -p "$dir"
  convert "$TMP/ic_launcher.png" -resize "${size}x${size}" -strip "PNG32:${dir}/ic_launcher.png"
  convert "$TMP/ic_launcher_round.png" -resize "${size}x${size}" -strip "PNG32:${dir}/ic_launcher_round.png"
  content=$((fg * 66 / 100))
  convert -size ${fg}x${fg} xc:none \
    \( "$TMP/ic_launcher.png" -resize ${content}x${content} \) \
    -gravity center -composite -strip "PNG32:${dir}/ic_launcher_foreground.png"
  echo "Wrote $dens size=$size fg=$fg"
done

mkdir -p "$ROOT/app/src/main/res/drawable"
convert "$TMP/ic_launcher.png" -alpha extract -threshold 30% -alpha off \
  \( +clone -fill white -colorize 100 \) \
  -compose CopyOpacity -composite -resize 108x108 -strip \
  "PNG32:$ROOT/app/src/main/res/drawable/ic_launcher_monochrome.png"

rm -rf "$TMP"
echo "Launcher icons installed successfully."
find "$ROOT/app/src/main/res" -name "ic_launcher*.png" -exec ls -lh {} \;
