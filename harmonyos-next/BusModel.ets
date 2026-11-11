/**
 * Krasnoyarsk Bus Watch — переносимая логика для HarmonyOS NEXT (ArkTS).
 *
 * Здесь нет Android/UI-зависимостей: только модели, геодезия, разбор фида и
 * построение доски «остановка → автобусы + время ожидания».
 * Скопируйте файл в entry/src/main/ets/model/ проекта DevEco Studio.
 */

export interface RouteItem {
  id: number;
  name: string;
  type: number;   // 0 автобус, 1 троллейбус, 2 трамвай, 3 маршрутка
  title: string;
}

export interface StopItem {
  id: number;
  name: string;
  lat: number;
  lon: number;
}

export interface Vehicle {
  uid: string;
  routeId: number;
  routeName: string;
  lat: number;
  lon: number;
  speed: number;
  heading: number;
  nextStop: string;
  plate: string;
  dir: number;
  ts: number;
  sleep: boolean;
  stopId: number;
  order: number;
  distance: number;
}

export interface BoardRow {
  index: number;
  stop: StopItem;
  here: Vehicle[];
  etaMinutes: number;      // 0 = автобус здесь, -1 = нет прогноза
  isUserStop: boolean;
}

/** Демонстрационный набор маршрутов (полный каталог — в app/assets/routes.json). */
export const ROUTES: RouteItem[] = [
  { id: 534, name: '2', type: 0, title: 'Автовокзал Восточный ↔ Дом учёных' },
  { id: 535, name: '3', type: 0, title: 'Автовокзал Восточный ↔ Академгородок' },
  { id: 539, name: '5', type: 0, title: 'Спорткомплекс Радуга ↔ ОАО Красфарма' },
  { id: 551, name: '11', type: 0, title: '3-я Дальневосточная ↔ Молодёжная' },
  { id: 553, name: '12', type: 0, title: 'с/х Удачный ↔ мкр. Северный' },
  { id: 576, name: '52', type: 0, title: 'мкр. Тихие Зори ↔ Озеро' },
  { id: 540, name: '5', type: 1, title: '4-й мкр. (Славы) ↔ Экопарк Гремячая грива' },
  { id: 533, name: '2', type: 2, title: 'КрасТЭЦ ↔ пос. Энергетиков' },
];

/** Демонстрационные остановки маршрута 2 (полные списки — в route_stops.json). */
export const DEMO_STOPS: StopItem[] = [
  { id: 7830, name: 'Автовокзал Восточный', lat: 56.017931, lon: 93.028793 },
  { id: 7744, name: 'КрасТЭЦ', lat: 56.014051, lon: 92.975774 },
  { id: 7746, name: 'Крылова', lat: 56.003723, lon: 92.935116 },
  { id: 7749, name: 'Хлебозавод', lat: 55.992556, lon: 92.886849 },
  { id: 7700, name: 'Краевая детская библиотека', lat: 56.013286, lon: 92.876213 },
  { id: 7701, name: 'Дом дружбы народов Родина', lat: 56.004803, lon: 92.940513 },
  { id: 7750, name: 'ДК 1 Мая', lat: 56.012623, lon: 92.851744 },
  { id: 28001, name: 'Аэрокосмическая академия', lat: 56.009728, lon: 92.801528 },
];

export class Geo {
  static readonly EARTH_R = 6371000;
  static readonly CITY_LAT = 56.0106;
  static readonly CITY_LON = 92.8526;

  static distanceMeters(lat1: number, lon1: number, lat2: number, lon2: number): number {
    const p1 = lat1 * Math.PI / 180;
    const p2 = lat2 * Math.PI / 180;
    const dp = p2 - p1;
    const dl = (lon2 - lon1) * Math.PI / 180;
    const a = Math.sin(dp / 2) * Math.sin(dp / 2)
      + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return 2 * Geo.EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  static formatDistance(m: number): string {
    if (m < 950) return `${Math.max(5, Math.round(m / 10) * 10)} м`;
    const km = m / 1000;
    return km < 9.95 ? `${km.toFixed(1).replace('.', ',')} км` : `${Math.round(km)} км`;
  }

  static etaMinutes(m: number): number {
    const min = Math.round(m / 1000 / 20 * 60 + 0.5);
    return Math.min(90, Math.max(1, min));
  }
}

interface FeedVehicle {
  x?: number;
  y?: number;
  u?: string;
  s?: number;
  h?: number;
  bn?: string;
  g?: string;
  d?: number;
  ts?: number;
  sleep?: boolean;
  b?: number;
  order?: number;
}

interface FeedBdata {
  l?: FeedVehicle[];
}

interface FeedRoot {
  bdata_mode0?: FeedBdata;
}

export class Feed {
  static parse(json: string, route: RouteItem): Vehicle[] {
    const out: Vehicle[] = [];
    if (!json) return out;
    let root: FeedRoot;
    try {
      root = JSON.parse(json) as FeedRoot;
    } catch (e) {
      return out;
    }
    const bd = root.bdata_mode0;
    if (!bd || !bd.l) return out;
    for (const m of bd.l) {
      if (m.x === undefined || m.y === undefined) continue;
      const lat = m.y;
      const lon = m.x;
      if (lat === 0 || lon === 0) continue;
      out.push({
        uid: m.u ?? '',
        routeId: route.id,
        routeName: route.name,
        lat: lat,
        lon: lon,
        speed: m.s ?? 0,
        heading: m.h ?? 0,
        nextStop: m.bn ?? '',
        plate: m.g ?? '',
        dir: m.d ?? 0,
        ts: m.ts ?? 0,
        sleep: m.sleep === true,
        stopId: m.b ?? 0,
        order: m.order ?? -1,
        distance: 0,
      });
    }
    return out;
  }
}

export class Board {
  static readonly SECONDS_PER_STOP = 100;

  static resolveOrder(v: Vehicle, stops: StopItem[]): number {
    if (v.order >= 0 && v.order < stops.length) return v.order;
    if (v.stopId > 0) {
      const i = stops.findIndex((s: StopItem) => s.id === v.stopId);
      return i;
    }
    return -1;
  }

  static build(stops: StopItem[], vehicles: Vehicle[], dir: number,
               userLat: number, userLon: number, nowSec: number): BoardRow[] {
    const rows: BoardRow[] = [];
    const live: Vehicle[] = vehicles.filter((v: Vehicle) =>
      v.dir === dir && !v.sleep && (v.ts === 0 || nowSec - v.ts <= 300));

    let userIdx = -1;
    let best = Number.MAX_VALUE;
    for (let i = 0; i < stops.length; i++) {
      const d = Geo.distanceMeters(userLat, userLon, stops[i].lat, stops[i].lon);
      if (d < best) {
        best = d;
        userIdx = i;
      }
    }

    for (let i = 0; i < stops.length; i++) {
      const here: Vehicle[] = [];
      let eta = -1;
      let bestOrder = -1;
      for (const v of live) {
        const o = Board.resolveOrder(v, stops);
        if (o === i) here.push(v);
        else if (o >= 0 && o < i && o > bestOrder) bestOrder = o;
      }
      if (here.length > 0) eta = 0;
      else if (bestOrder >= 0) {
        eta = Math.max(1, Math.round((i - bestOrder) * Board.SECONDS_PER_STOP / 60));
      }
      rows.push({ index: i, stop: stops[i], here: here, etaMinutes: eta, isUserStop: i === userIdx });
    }
    return rows;
  }

  static sortByDistance(vehicles: Vehicle[], lat: number, lon: number): Vehicle[] {
    for (const v of vehicles) {
      v.distance = Geo.distanceMeters(lat, lon, v.lat, v.lon);
    }
    return vehicles.sort((a: Vehicle, b: Vehicle) => a.distance - b.distance);
  }
}
