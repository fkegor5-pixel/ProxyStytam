# Решение проблем

## Скриншоты 0 байт

Live TV, HDMI и некоторые приложения (Netflix) дают пустые скриншоты.
Это защита контента (DRM), а не сломанный дебулот.

Проверьте текущее приложение:
```bash
adb shell dumpsys activity activities | grep topResumedActivity
```

## pm disable-user отклоняется

Некоторые прошивки (Fire OS 5) отклоняют `pm disable-user` с `SecurityException`.
На таких устройствах дебулот через pm невозможен. Используйте:
- Приватный DNS для блокировки рекламы
- Отключение аналитики в настройках ТВ

## Пакеты возвращаются после ребута

TCL Android 9 может восстанавливать пакеты из собственного списка.
Проверьте после ребута:
```bash
adb shell pm list packages -d
```
Сравните с тем, что вы отключали. Если пакет вернулся — он не отключается
постоянно на этой прошивке. Не пытайтесь отключать его снова после каждого ребута.

## Экран увеличен/обрезан

Проверьте display overrides:
```bash
adb shell wm size
adb shell wm density
adb shell settings get secure display_density_forced
```
Если видите override — это не баг лаунчера, а остаток от прежних настроек.

## Кнопки пульта не работают после дебулота

Если после отключения стокового лаунчера перестали работать кнопки
(Netflix, YouTube, Input, Settings), включите соответствующие хелперы
в `config.xml`.

## Чёрный экран после ребута

Если Home показывает чёрный экран:
```bash
adb shell logcat | grep RecoveryActivity
```
Если setupwraith перехватывает Home:
```bash
adb shell pm enable <стоковый_лаунчер>
```

## factory/fac menu пакеты

**НИКОГДА** не запускайте пакеты с именами factory, fac, AT:
- `com.cvte.tv.hkcfac`
- `com.cvte.tv.fac.menu`
- `com.at.factory.*`
- `mediatek.factorymenu.ui`

На Haier/CVTE это включает factory mode и ломает Play Services.
Используйте `dumpsys package <name>` для просмотра без запуска.
