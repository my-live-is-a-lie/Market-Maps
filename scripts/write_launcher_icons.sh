#!/bin/bash
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "Installing pre-sized launcher icons..."

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xhdpi/ic_launcher.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xhdpi__ic_launcher.png.b64" > "$ROOT/app/src/main/res/mipmap-xhdpi/ic_launcher.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xhdpi__ic_launcher_foreground.png.b64" > "$ROOT/app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xhdpi/ic_launcher_round.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xhdpi__ic_launcher_round.png.b64" > "$ROOT/app/src/main/res/mipmap-xhdpi/ic_launcher_round.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxhdpi/ic_launcher.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxhdpi__ic_launcher.png.b64" > "$ROOT/app/src/main/res/mipmap-xxhdpi/ic_launcher.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxhdpi__ic_launcher_foreground.png.b64" > "$ROOT/app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxhdpi__ic_launcher_round.png.b64" > "$ROOT/app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-mdpi/ic_launcher.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-mdpi__ic_launcher.png.b64" > "$ROOT/app/src/main/res/mipmap-mdpi/ic_launcher.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-mdpi__ic_launcher_foreground.png.b64" > "$ROOT/app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-mdpi/ic_launcher_round.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-mdpi__ic_launcher_round.png.b64" > "$ROOT/app/src/main/res/mipmap-mdpi/ic_launcher_round.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-hdpi/ic_launcher.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-hdpi__ic_launcher.png.b64" > "$ROOT/app/src/main/res/mipmap-hdpi/ic_launcher.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-hdpi__ic_launcher_foreground.png.b64" > "$ROOT/app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-hdpi/ic_launcher_round.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-hdpi__ic_launcher_round.png.b64" > "$ROOT/app/src/main/res/mipmap-hdpi/ic_launcher_round.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxxhdpi/ic_launcher.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxxhdpi__ic_launcher.png.b64" > "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxxhdpi__ic_launcher_foreground.png.b64" > "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png)"
base64 -d < "$ROOT/scripts/icon_data/mipmap-xxxhdpi__ic_launcher_round.png.b64" > "$ROOT/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"

mkdir -p "$ROOT/$(dirname app/src/main/res/drawable/ic_launcher_monochrome.png)"
base64 -d < "$ROOT/scripts/icon_data/drawable__ic_launcher_monochrome.png.b64" > "$ROOT/app/src/main/res/drawable/ic_launcher_monochrome.png"

echo "Done."
find "$ROOT/app/src/main/res" -name "ic_launcher*.png" -exec ls -lh {} \;
