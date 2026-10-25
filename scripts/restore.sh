#!/usr/bin/env bash
# Восстановить все отключённые пакеты.
# Использование: restore.sh <HOST:PORT> [backup_dir]
#
# Включает обратно все пакеты из packages-disabled.txt,
# которых не было в бэкапе (т.е. которые мы сами отключили).
set -euo pipefail

ADDR="${1:?Использование: restore.sh HOST:PORT [backup_dir]}"
DIR="${2:-}"

if [[ -z "$DIR" ]]; then
    # Найти последний бэкап
    DIR=$(ls -d backup/*/ 2>/dev/null | sort | tail -1)
    if [[ -z "$DIR" ]]; then
        echo "Бэкап не найден. Укажите папку вручную." >&2
        exit 1
    fi
fi

echo "Восстановление из $DIR ..."

# Текущие отключённые пакеты
CURRENT_DISABLED=$(adb -s "$ADDR" shell pm list packages -d | sed 's/package://')

# Пакеты, которые были отключены ДО нашего дебулота
ORIG_DISABLED=""
if [[ -f "$DIR/packages-disabled.txt" ]]; then
    ORIG_DISABLED=$(cat "$DIR/packages-disabled.txt" | sed 's/package://')
fi

restored=0
for pkg in $CURRENT_DISABLED; do
    # Пропустить пакеты, которые уже были отключены до дебулота
    if echo "$ORIG_DISABLED" | grep -qx "$pkg"; then
        echo "  Пропускаю $pkg (был отключён до дебулота)"
        continue
    fi
    echo -n "  Включаю $pkg ... "
    result=$(adb -s "$ADDR" shell "pm enable $pkg" 2>&1)
    echo "$result"
    ((restored++))
done

echo ""
echo "Готово: включено обратно $restored пакетов."
echo ""
echo "Чтобы восстановить стоковый лаунчер:"
echo "  adb -s $ADDR shell pm enable <пакет_стокового_лаунчера>"
echo "  adb -s $ADDR shell cmd package set-home-activity <пакет_стокового_лаунчера>/.MainActivity"
echo ""
echo "Чтобы удалить наш лаунчер:"
echo "  adb -s $ADDR uninstall com.proxystytam.launcher"
