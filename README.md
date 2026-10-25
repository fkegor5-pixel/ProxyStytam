# ProxyStytam — Дебулот и кастомный лаунчер для Android TV

Чистый домашний экран в стиле Apple TV 4K для телевизоров на Android TV (TCL, Xiaomi, и др.).

## Что внутри

```
tools/          — утилиты для работы с ТВ по ADB
  tv.py         — команды, кнопки, скриншоты
  relay.py      — TCP-прокси для удалённых сессий
  disable.sh    — безопасное отключение пакетов (с защитой от брика)
  adb-closed.py — проверка, что ADB-порт закрыт
scripts/        — основные скрипты
  backup.sh     — бэкап перед дебулотом
  restore.sh    — восстановление всех пакетов
  debloat-tcl.sh — шаблон дебулота для TCL
  install-launcher.sh — сборка и установка лаунчера
launcher/       — Android TV лаунчер (Java, Gradle)
```

## Быстрый старт

### 1. Подготовка ТВ

На телевизоре: **Настройки → Об устройстве → Сборка** (нажать OK 7 раз) →
вернуться → **Для разработчиков → Отладка по USB** (вкл).

### 2. Подключение

```bash
adb connect <IP_ТВ>:5555
# Подтвердите запрос на экране ТВ
```

### 3. Бэкап

```bash
bash scripts/backup.sh <IP_ТВ>:5555
```

### 4. Дебулот

Отредактируйте `scripts/debloat-tcl.sh` — раскомментируйте пакеты для отключения:

```bash
bash scripts/debloat-tcl.sh <IP_ТВ>:5555
```

### 5. Сборка и установка лаунчера

Нужен Android SDK. Установите [Android Studio](https://developer.android.com/studio) или
[command-line tools](https://developer.android.com/studio#command-line-tools-only).

```bash
bash scripts/install-launcher.sh <IP_ТВ>:5555
```

Или соберите APK вручную:
```bash
cd launcher
./gradlew assembleDebug
# APK: launcher/app/build/outputs/apk/debug/app-debug.apk
adb -s <IP_ТВ>:5555 install app/build/outputs/apk/debug/app-debug.apk
```

Или скачайте готовый APK из [GitHub Actions](../../actions/workflows/build-launcher.yml).

### 6. Настройка дока

Отредактируйте `launcher/app/src/main/res/values/config.xml`:

```xml
<integer name="columns">4</integer>
<string name="dock_apps">com.google.android.youtube.tv,com.netflix.ninja,org.xbmc.kodi,com.google.android.tv.settings</string>
```

Пересоберите и переустановите.

### 7. Завершение

```bash
# Выключите отладку на ТВ
# Проверьте, что порт закрыт:
python3 tools/adb-closed.py <IP_ТВ>
```

## Восстановление

```bash
bash scripts/restore.sh <IP_ТВ>:5555
# Удалить лаунчер:
adb -s <IP_ТВ>:5555 uninstall com.proxystytam.launcher
```

## Приватный DNS (блокировка рекламы)

На Android 9+:
```bash
adb -s <IP_ТВ>:5555 shell settings put global private_dns_specifier dns.adguard-dns.com
adb -s <IP_ТВ>:5555 shell settings put global private_dns_mode hostname
```
Не блокирует рекламу YouTube. Отмена: `private_dns_mode opportunistic`.

Если роутер уже фильтрует DNS (Pi-hole, AdGuard Home) — Private DNS обойдёт его.
В этом случае лучше блокировать на роутере.
