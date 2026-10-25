#!/usr/bin/env bash
# Дебулот TCL Android TV — пример для TCL ME RT2851-S1.
# Перед запуском выполните: scripts/backup.sh HOST:PORT
#
# ВАЖНО: скрипт не запускается автоматически. Просмотрите список и
# раскомментируйте строки, которые хотите отключить.
set -euo pipefail

ADDR="${1:?Использование: debloat-tcl.sh HOST:PORT}"

disable() {
    echo -n "  $1 ... "
    local r=$(adb -s "$ADDR" shell "pm disable-user --user 0 '$1'" 2>&1)
    if echo "$r" | grep -qi "new state: disabled"; then
        echo "ОК"
    else
        echo "ОШИБКА: $r"
    fi
}

echo "=== TCL телеметрия и аналитика ==="
# disable com.tcl.bi               # TCL BI (бизнес-аналитика)
# disable com.tcl.guard            # TCL Guard
# disable com.tcl.logsystem        # TCL Log System
# disable com.tcl.statistics        # TCL Statistics
# disable com.tcl.usercenter       # TCL User Center (может вернуться после ребута)

echo ""
echo "=== TCL рекламные/контент сервисы ==="
# disable com.tcl.tvweishi         # TCL рекомендации
# disable com.tcl.microvideo       # TCL микровидео
# disable com.tcl.partnercustomizer # TCL партнёрский кастомизатор

echo ""
echo "=== Фоновые сервисы ==="
# disable com.tcl.miracast         # Miracast (если не используете)
# disable com.android.printspooler # Печать (ТВ не печатает)

echo ""
echo "=== Предустановленные приложения ==="
# disable com.google.android.videos  # Google TV / Google Play Фильмы
# disable com.google.android.music   # Google Play Музыка
# disable com.google.android.apps.magazines # Google Новости

echo ""
echo "=== Стриминговые приложения ==="
echo "# Раскомментируйте только те, которые вы НЕ используете:"
# disable com.netflix.ninja          # Netflix
# disable com.google.android.youtube.tv  # YouTube
# disable com.spotify.tv             # Spotify

echo ""
echo "Готово. Перезагрузите ТВ: adb -s $ADDR shell reboot"
echo "После ребута проверьте: adb -s $ADDR shell pm list packages -d"
