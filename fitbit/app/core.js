/**
 * Чистая логика порта «Автобусы» для Fitbit OS.
 * Не зависит от SDK: тестируется в Node (см. test-core.mjs).
 */

export const CITY_LAT = 56.02444;
export const CITY_LON = 92.90658;
export const SECONDS_PER_STOP = 100;
export const MAX_AGE_SEC = 300;

/* ------------------------------------------------------------------ geo */

export function distanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const p = Math.PI / 180;
  const dLat = (lat2 - lat1) * p;
  const dLon = (lon2 - lon1) * p;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * p) * Math.cos(lat2 * p) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

export function clamp(v, lo, hi) {
  return v < lo ? lo : (v > hi ? hi : v);
}

/* --------------------------------------------------------------- cities */

export function parseCities(json) {
  const arr = typeof json === "string" ? JSON.parse(json) : json;
  if (!Array.isArray(arr)) return [];
  return arr.map((c) => ({
    id: c.id,
    slug: c.slug || "",
    name: c.name || "?",
    lat: typeof c.lat === "number" ? c.lat : CITY_LAT,
    lon: typeof c.lon === "number" ? c.lon : CITY_LON,
  }));
}

export function nearestCity(cities, lat, lon, maxKm = 250) {
  let best = null;
  let bestD = Infinity;
  for (const c of cities) {
    const d = distanceMeters(lat, lon, c.lat, c.lon);
    if (d < bestD) {
      bestD = d;
      best = c;
    }
  }
  return bestD <= maxKm * 1000 ? best : null;
}

export function cityById(cities, id) {
  return cities.find((c) => c.id === id) || null;
}

/* ---------------------------------------------------------- route/stops */

/** /api/v1/stops?place=<id> → { routes, stops }. */
export function parseStopsApi(json) {
  const root = typeof json === "string" ? JSON.parse(json) : json;
  const b = (root && root.b) || [];
  const r = (root && root.r) || [];
  const routes = b.map((item) => {
    const [name, type, url] = item;
    const slug = String(url || "").replace(/^\/+|\/+$/g, "").split("/").pop();
    return { name: String(name || ""), type: Number(type) || 0, slug };
  });
  const stops = r.map((item) => ({
    id: Number(item[0]),
    name: String(item[1] || ""),
    lat: Number(item[2]),
    lon: Number(item[3]),
  }));
  return { routes, stops };
}

/** Route page HTML → { dir0, dir1, stops: {id: {name, lat, lon}} }. */
export function parseRoutePage(html) {
  const ids = (re) => {
    const m = html.match(re);
    if (!m) return [];
    const out = [];
    const num = /\d+/g;
    let n;
    while ((n = num.exec(m[1])) !== null) out.push(Number(n[0]));
    return out;
  };
  const dir0 = ids(/var\s+route0\s*=\s*\[([^\]]*)\]/);
  const dir1 = ids(/var\s+route1\s*=\s*\[([^\]]*)\]/);

  const stops = {};
  const start = html.indexOf("var busstops");
  if (start >= 0) {
    const brace = html.indexOf("{", start);
    const end = brace >= 0 ? matchingBrace(html, brace) : -1;
    const body = brace >= 0 ? html.slice(brace + 1, end > brace ? end : undefined) : "";
    const re = /(\d+)\s*:\s*\{[^{}]*?name\s*:\s*"((?:[^"\\]|\\.)*)"[^{}]*?x\s*:\s*(-?\d+(?:\.\d+)?)\s*,\s*y\s*:\s*(-?\d+(?:\.\d+)?)/g;
    let m;
    while ((m = re.exec(body)) !== null) {
      stops[Number(m[1])] = {
        id: Number(m[1]),
        name: unescape(m[2]),
        lon: parseFloat(m[3]),
        lat: parseFloat(m[4]),
      };
    }
  }
  return { dir0, dir1, stops };
}

function matchingBrace(s, open) {
  let depth = 0;
  for (let i = open; i < s.length; i++) {
    if (s[i] === "{") depth++;
    else if (s[i] === "}") {
      depth--;
      if (depth === 0) return i;
    }
  }
  return s.length;
}

export function unescape(s) {
  return String(s)
    .replace(/\\u([0-9a-fA-F]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16)))
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, "\\");
}

/** Ordered stop objects for one direction (unknown ids are dropped). */
export function directionStops(page, dir) {
  const ids = dir === 1 ? page.dir1 : page.dir0;
  const out = [];
  for (const id of ids) {
    const s = page.stops[id];
    if (s) out.push({ id: s.id, name: s.name, lat: s.lat, lon: s.lon });
  }
  return out;
}

/* ----------------------------------------------------------------- feed */

export function parseFeedVehicles(json) {
  let root;
  try {
    root = typeof json === "string" ? JSON.parse(json) : json;
  } catch (e) {
    return [];
  }
  const bd = root && root.bdata_mode0;
  const list = (bd && bd.l) || [];
  const out = [];
  for (const m of list) {
    const lat = Number(m.y);
    const lon = Number(m.x);
    if (!isFinite(lat) || !isFinite(lon) || lat === 0 || lon === 0) continue;
    out.push({
      uid: String(m.u || ""),
      lat,
      lon,
      dir: Number(m.d) || 0,
      order: m.order === undefined || m.order === null ? -1 : Number(m.order),
      stopId: Number(m.b) || 0,
      plate: String(m.bn || ""),
      timestampSec: Number(m.ts) || 0,
      sleeping: m.sleep === true || m.sleep === 1,
      speed: Number(m.s) || 0,
    });
  }
  return uniqueVehicles(out);
}

export function uniqueVehicles(list) {
  const seen = new Set();
  const out = [];
  for (const v of list) {
    const key = v.uid || `${v.lat},${v.lon},${v.plate}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(v);
  }
  return out;
}

/* ---------------------------------------------------------------- board */

function resolveOrder(v, stops) {
  if (v.order >= 0 && v.order < stops.length) return v.order;
  if (v.stopId > 0) {
    for (let i = 0; i < stops.length; i++) {
      if (stops[i].id === v.stopId) return i;
    }
  }
  return -1;
}

export function resolveVehicleOrders(stops, vehicles, dir, nowSec, maxAge = MAX_AGE_SEC) {
  const out = new Map();
  for (const v of vehicles) {
    if (v.dir !== dir || v.sleeping) continue;
    if (v.timestampSec > 0 && nowSec - v.timestampSec > maxAge) continue;
    const o = resolveOrder(v, stops);
    if (o >= 0) out.set(v.uid || `${v.lat},${v.lon},${v.plate}`, o);
  }
  return out;
}

/**
 * Per-stop board: which buses are here now and the wait for the next one.
 * Mirrors the Java Board used by the watch app.
 */
export function buildBoard(stops, vehicles, dir, userLat, userLon, nowSec, maxAge = MAX_AGE_SEC) {
  const byOrder = new Map();
  const live = [];
  for (const v of uniqueVehicles(vehicles)) {
    if (v.dir !== dir || v.sleeping) continue;
    if (v.timestampSec > 0 && nowSec - v.timestampSec > maxAge) continue;
    const o = resolveOrder(v, stops);
    if (o < 0) continue;
    live.push(v);
    if (!byOrder.has(o)) byOrder.set(o, []);
    byOrder.get(o).push(v);
  }

  let userIdx = -1;
  let best = Infinity;
  for (let i = 0; i < stops.length; i++) {
    const d = distanceMeters(userLat, userLon, stops[i].lat, stops[i].lon);
    if (d < best) {
      best = d;
      userIdx = i;
    }
  }

  const rows = [];
  for (let i = 0; i < stops.length; i++) {
    const at = byOrder.get(i) || [];
    let eta = -1;
    if (at.length > 0) {
      eta = 0;
    } else {
      let bestOrder = -1;
      for (const v of live) {
        const o = resolveOrder(v, stops);
        if (o < i && o > bestOrder) bestOrder = o;
      }
      if (bestOrder >= 0) {
        eta = Math.max(1, Math.round(((i - bestOrder) * SECONDS_PER_STOP) / 60));
      }
    }
    rows.push({
      index: i,
      stop: stops[i],
      atStop: at,
      etaMinutes: eta,
      hasBusHere: at.length > 0,
      isUserStop: i === userIdx,
    });
  }
  return { rows, userStopIndex: userIdx };
}

/* ---------------------------------------------------------------- alert */

export class Alert {
  constructor(stopIndex) {
    this.stopIndex = stopIndex;
    this.previous = new Map();
    this.fired = false;
  }

  check(orders) {
    if (this.fired) return true;
    let hit = false;
    for (const [key, now] of orders.entries()) {
      if (now === this.stopIndex) {
        hit = true;
        break;
      }
      const was = this.previous.get(key);
      if (was !== undefined && was >= 0 && was < this.stopIndex && now > this.stopIndex) {
        hit = true;
        break;
      }
    }
    this.previous = new Map(orders);
    if (hit) this.fired = true;
    return this.fired;
  }
}

/* ------------------------------------------------------------ formatting */

export function clockText(date) {
  const h = String(date.getHours()).padStart(2, "0");
  const m = String(date.getMinutes()).padStart(2, "0");
  return `${h}:${m}`;
}

export function etaText(row) {
  if (row.hasBusHere) return "здесь";
  if (row.etaMinutes > 0) return `~${row.etaMinutes}′`;
  return "—";
}

export function routeNumber(name, id) {
  const n = String(name || "").trim();
  return n || String(id);
}
