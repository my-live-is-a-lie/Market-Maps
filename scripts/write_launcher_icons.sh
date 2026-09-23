#!/bin/bash
# تثبيت أيقونات التطبيق الصغيرة في مجلدات res
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 "$ROOT/scripts/install_icons.py"
