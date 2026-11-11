/**
 * Companion «Автобусов» для Fitbit OS: сеть, каталог городов и маршрутов.
 * Общается с приложением на часах через messaging.
 */
import * as messaging from "messaging";
import * as core from "./core";
import data from "./data";

const PAGE_HOSTS = [
  "https://en.busti.me/",
  "https://busti.me/",
];
const FEED_HOSTS = [
  "https://busti.me/ajax/bus/?bus_id=",
  "https://en.busti.me/ajax/bus/?bus_id=",
];

const cities = data.cities;
const routes = data.routes;
let currentCityId = 3;

function cityById(id) {
  return cities.find((c) => c.id === id) || cities[0];
}

function fetchText(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    let done = false;
    const timer = setTimeout(() => {
      if (!done) {
        done = true;
        reject(new Error("timeout"));
      }
    }, timeoutMs);
    fetch(url)
      .then((res) => res.text())
      .then((text) => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        resolve(text);
      })
      .catch((err) => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        reject(err);
      });
  });
}

async function fetchFirst(urls, timeoutMs) {
  let last = null;
  for (const url of urls) {
    try {
      return await fetchText(url, timeoutMs);
    } catch (e) {
      last = e;
    }
  }
  throw last || new Error("network");
}

function send(msg) {
  try {
    if (messaging.peerSocket.readyState === messaging.peerSocket.OPEN) {
      messaging.peerSocket.send(msg);
    }
  } catch (e) {
    // device socket closed
  }
}

function sortRoutes(list) {
  return list.slice().sort((a, b) => {
    if (a.t !== b.t) return a.t - b.t;
    return String(a.n).localeCompare(String(b.n), "ru", { numeric: true });
  });
}

async function handleInit(lat, lon) {
  const city = core.nearestCity(cities, lat, lon, 250) || cityById(3);
  currentCityId = city.id;
  send({ type: "cities", cities, city });
  sendRoutes(city.id);
}

function sendRoutes(cityId) {
  const city = cityById(cityId);
  currentCityId = city.id;
  const list = sortRoutes(routes.filter((r) => r.c === city.id))
    .map((r) => ({ id: r.id, n: r.n, t: r.t, s: r.s }));
  send({ type: "routes", cityId: city.id, routes: list });
}

async function handleRoute(cityId, route) {
  const city = cityById(cityId);
  const slug = route.s;
  let page = null;
  try {
    const html = await fetchFirst(
      PAGE_HOSTS.map((h) => h + city.slug + "/" + slug + "/"), 12000);
    page = core.parseRoutePage(html);
  } catch (e) {
    send({ type: "error", message: "Нет данных маршрута" });
    return;
  }
  const d0 = core.directionStops(page, 0);
  const d1 = core.directionStops(page, 1);
  const dir = d0.length >= d1.length ? 0 : 1;
  const stops = dir === 0 ? d0 : d1;
  send({
    type: "stops",
    routeId: route.id,
    dir,
    dirs: [d0.length > 1, d1.length > 1],
    stops,
  });
}

async function handleFeed(routeId) {
  try {
    const json = await fetchFirst(
      FEED_HOSTS.map((h) => h + routeId), 9000);
    const vehicles = core.parseFeedVehicles(json);
    send({ type: "feed", vehicles });
  } catch (e) {
    send({ type: "error", message: "Нет связи с сервером" });
  }
}

messaging.peerSocket.onmessage = (evt) => {
  const msg = evt.data || {};
  if (msg.type === "init") {
    handleInit(msg.lat, msg.lon).catch(() => send({ type: "error", message: "Ошибка города" }));
  } else if (msg.type === "routes") {
    sendRoutes(msg.cityId || currentCityId);
  } else if (msg.type === "route") {
    handleRoute(msg.cityId || currentCityId, msg.route).catch(() =>
      send({ type: "error", message: "Ошибка маршрута" }));
  } else if (msg.type === "feed") {
    handleFeed(msg.routeId).catch(() => send({ type: "error", message: "Ошибка сети" }));
  }
};
