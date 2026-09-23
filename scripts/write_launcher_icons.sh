#!/bin/bash
# تثبيت أيقونات التطبيق الصغيرة في مجلدات res
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DATA="$ROOT/scripts/icon_data"

if [ ! -d "$DATA" ]; then
  echo "icon_data not found at $DATA"
  exit 1
fi

while IFS='|' read -r rel safe size; do
  [ -z "$rel" ] && continue
  src="$DATA/${safe}.b64"
  dest="$ROOT/app/src/main/res/$rel"
  mkdir -p "$(dirname "$dest")"
  tr -d '\n' < "$src" | base64 -d > "$dest"
  echo "Wrote $dest ($(wc -c < "$dest") bytes)"
done < "$DATA/manifest.txt"

echo "Launcher icons installed successfully."
