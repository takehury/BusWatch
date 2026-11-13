# Сборки по моделям часов

| Папка | Модель / ОС | Артефакт |
|---|---|---|
| `huawei-watch-3-pro/` | Huawei Watch 3 Pro (HarmonyOS 2, Android APK) | `../versions/v1.70/KrskBusWatch-1.70.apk` |
| `wear-os/` | Wear OS 3 / 4 / 5 (Pixel Watch, Galaxy Watch 4+, TicWatch) | `wear-os/dist/KrskBusWatch-wear-1.70.apk` |
| `harmonyos/` | HarmonyOS 2 / 3 / 4 (APK-совместимые) | `harmonyos/dist/KrskBusWatch-harmony-1.70.apk` |
| `harmonyos-next/` | HarmonyOS NEXT 5.x (HAP, ArkTS) | заготовка + инструкция (DevEco Studio) |
| `samsung-galaxy-watch-3/` | Samsung Galaxy Watch 3 (Tizen 5.5) | `samsung-galaxy-watch-3/dist/KrskBusWatch-tizen-1.0.0.wgt` |
| `fitbit/` | Fitbit OS 5.x (Versa 3/4, Sense/2) | `fitbit/` → `fitbit.fba` (сборка через Fitbit SDK) |

Каждая папка самодостаточна: исходники, скрипт сборки, README с установкой и
ограничениями. Общее ядро — один и тот же фид Bustime и каталог городов.

## Быстрый выбор

- **Huawei Watch 3 Pro** → основная сборка (папка `huawei-watch-3-pro/`).
- **Wear OS 3/4/5** → `wear-os/`.
- **HarmonyOS 2–4** (часы и телефоны Huawei) → `harmonyos/`.
- **HarmonyOS NEXT 5.x** → APK не подходит, читайте `harmonyos-next/README.md`.
- **Fitbit** (Versa, Sense) → `fitbit/` (JavaScript + SVG, свой SDK).
- **Samsung Galaxy Watch 3** — это **Tizen**, не Wear OS → `samsung-galaxy-watch-3/`.
- **Galaxy Watch 4/5/6/7** — Wear OS → `wear-os/`.

## Сборка

```bash
cd models/wear-os && JAVA_HOME=/tmp/jdk17home ./build.sh
cd models/harmonyos && JAVA_HOME=/tmp/jdk17home ./build.sh
cd models/samsung-galaxy-watch-3 && ./build.sh
```

Требования: JDK 17, Android Build-Tools 34, Platform android-33
(в основной сборке — `build_apk.sh` в корне).
