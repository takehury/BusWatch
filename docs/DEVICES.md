# Поддерживаемые часы

Приложение написано так, чтобы один APK работал на любых часах с **Wear OS**
(Android 8+) и на APK-совместимых **HarmonyOS**. Ниже — модели с 2020 года.

## Как достигается универсальность

| Параметр | Решение |
|---|---|
| Версия Android | `minSdk 26` (Android 8.0), `targetSdk 34`; новые API вызываются только под проверкой версии |
| Разрешение | весь UI нарисован на холсте **466×466** и равномерно масштабируется: 360, 384, 390, 396, 408, 416, 450, 454, 456, 466, 480 px |
| Плотность пикселей | только `dp`/`sp`, никаких фиксированных пикселей; шрифты масштабируются системой |
| Форма экрана | интерфейс вписан в круг радиусом 112 dp; на квадратных экранах остаётся круглый циферблат |
| Сеть | Bluetooth-тетеринг, Wi-Fi и LTE/eSIM выглядят для Android как обычная сеть — отдельный код не нужен |

Проверка масштабирования закреплена тестами (`Displays`, 360–480 px) и превью
`tests/preview/10-size-360.png`, `11-size-480.png`.

## Wear OS (полная поддержка)

| Модель | Год | ОС | Экран | Примечание |
|---|---|---|---|---|
| Google Pixel Watch | 2022 | Wear OS 3.5 | 384×384 | ✅ |
| Google Pixel Watch 2 | 2023 | Wear OS 4 | 384×384 | ✅ |
| Google Pixel Watch 3 (41 мм) | 2024 | Wear OS 5 | 408×408 | ✅ |
| Google Pixel Watch 3 (45 мм) | 2024 | Wear OS 5 | 456×456 | ✅ |
| Samsung Galaxy Watch 4 (40/44) | 2021 | Wear OS 3 | 396/450 | ✅ |
| Samsung Galaxy Watch 5 (40/44) | 2022 | Wear OS 3.5 | 396/450 | ✅ |
| Samsung Galaxy Watch 6 (40/44) | 2023 | Wear OS 4 | 396/480 | ✅ |
| Samsung Galaxy Watch 7 (40/44) | 2024 | Wear OS 5 | 396/480 | ✅ |
| Samsung Galaxy Watch FE | 2024 | Wear OS 4 | 396 | ✅ |
| Xiaomi Watch 2 | 2024 | Wear OS 3.5 | 466×466 | ✅ |
| Xiaomi Watch 2 Pro | 2023 | Wear OS 3.5 | 466×466 | ✅ |
| OnePlus Watch 2 | 2024 | Wear OS 4 | 466×466 | ✅ |
| OnePlus Watch 2R | 2024 | Wear OS 4 | 466×466 | ✅ |
| Mobvoi TicWatch Pro 5 | 2023 | Wear OS 3.5 | 466×466 | ✅ |
| Fossil Gen 6 | 2021 | Wear OS 3 | 416×416 | ✅ |

Сборка: `models/wear-os/` (`KrskBusWatch-wear-*.apk`, watch required, targetSdk 34).

## HarmonyOS

| Модель | Год | ОС | Экран | Статус |
|---|---|---|---|---|
| Huawei Watch 3 / 3 Pro | 2021 | HarmonyOS 2 | 466×466 | ⚠️ APK ставится только на прошивки с поддержкой sideload |
| Huawei Watch 4 / 4 Pro | 2023 | HarmonyOS 3.1 | 466×466 | ⚠️ то же |
| Huawei Watch 5 | 2024 | HarmonyOS 5 | 466×466 | ❌ только HAP, APK не работает |
| Huawei Watch GT 2/3/4/5 | 2019–2024 | HarmonyOS / LiteOS | 454/466 | ❌ закрытая ОС, сторонние приложения не ставятся |

Сборка: `models/harmonyos/` (APK для APK-совместимых прошивок).
Для HarmonyOS NEXT — `models/harmonyos-next/` (нативный HAP, нужен DevEco Studio).

## Не поддерживается

Часы на закрытых RTOS: Xiaomi Watch S1/S2/S3 (MIUI/HyperOS), OnePlus Watch 1,
Amazfit, Garmin, Polar, Withings. Они не запускают APK — нужен отдельный нативный порт.

## Samsung Galaxy Watch 3 (2020)

Работает на **Tizen 5.5**, а не на Wear OS. Для них — отдельный Tizen-порт
в `models/samsung-galaxy-watch-3/` (`.wgt`, требуется подпись Samsung).

## Установка на Wear OS

1. На часах: Настройки → Система → О телефоне/часах → 7 раз по «Номер сборки»,
   затем «Для разработчиков» → **Отладка по ADB** (или по Wi-Fi).
2. `adb install -r KrskBusWatch-wear-1.28.apk`
3. Разрешить геопозицию и уведомления.

## Сеть на часах

- **Bluetooth**: часы используют интернет телефона автоматически (Wear OS).
- **Wi-Fi**: включается в настройках часов; приложение ничего не требует.
- **LTE/eSIM**: на моделях с eSIM работает без телефона; отдельного разрешения нет.

Перед запросом приложение проверяет доступность сети и показывает подсказку
«Нет сети: проверьте Wi-Fi, Bluetooth или LTE» вместо долгого ожидания.
