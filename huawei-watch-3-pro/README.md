# Huawei Watch 3 Pro — основная сборка

Это основное приложение проекта: Android APK под круглый экран 466×466 (xhdpi),
управление колёсиком, fisheye-список, 22 города РФ, ожидание автобуса в фоне.

| Что | Где |
|---|---|
| Исходники (текущая версия) | `../../app/` |
| Сборка | `../../build_apk.sh` → `../../versions/v1.70/KrskBusWatch-1.70.apk` |
| Тесты | `../../run_tests.sh` (319 проверок + живой фид) |
| Релиз 1.70 | `../../versions/v1.70/KrskBusWatch-1.70.apk` |
| Все версии | `../../versions/v1.1 … v1.8` |
| Полные снапшоты | `../../backups/` |

## Установка

```bash
adb install -r versions/v1.70/KrskBusWatch-1.70.apk
```

Требуется Android 8.0+ (API 26). На Watch 3 Pro (HarmonyOS 2) устанавливается через ADB
в режиме разработчика.

> Для HarmonyOS NEXT (5.x) APK не подойдёт — см. `../harmonyos-next/`.
