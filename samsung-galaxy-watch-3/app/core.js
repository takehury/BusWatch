/*
 * Чистая логика Tizen-порта: геодезия, разбор фида Bustime и построение
 * доски «остановка → автобусы + ЭТА».
 *
 * Файл не зависит от DOM, поэтому его можно проверить в Node:
 *   node test-core.js
 */
(function (root) {
    'use strict';

    function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

    function distance(lat1, lon1, lat2, lon2) {
        var p1 = lat1 * Math.PI / 180, p2 = lat2 * Math.PI / 180;
        var dp = p2 - p1, dl = (lon2 - lon1) * Math.PI / 180;
        var a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371000 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    function fmtDistance(m) {
        if (m < 950) return Math.max(5, Math.round(m / 10) * 10) + ' м';
        var km = m / 1000;
        return (km < 9.95 ? km.toFixed(1).replace('.', ',') : Math.round(km)) + ' км';
    }

    function etaMinutes(m) {
        return clamp(Math.round(m / 1000 / 20 * 60 + 0.5), 1, 90);
    }

    function typeLabel(t) {
        if (t === 1) return 'Троллейбус';
        if (t === 2) return 'Трамвай';
        if (t === 3) return 'Маршрутка';
        return 'Автобус';
    }

    function destination(route, dir) {
        var parts = String(route.title || '').split(/\s*(?:↔|⇄|—|–|-)\s*/);
        if (parts.length >= 2) return dir === 0 ? parts[parts.length - 1] : parts[0];
        return route.title || '';
    }

    function parseFeed(text) {
        var out = [];
        var root;
        try { root = JSON.parse(text); } catch (e) { return out; }
        var bd = root && root.bdata_mode0;
        if (!bd || !bd.l) return out;
        for (var i = 0; i < bd.l.length; i++) {
            var m = bd.l[i];
            if (typeof m.x !== 'number' || typeof m.y !== 'number') continue;
            if (!m.x || !m.y) continue;
            out.push({
                uid: m.u || '',
                lat: m.y,
                lon: m.x,
                next: m.bn || '',
                plate: m.g || '',
                dir: m.d || 0,
                ts: m.ts || 0,
                sleep: m.sleep === true || m.sleep === 1,
                stopId: m.b || 0,
                order: (typeof m.order === 'number' ? m.order : -1)
            });
        }
        return out;
    }

    function resolveOrder(v, stops) {
        if (v.order >= 0 && v.order < stops.length) return v.order;
        if (v.stopId > 0) {
            for (var i = 0; i < stops.length; i++) {
                if (stops[i].id === v.stopId) return i;
            }
        }
        return -1;
    }

    function buildBoard(stops, vehicles, dir, lat, lon, nowSec) {
        var now = nowSec || Math.floor(Date.now() / 1000);
        var live = (vehicles || []).filter(function (v) {
            return v.dir === dir && !v.sleep && (!v.ts || now - v.ts <= 300);
        });

        var userIdx = -1, best = Infinity;
        for (var i = 0; i < stops.length; i++) {
            var d = distance(lat, lon, stops[i].lat, stops[i].lon);
            if (d < best) { best = d; userIdx = i; }
        }

        var rows = [];
        for (var j = 0; j < stops.length; j++) {
            var here = 0, bestOrder = -1;
            for (var k = 0; k < live.length; k++) {
                var o = resolveOrder(live[k], stops);
                if (o === j) here++;
                else if (o >= 0 && o < j && o > bestOrder) bestOrder = o;
            }
            var eta = -1;
            if (here > 0) eta = 0;
            else if (bestOrder >= 0) eta = Math.max(1, Math.round((j - bestOrder) * 100 / 60));
            rows.push({
                index: j,
                stop: stops[j],
                here: here,
                eta: eta,
                user: j === userIdx
            });
        }
        return rows;
    }

    var api = {
        distance: distance,
        fmtDistance: fmtDistance,
        etaMinutes: etaMinutes,
        typeLabel: typeLabel,
        destination: destination,
        parseFeed: parseFeed,
        resolveOrder: resolveOrder,
        buildBoard: buildBoard
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    } else {
        root.BusCore = api;
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
