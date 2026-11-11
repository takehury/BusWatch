# Публикационный пакет

Всё, что нужно, чтобы выложить проект на GitHub и в магазины приложений.

## 1. Описание репозитория

**Название:** Автобусы — умные часы

**Короткое описание (About, 350 символов):**

> Автобусы, троллейбусы и трамваи на умных часах: живые позиции, остановки,
> прогноз ожидания и виброуведомление о прибытии. Wear OS, HarmonyOS, Tizen.
> 22 города России. Открытый исходный код, MIT.

**Topics:**

```
wearos harmonyos tizen smartwatch android bus public-transport realtime
java krasnoyarsk russia wearable watch transit open-source mit
```

**Категории для магазинов:** Транспорт / Навигация; для RuStore — «Транспорт».
**Возрастной рейтинг:** 0+ (нет пользовательского контента и покупок).
**Разрешения и их назначение:**

| Разрешение | Зачем |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | загрузка позиций автобусов |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | ближайшая остановка и автоопределение города |
| `VIBRATE` | сигнал о прибытии |
| `POST_NOTIFICATIONS` | уведомление о прибытии |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | ожидание автобуса при выключенном экране |
| `SCHEDULE_EXACT_ALARM`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | сторожевой будильник в Doze |

## 2. Что входит в пакет

```
README.md                 описание, скриншоты, сборка, тесты
LICENSE                   MIT
CONTRIBUTING.md           как предложить изменения
CHANGELOG.md              история версий
app/                      исходники + assets (22 города)
models/                   сборки по моделям часов
versions/v1.28/           APK последней версии + changelog
docs/                     устройства, архитектура, данные, фон, скриншоты
docs/releases/v1.28.md    текст для GitHub Releases
tests/, tools/, data/     тесты, скрипты сбора каталога, выгрузки
.github/workflows/        CI: тесты + сборка APK
build_apk.sh, run_tests.sh
```

## 3. Чек-лист публикации

- [ ] Создать пустой репозиторий на GitHub (без README).
- [ ] `git remote add origin git@github.com:<user>/<repo>.git && git push -u origin master`.
- [ ] В Settings → Description вставить короткое описание из раздела 1.
- [ ] Topics — список из раздела 1.
- [ ] Social preview — `docs/images/screenshots/github-banner.png`.
- [ ] Создать Release `v1.28`, тело — `docs/releases/v1.28.md`, приложить 3 APK и `.wgt`.
- [ ] Проверить, что Actions прошёл (workflow `build-and-test`).
- [ ] (опционально) Включить Issues и Discussions.

## 4. Текст для GitHub Releases (v1.28)

Заголовок: **Автобусы 1.28 — ожидание автобуса при выключенном экране**

Тело — файл [`docs/releases/v1.28.md`](releases/v1.28.md).

## 5. Для магазинов приложений

- **Скриншоты:** `docs/images/screenshots/01-routes.png` … `06-error.png`
  (1200×1500), обложка `hero.png` (2400×1200).
- **Иконка:** `docs/images/icon.png` (512×512) и `app/icon_master.png`.
- **Описание (полное):** раздел «Возможности» в `README.md`.
- **Политика конфиденциальности:** приложение не собирает и не передаёт
  персональные данные; координаты используются только на устройстве для выбора
  ближайшей остановки. Требуется отдельный документ при публикации в RuStore.
- **Данные:** публичный фид Bustime; проект не является официальным продуктом
  Bustime (см. `docs/DATA.md`).
