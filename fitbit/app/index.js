/**
 * Приложение «Автобусы» для Fitbit OS 5.
 *
 * Список маршрутов → остановки с прогнозом → выбор города. Живые позиции
 * приходят от companion-приложения (у часового JS нет прямого доступа в сеть).
 */
import document from "document";
import * as messaging from "messaging";
import { geolocation } from "geolocation";
import { screen } from "screen";
import { vibration } from "haptics";
import * as core from "./core";

const W = screen.width;
const H = screen.height;
const HEADER = Math.round(H * 0.17);
const FOOTER = Math.round(H * 0.09);
const ROWS = 6;
const ROW_H = Math.round((H - HEADER - FOOTER) / ROWS);
const ROW_GAP = 4;

const el = {
  clock: document.getElementById("clock"),
  title: document.getElementById("title"),
  back: document.getElementById("back"),
  backLabel: document.getElementById("backLabel"),
  footer: document.getElementById("footer"),
  rows: [],
};

for (let i = 0; i < ROWS; i++) {
  el.rows.push({
    bg: document.getElementById(i === 0 ? "r0" : "r" + i),
    name: document.getElementById("n" + i),
    eta: document.getElementById("e" + i),
  });
}

const state = {
  mode: "loading",       // loading | routes | stops | cities | error
  cities: [],
  city: null,
  routes: [],
  route: null,
  stops: [],
  dir: 0,
  dirs: [true, true],
  vehicles: [],
  board: null,
  sel: 0,
  top: 0,
  message: "Загрузка…",
  waiting: false,
  lastFeedAt: 0,
};

/* ---------------------------------------------------------------- layout */

function layout() {
  el.clock.x = W / 2;
  el.clock.y = Math.round(HEADER * 0.36);
  el.title.x = W / 2;
  el.title.y = Math.round(HEADER * 0.78);

  el.back.x = 8;
  el.back.y = 6;
  el.back.width = Math.round(W * 0.16);
  el.back.height = Math.round(HEADER * 0.55);
  el.backLabel.x = 8 + Math.round(W * 0.08);
  el.backLabel.y = 6 + Math.round(HEADER * 0.38);

  el.footer.x = W / 2;
  el.footer.y = H - Math.round(FOOTER * 0.35);

  for (let i = 0; i < ROWS; i++) {
    const y = HEADER + i * ROW_H + ROW_GAP / 2;
    const row = el.rows[i];
    row.bg.x = 8;
    row.bg.y = y;
    row.bg.width = W - 16;
    row.bg.height = ROW_H - ROW_GAP;
    row.name.x = 22;
    row.name.y = y + Math.round(ROW_H * 0.62);
    row.eta.x = W - 22;
    row.eta.y = y + Math.round(ROW_H * 0.62);
  }

  el.back.onclick = back;
  el.backLabel.onclick = back;
  el.title.onclick = onHeaderRight;
}

/* --------------------------------------------------------------- drawing */

function item(i) {
  if (state.mode === "routes") return state.routes[state.top + i];
  if (state.mode === "stops") return state.board ? state.board.rows[state.top + i] : null;
  if (state.mode === "cities") return state.cities[state.top + i];
  return null;
}

function rowText(i) {
  const it = item(i);
  if (!it) return null;
  if (state.mode === "routes") {
    return { left: it.n, right: typeLabel(it.t) };
  }
  if (state.mode === "cities") {
    return { left: it.name, right: state.city && it.id === state.city.id ? "•" : "" };
  }
  return { left: it.stop.name, right: core.etaText(it) };
}

function typeLabel(type) {
  return ["авт", "трол", "трам", "марш"][type] || "авт";
}

function render() {
  el.clock.text = core.clockText(new Date());

  if (state.mode === "loading" || state.mode === "error") {
    el.title.text = state.mode === "error" ? "НЕТ СВЯЗИ" : "ЗАГРУЗКА";
    for (let i = 0; i < ROWS; i++) {
      el.rows[i].bg.style.fill = "#0b1218";
      el.rows[i].name.text = i === 2 ? state.message : "";
      el.rows[i].eta.text = "";
    }
    el.footer.text = state.mode === "error" ? "нажмите, чтобы повторить" : "";
    return;
  }

  if (state.mode === "routes") el.title.text = "МАРШРУТЫ";
  if (state.mode === "stops") el.title.text = state.route ? "№" + state.route.n : "ОСТАНОВКИ";
  if (state.mode === "cities") el.title.text = "ГОРОД";

  for (let i = 0; i < ROWS; i++) {
    const idx = state.top + i;
    const t = rowText(i);
    const row = el.rows[i];
    if (!t) {
      row.bg.style.fill = "#0b1218";
      row.name.text = "";
      row.eta.text = "";
      continue;
    }
    const selected = idx === state.sel;
    row.bg.style.fill = selected ? "#2a3b49" : "#16232e";
    row.name.text = t.left;
    row.name.style.fill = selected ? "#ffffff" : "#cfe0ec";
    row.eta.text = t.right;
    row.eta.style.fill = t.right === "здесь" ? "#3ddc97" : "#7f97a8";
  }

  if (state.mode === "stops") {
    el.footer.text = state.waiting ? "ЖДУ АВТОБУС — метка на остановке" : "свайп ↑↓ · нажмите для выбора";
  } else {
    el.footer.text = "свайп ↑↓ · нажмите для выбора";
  }
}

/* ------------------------------------------------------------------ list */

function size() {
  if (state.mode === "routes") return state.routes.length;
  if (state.mode === "cities") return state.cities.length;
  if (state.mode === "stops") return state.board ? state.board.rows.length : 0;
  return 0;
}

function move(delta) {
  const n = size();
  if (n <= 0) return;
  let sel = core.clamp(state.sel + delta, 0, n - 1);
  state.sel = sel;
  const half = Math.floor(ROWS / 2);
  state.top = core.clamp(sel - half, 0, Math.max(0, n - ROWS));
  render();
}

function open() {
  if (state.mode === "error") {
    retry();
    return;
  }
  if (state.mode === "cities") {
    const city = state.cities[state.sel];
    if (!city) return;
    state.city = city;
    send({ type: "routes", cityId: city.id });
    setMode("loading", "Маршруты…");
    return;
  }
  if (state.mode === "routes") {
    const route = state.routes[state.sel];
    if (!route) return;
    state.route = route;
    state.dir = 0;
    setMode("loading", "Остановки…");
    send({ type: "route", cityId: state.city.id, route });
    return;
  }
  if (state.mode === "stops") {
    state.waiting = !state.waiting;
    vibration.start("confirmation");
    render();
  }
}

function back() {
  if (state.mode === "stops") {
    stopFeed();
    state.mode = "routes";
    state.sel = Math.max(0, state.routes.findIndex((r) => r === state.route));
    state.top = core.clamp(state.sel - 2, 0, Math.max(0, state.routes.length - ROWS));
    state.waiting = false;
    render();
    return;
  }
  if (state.mode === "cities") {
    state.mode = "routes";
    state.sel = 0;
    state.top = 0;
    render();
    return;
  }
  // на списке маршрутов левая кнопка открывает выбор города
  if (state.mode === "routes") {
    state.mode = "cities";
    state.sel = 0;
    state.top = 0;
    render();
  }
}

function onHeaderRight() {
  if (state.mode === "stops") {
    toggleDir();
  } else if (state.mode === "routes") {
    state.mode = "cities";
    state.sel = 0;
    state.top = 0;
    render();
  }
}

/* ---------------------------------------------------------------- touch */

let touchStart = null;

function attachTouch(i) {
  const row = el.rows[i];
  const onDown = (e) => {
    touchStart = { y: e.screenY, x: e.screenX, row: i };
  };
  const onUp = (e) => {
    if (!touchStart) return;
    const dy = e.screenY - touchStart.y;
    const dx = e.screenX - touchStart.x;
    const sameRow = touchStart.row === i;
    touchStart = null;
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) {
      if (dx > 0) back(); else onHeaderRight();
      return;
    }
    if (Math.abs(dy) > ROW_H * 0.45) {
      move(dy < 0 ? 1 : -1);
      return;
    }
    if (sameRow) {
      const idx = state.top + i;
      if (idx !== state.sel) {
        state.sel = idx;
        render();
      } else {
        open();
      }
    }
  };
  row.bg.onmousedown = onDown;
  row.bg.onmouseup = onUp;
  row.name.onmousedown = onDown;
  row.name.onmouseup = onUp;
  row.eta.onmousedown = onDown;
  row.eta.onmouseup = onUp;
}

/* -------------------------------------------------------------- messaging */

function send(msg) {
  try {
    if (messaging.peerSocket.readyState === messaging.peerSocket.OPEN) {
      messaging.peerSocket.send(msg);
    }
  } catch (e) {
    setMode("error", "Нет связи с телефоном");
  }
}

messaging.peerSocket.onmessage = (evt) => {
  const data = evt.data || {};
  if (data.type === "cities") {
    state.cities = data.cities || [];
    if (data.city) state.city = data.city;
    setMode("routes", "");
    send({ type: "routes", cityId: state.city ? state.city.id : 3 });
    return;
  }
  if (data.type === "routes") {
    state.routes = data.routes || [];
    state.sel = 0;
    state.top = 0;
    if (state.cities.length === 0 && data.city) state.cities = state.cities;
    setMode("routes", "");
    return;
  }
  if (data.type === "stops") {
    state.dirs = data.dirs || [true, true];
    if (!state.dirs[state.dir]) state.dir = state.dirs[0] ? 0 : 1;
    state.stops = data.stops || [];
    rebuildBoard();
    setMode("stops", "");
    startFeed();
    return;
  }
  if (data.type === "feed") {
    state.vehicles = data.vehicles || [];
    state.lastFeedAt = Date.now();
    rebuildBoard();
    render();
    checkWaiting();
    return;
  }
  if (data.type === "error") {
    setMode("error", data.message || "Ошибка сети");
  }
};

messaging.peerSocket.onerror = () => {
  setMode("error", "Нет связи с телефоном");
};

function setMode(mode, message) {
  state.mode = mode;
  if (message !== undefined) state.message = message;
  if (mode !== "stops") stopFeed();
  render();
}

/* ------------------------------------------------------------------ feed */

let feedTimer = null;

function startFeed() {
  stopFeed();
  requestFeed();
  feedTimer = setInterval(requestFeed, 5000);
}

function stopFeed() {
  if (feedTimer) {
    clearInterval(feedTimer);
    feedTimer = null;
  }
}

function requestFeed() {
  if (!state.route) return;
  send({ type: "feed", routeId: state.route.id });
}

function rebuildBoard() {
  if (!state.stops || state.stops.length === 0) {
    state.board = null;
    return;
  }
  const lat = state.lat !== undefined ? state.lat : core.CITY_LAT;
  const lon = state.lon !== undefined ? state.lon : core.CITY_LON;
  const now = Math.floor(Date.now() / 1000);
  state.board = core.buildBoard(state.stops, state.vehicles, state.dir, lat, lon, now, 300);
}

function checkWaiting() {
  if (!state.waiting || !state.board) return;
  const row = state.board.rows.find((r) => r.isUserStop);
  if (row && row.hasBusHere) {
    vibration.start("ring");
    setTimeout(() => vibration.stop(), 1200);
    state.waiting = false;
    render();
  }
}

function toggleDir() {
  if (!state.dirs[0] && !state.dirs[1]) return;
  const next = state.dir === 0 ? 1 : 0;
  state.dir = state.dirs[next] ? next : state.dir;
  rebuildBoard();
  render();
}

function retry() {
  init();
}

/* ------------------------------------------------------------------ init */

function init() {
  setMode("loading", "Определяем город…");
  if (state.cities.length > 0) {
    send({ type: "routes", cityId: state.city ? state.city.id : 3 });
    return;
  }
  geolocation.getCurrentPosition(
    (pos) => {
      state.lat = pos.coords.latitude;
      state.lon = pos.coords.longitude;
      send({ type: "init", lat: state.lat, lon: state.lon });
    },
    () => {
      send({ type: "init", lat: core.CITY_LAT, lon: core.CITY_LON });
    },
    { timeout: 15000, maximumAge: 600000 }
  );
}

layout();
for (let i = 0; i < ROWS; i++) attachTouch(i);

messaging.peerSocket.onopen = () => {
  init();
};

if (messaging.peerSocket.readyState === messaging.peerSocket.OPEN) {
  init();
} else {
  setMode("loading", "Подключение…");
}

render();
