#!/usr/bin/env bash
# Создать бэкап текущего состояния Android TV перед дебулотом.
# Использование: backup.sh <HOST:PORT> [backup_dir]
set -euo pipefail

ADDR="${1:?Использование: backup.sh HOST:PORT [backup_dir]}"
DIR="${2:-backup/$(date +%Y%m%d_%H%M%S)}"

mkdir -p "$DIR"
echo "Сохраняю бэкап в $DIR ..."

echo "  Список всех пакетов..."
adb -s "$ADDR" shell pm list packages -f > "$DIR/packages-all.txt"

echo "  Список отключённых пакетов..."
adb -s "$ADDR" shell pm list packages -d > "$DIR/packages-disabled.txt"

echo "  getprop..."
adb -s "$ADDR" shell getprop > "$DIR/getprop.txt"

echo "  settings system..."
adb -s "$ADDR" shell settings list system > "$DIR/settings-system.txt"

echo "  settings secure..."
adb -s "$ADDR" shell settings list secure > "$DIR/settings-secure.txt"

echo "  settings global..."
adb -s "$ADDR" shell settings list global > "$DIR/settings-global.txt"

echo "  dumpsys package (список)..."
adb -s "$ADDR" shell "pm list packages" | sed 's/package://' | while read -r pkg; do
    echo "$pkg" >> "$DIR/package-names.txt"
done

echo ""
echo "Бэкап сохранён в: $DIR"
echo "Файлы:"
ls -la "$DIR/"
