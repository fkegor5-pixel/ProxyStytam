#!/usr/bin/env bash
# Собрать и установить лаунчер на ТВ.
# Использование: install-launcher.sh <HOST:PORT>
set -euo pipefail

ADDR="${1:?Использование: install-launcher.sh HOST:PORT}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LAUNCHER_DIR="$SCRIPT_DIR/../launcher"

echo "=== Сборка лаунчера ==="
cd "$LAUNCHER_DIR"
if [[ -f gradlew ]]; then
    ./gradlew assembleDebug --no-daemon
else
    gradle assembleDebug --no-daemon
fi

APK="$LAUNCHER_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -f "$APK" ]]; then
    echo "ОШИБКА: APK не найден: $APK" >&2
    exit 1
fi

echo ""
echo "=== Проверка SDK уровня ТВ ==="
TV_SDK=$(adb -s "$ADDR" shell getprop ro.build.version.sdk | tr -d '\r')
echo "SDK уровень ТВ: $TV_SDK"
if [[ "$TV_SDK" -lt 26 ]]; then
    echo "ОШИБКА: ТВ ниже Android 8.0 (SDK 26). Лаунчер несовместим." >&2
    exit 1
fi

echo ""
echo "=== Проверка возможности установки APK ==="
# Тест: pm install
adb -s "$ADDR" push "$APK" /data/local/tmp/launcher.apk
RESULT=$(adb -s "$ADDR" shell "pm install -r /data/local/tmp/launcher.apk" 2>&1)
echo "$RESULT"

if echo "$RESULT" | grep -qi "INSTALL_FAILED_VERIFICATION_FAILURE"; then
    echo ""
    echo "ОШИБКА: ТВ отклоняет все сторонние APK (INSTALL_FAILED_VERIFICATION_FAILURE)."
    echo "Установка лаунчера невозможна на этом устройстве."
    adb -s "$ADDR" shell rm -f /data/local/tmp/launcher.apk
    exit 1
fi

if echo "$RESULT" | grep -qi "INSTALL_FAILED_OLDER_SDK"; then
    echo ""
    echo "ОШИБКА: SDK ТВ ниже минимального для APK."
    adb -s "$ADDR" shell rm -f /data/local/tmp/launcher.apk
    exit 1
fi

if ! echo "$RESULT" | grep -qi "success"; then
    echo ""
    echo "Установка не удалась: $RESULT"
    adb -s "$ADDR" shell rm -f /data/local/tmp/launcher.apk
    exit 1
fi

adb -s "$ADDR" shell rm -f /data/local/tmp/launcher.apk
echo "Лаунчер установлен."

echo ""
echo "=== Установка как домашний экран ==="
# Список HOME-активностей
echo "HOME-активности:"
adb -s "$ADDR" shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME" 2>/dev/null || true

# Установить наш лаунчер как домашний
adb -s "$ADDR" shell "cmd package set-home-activity com.proxystytam.launcher/.MainActivity" 2>/dev/null || true

# Проверка
sleep 1
FOCUSED=$(adb -s "$ADDR" shell "dumpsys activity activities | grep mFocusedApp" 2>/dev/null || true)
echo "Текущий фокус: $FOCUSED"

if echo "$FOCUSED" | grep -q "proxystytam"; then
    echo ""
    echo "Лаунчер успешно установлен как домашний экран!"
else
    echo ""
    echo "ВНИМАНИЕ: Лаунчер установлен, но может не быть домашним экраном."
    echo "Нажмите кнопку Home на пульте — если появится выбор, выберите 'Home' и 'Всегда'."
fi
