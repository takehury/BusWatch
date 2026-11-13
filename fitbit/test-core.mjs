import fs from "node:fs";
import assert from "node:assert";
import * as core from "./core.mjs";

let passed = 0;
let failed = 0;
function check(name, cond) {
  if (cond) {
    passed++;
    console.log(`  ok   ${name}`);
  } else {
    failed++;
    console.log(`  FAIL ${name}`);
  }
}
const read = (p) => fs.readFileSync(new URL(p, import.meta.url), "utf8");

/* ------------------------------------------------------------------- geo */
const mskSpb = core.distanceMeters(55.7558, 37.6173, 59.9343, 30.3351);
check("geo moscow-spb ~634 km", mskSpb > 620000 && mskSpb < 650000);
check("geo same point", core.distanceMeters(56, 92, 56, 92) === 0);
check("clamp", core.clamp(5, 0, 3) === 3 && core.clamp(-1, 0, 3) === 0);

/* ---------------------------------------------------------------- cities */
const cities = core.parseCities(read("../../app/assets/cities.json"));
check("cities parsed", cities.length === 22);
check("cities filled", cities.every((c) => c.id && c.name && isFinite(c.lat)));
const spb = core.cityById(cities, 5);
check("city by id", spb && spb.slug === "spb");
check("nearest spb", core.nearestCity(cities, 59.93, 30.31, 100).id === 5);
check("nearest krasnoyarsk", core.nearestCity(cities, 56.01, 92.85, 100).id === 3);
check("nearest none far away", core.nearestCity(cities, 0, 0, 200) === null);

/* ----------------------------------------------------------- stops API/fixtures */
const page = core.parseRoutePage(read("fixtures/spb_page.html"));
check("spb page dir0", page.dir0.length > 20);
check("spb page dir1", page.dir1.length > 20);
check("spb page busstops", Object.keys(page.stops).length > 20);
const stops = core.directionStops(page, 0);
check("spb direction stops", stops.length === page.dir0.length);
check("spb first stop coords", Math.abs(stops[0].lat - 59.8) < 1 && Math.abs(stops[0].lon - 30.2) < 1);

const nsk = core.parseRoutePage(read("fixtures/route_page_nsk.html"));
check("nsk page dir0 32", nsk.dir0.length === 32);
check("nsk page dir1 33", nsk.dir1.length === 33);

/* ------------------------------------------------------------------ feed */
const feed = core.parseFeedVehicles(read("fixtures/spb_feed.json"));
check("spb feed vehicles", feed.length === 22);
check("feed coords sane", feed.every((v) => v.lat > 59 && v.lat < 61 && v.lon > 29 && v.lon < 31));
check("feed dirs", feed.every((v) => v.dir === 0 || v.dir === 1));
check("feed unique", core.uniqueVehicles(feed.concat(feed)).length === feed.length);

const krskFeed = core.parseFeedVehicles(read("fixtures/bus_534.json"));
check("krsk feed parsed", krskFeed.length > 0);

/* ----------------------------------------------------------------- board */
const now = Math.max(...feed.map((v) => v.timestampSec)) + 30;
const board = core.buildBoard(stops, feed, 0, stops[0].lat, stops[0].lon, now, 300);
check("board rows == stops", board.rows.length === stops.length);
check("board user stop", board.userStopIndex === 0);
check("board has buses", board.rows.some((r) => r.hasBusHere));
check("board etas present", board.rows.filter((r) => r.etaMinutes > 0).length > 3);
check("board at stop eta 0", board.rows.filter((r) => r.hasBusHere)
  .every((r) => r.etaMinutes === 0));
check("board duplicates ignored", (() => {
  const dup = feed.concat(feed);
  const b = core.buildBoard(stops, dup, 0, stops[0].lat, stops[0].lon, now, 300);
  return b.rows.every((r) => r.atStop.length <= 3);
})());
check("board other direction length", (() => {
  const stops1 = core.directionStops(page, 1);
  const b = core.buildBoard(stops1, feed, 1, stops1[0].lat, stops1[0].lon, now, 300);
  return b.rows.length === stops1.length;
})());
check("board other direction not mixed", (() => {
  const onlyDir0 = feed.filter((v) => v.dir === 0);
  const b = core.buildBoard(stops, onlyDir0, 1, stops[0].lat, stops[0].lon, now, 300);
  return b.rows.every((r) => r.atStop.length === 0);
})());
check("board stale vehicles dropped", (() => {
  // «сейчас» на 20 часов позже снимка: фид вне часовых поясов → ничего не показываем
  const b = core.buildBoard(stops, feed, 0, stops[0].lat, stops[0].lon,
    now + 72000, 300);
  return b.rows.every((r) => r.atStop.length === 0);
})());

/* ----------------------------------------------------------- alert logic */
const orders = core.resolveVehicleOrders(stops, feed, 0, now, 300);
check("orders resolved", orders.size > 0);
const alert = new core.Alert(3);
check("alert no fire far", alert.check(new Map([["a", 0]])) === false);
const alert2 = new core.Alert(3);
check("alert fire at stop", alert2.check(new Map([["a", 3]])) === true);
const alert3 = new core.Alert(5);
alert3.check(new Map([["a", 4]]));
check("alert fire jump over", alert3.check(new Map([["a", 6]])) === true);
const alert4 = new core.Alert(5);
check("alert ignores passed bus", alert4.check(new Map([["a", 6]])) === false);
const alert5 = new core.Alert(2);
alert5.check(new Map([["a", 2]]));
check("alert fires once", alert5.check(new Map([["a", 0]])) === true);

/* ----------------------------------------------------------- formatting */
check("eta text here", core.etaText({ hasBusHere: true, etaMinutes: 0 }) === "здесь");
check("eta text minutes", core.etaText({ hasBusHere: false, etaMinutes: 7 }) === "~7′");
check("eta text none", core.etaText({ hasBusHere: false, etaMinutes: -1 }) === "—");
check("route number", core.routeNumber("2", 195) === "2");
check("clock", /^\d{2}:\d{2}$/.test(core.clockText(new Date())));

/* ----------------------------------------------------------- feed clock */
const nowSec = Math.floor(Date.now() / 1000);
const nskV = { uid: "a", lat: 55, lon: 82, dir: 0, order: 0, stopId: 1, plate: "P", timestampSec: nowSec - 7 * 3600, sleeping: false };
const vladV = { uid: "b", lat: 43, lon: 131, dir: 0, order: 0, stopId: 1, plate: "Q", timestampSec: nowSec + 10 * 3600, sleeping: false };
const krskV = { uid: "c", lat: 56, lon: 92, dir: 0, order: 0, stopId: 1, plate: "R", timestampSec: nowSec - 60, sleeping: false };
check("clock offset nsk", core.estimateOffset([nskV], nowSec) === 7 * 3600);
check("clock offset vlad", core.estimateOffset([vladV], nowSec) === -10 * 3600);
check("clock offset utc", core.estimateOffset([krskV], nowSec) === 60);
check("clock fresh nsk", core.isFresh(nskV, nowSec, core.estimateOffset([nskV], nowSec)));
check("clock fresh vlad", core.isFresh(vladV, nowSec, core.estimateOffset([vladV], nowSec)));
check("clock stale dropped", !core.isFresh(
  { ...nskV, timestampSec: nowSec - 7 * 3600 - 900 }, nowSec, 7 * 3600));
check("clock no timestamp", core.isFresh({ ...nskV, timestampSec: 0 }, nowSec, 0));
check("clock broken feed rejected", (() => {
  const off = core.estimateOffset([{ ...nskV, timestampSec: nowSec - 20 * 3600 }], nowSec);
  return off === core.OFFSET_BROKEN
    && !core.isFresh(nskV, nowSec, off);
})());

/* --------------------------------------------------- companion data bundle */
const data = (await import("./companion/data.js")).default;
check("data cities 22", data.cities.length === 22);
check("data routes > 6000", data.routes.length > 6000);
check("data routes valid", data.routes.every((r) => r.c && r.id > 0 && r.n && r.s));
check("data city ids valid", data.routes.every((r) =>
  data.cities.some((c) => c.id === r.c)));
check("data every city has routes", data.cities.every((c) =>
  data.routes.some((r) => r.c === c.id)));

/* --------------------------------------------- app/core.js stays in sync */
const coreSrc = read("core.mjs");
const appCore = read("app/core.js");
const compCore = read("companion/core.js");
check("app core synced", appCore === coreSrc);
check("companion core synced", compCore === coreSrc);

console.log(`\nFITBIT CORE: ${passed} passed, ${failed} failed`);
process.exit(failed === 0 ? 0 : 1);
