# Скриншоты для публикации

Готовый набор для GitHub, RuStore, AppGallery и Google Play.

| Файл | Размер | Назначение |
|---|---|---|
| `hero.png` | 2400×1200 | обложка README и соцпревью |
| `github-banner.png` | 1280×640 | баннер репозитория (Social preview) |
| `01-routes.png` … `06-error.png` | 1200×1500 | карточки экранов (4:5) |

Все изображения собраны из **реальных рендеров интерфейса** (не макеты): приложение
рисует кадр через `UiFrame`, тестовый рендерер сохраняет его в PNG, а скрипт
добавляет фон, безель и подписи.

Экраны сняты для **Санкт-Петербурга** (маршрут 2): данные фида и страницы
маршрута сохранены в `tests/fixtures/spb_*`, поэтому рендер воспроизводим офлайн.
Список прокручен к середине, а «сейчас» берётся из самого фида — на остановках
видны реальные автобусы (зелёные кружки) и прогнозы `~N′`.

## Пересобрать

```bash
# 1. отрендерить экраны в 2x (932×932)
./run_tests.sh
mkdir -p /tmp/prev /tmp/shots
find app/src/ru/krsk/buswatch -name '*.java' \
  ! -name 'WatchView.java' ! -name 'BusWatchActivity.java' \
  ! -name 'LocationHelper.java' ! -name 'BusRepository.java' \
  ! -name 'BusAlertService.java' ! -name 'Prefs.java' ! -name 'Net.java' \
  > /tmp/prev/srcs.txt
echo app/tests/ru/krsk/buswatch/Preview.java >> /tmp/prev/srcs.txt
javac --release 8 -encoding UTF-8 -d /tmp/prev @/tmp/prev/srcs.txt
java -Djava.awt.headless=true -cp /tmp/prev ru.krsk.buswatch.Preview tests/fixtures /tmp/shots 2

# 2. собрать карточки
python3 tools/make_screenshots.py /tmp/shots docs/images/screenshots
```

Требуется Python с Pillow и системные шрифты Arial (macOS).
