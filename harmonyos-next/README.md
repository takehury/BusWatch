# HarmonyOS NEXT (5.x) — нативный порт: заготовка и инструкция

> **Важно.** HarmonyOS NEXT **не запускает Android APK**. Сборки из `../harmonyos/` и
> корневого `dist/` на NEXT не установятся — нужен нативный пакет **HAP** на ArkTS/ArkUI.
>
> В этой папке — переносимая логика и пошаговая инструкция. Полный HAP **не собран**:
> для сборки и установки нужны DevEco Studio и **подпись, привязанная к вашему
> Huawei ID** (профиль подписи нельзя сгенерировать без аккаунта разработчика).

## Что переносится без изменений

Вся предметная логика уже отделена от Android-UI и перенесена в `BusModel.ets`:

| Модуль | Назначение |
|---|---|
| `RouteItem`, `StopItem`, `Vehicle` | модели данных |
| `Geo.distanceMeters` / `formatDistance` / `etaMinutes` | геодезия и форматирование |
| `parseFeed(json, route)` | разбор ответа Bustime |
| `buildBoard(stops, vehicles, dir, lat, lon, now)` | доска «остановка → автобусы + ЭТА» |
| `ROUTES`, `DEMO_STOPS` | демонстрационные данные (полный каталог — в `../../app/assets/`) |

## Пошаговый перенос

1. Установите **DevEco Studio 5.0+** и создайте проект:
   `File → New → Create Project → Application → Empty Ability`, язык **ArkTS**,
   API **12** (HarmonyOS NEXT), устройство — **Wearable** (или Phone для проверки).
2. Скопируйте `BusModel.ets` в `entry/src/main/ets/model/`.
3. Откройте `entry/src/main/module.json5` и добавьте разрешения:

```json5
"requestPermissions": [
  { "name": "ohos.permission.INTERNET" },
  { "name": "ohos.permission.GET_NETWORK_INFO" },
  { "name": "ohos.permission.APPROXIMATELY_LOCATION" },
  { "name": "ohos.permission.LOCATION" },
  { "name": "ohos.permission.VIBRATE" },
  { "name": "ohos.permission.KEEP_BACKGROUND_RUNNING" }
]
```

4. Сетевой запрос (замена `HttpURLConnection`):

```ts
import { http } from '@kit.NetworkKit';

async function fetchRoute(id: number): Promise<string> {
  const req = http.createHttp();
  try {
    const resp = await req.request(
      `https://busti.me/ajax/bus/?bus_id=${id}`,
      { method: http.RequestMethod.GET, connectTimeout: 5000, readTimeout: 8000 }
    );
    return resp.responseCode === 200 ? (resp.result as string) : '';
  } finally {
    req.destroy();
  }
}
```

5. Геолокация (замена `LocationManager`):

```ts
import { geoLocationManager } from '@kit.LocationKit';

geoLocationManager.on('locationChange', { priority: geoLocationManager.LocationRequestPriority.FIRST_FIX }, (loc) => {
  this.lat = loc.latitude;
  this.lon = loc.longitude;
});
```

6. Уведомление о прибытии (замена `NotificationManager`):

```ts
import { notificationManager } from '@kit.NotificationKit';

await notificationManager.publish({
  id: 1,
  content: {
    notificationContentType: notificationManager.ContentType.NOTIFICATION_CONTENT_BASIC_TEXT,
    normal: { title: 'Автобус прибывает', text: `Маршрут №${num} на остановке «${stop}»` }
  }
});
```

7. Фоновая задача (замена foreground-сервиса):

```ts
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { wantAgent } from '@kit.AbilityKit';

await backgroundTaskManager.startBackgroundRunning(
  this.context, backgroundTaskManager.BackgroundMode.DATA_TRANSFER, wantAgentObj
);
```

8. Колёсико часов: в ArkUI обрабатывается `onKeyEvent` у корневого компонента —
   `KEYCODE_DPAD_UP` / `KEYCODE_DPAD_DOWN`, а на некоторых моделях приходит
   `onRotate` (событие `RotateEvent`). В примере `Index.ets` реализован `onKeyEvent`.

9. Подпись: `File → Project Structure → Signing Configs → Automatically generate signature`
   (нужен вход в Huawei ID). Затем `Build → Build Hap(s)/App(s)`.
10. Установка на часы: `hdc install entry/build/default/outputs/default/entry-default-signed.hap`.

## Что нужно доделать вручную

- Экран выбора маршрута и доска остановок уже есть в `Index.ets`, но **fisheye-геометрия**
  (строки, сужающиеся к краям круга) в ArkUI делается через `Canvas` или
  `.scale()`/`.width()` по индексу — в примере упрощена до `List`.
- Полный каталог (74 маршрута, 999 остановок, списки остановок) лежит в
  `../../app/assets/*.json`; положите их в `resources/rawfile/` и читайте через
  `resourceManager.getRawFileContent('route_stops.json')`.
- Разрешение на уведомления в NEXT запрашивается через
  `notificationManager.requestEnableNotification()`.
- Проверьте названия API под вашу версию SDK: в API 12+ часть модулей переехала в
  `@kit.*`, в API 9–11 использовались `@ohos.*`.

## Ограничения

- HAP нельзя собрать и подписать в этом проекте — только в DevEco Studio с вашим Huawei ID.
- Код в `BusModel.ets` — переносимая логика; `Index.ets` — компактный пример интерфейса,
  а не полный повтор Android-версии.
- HarmonyOS NEXT на часах требует отдельной проверки жестов короны (модели отличаются).
