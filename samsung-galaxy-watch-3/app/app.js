/*
 * Автобусы Красноярска — Tizen-версия для Samsung Galaxy Watch 3.
 *
 * Экран 360×360, круглый. Данные — публичный фид Bustime, каталог маршрутов
 * и остановок лежит в data/ (тот же, что в Android-версии).
 */
(function () {
    'use strict';

    var W = 360, H = 360, CX = 180, R = 176;
    var SLOT = [26, 34, 44, 34, 26];
    var GAP = 6;
    var SCALE = [0.72, 0.86, 1.0, 0.86, 0.72];
    var LIST_TOP = 98;
    var FEED = 'https://busti.me/ajax/bus/?bus_id=';

    var state = {
        screen: 'routes',      // routes | stops | menu
        routes: [],
        stopsById: {},
        route: null,
        dir: 0,
        sel: 0,
        stopSel: 0,
        board: [],
        buses: 0,
        lat: 56.0106,
        lon: 92.8526,
        locLabel: '—',
        manual: null,
        menu: [],
        menuSel: 0,
        alert: null,           // {routeId, dir, stopIndex, stopName}
        timer: null,
        lastFeed: null
    };

    var C = window.BusCore;

    var elList = document.getElementById('list');
    var elHint = document.getElementById('hint');
    var elClock = document.getElementById('clock');
    var elLoc = document.getElementById('loc');
    var elToast = document.getElementById('toast');

    // ------------------------------------------------------------- utils

    function clamp(v, a, b) { return v < a ? a : (v > b ? b : v); }

    function routeColor(t) {
        if (t === 1) return '#45b4ce';
        if (t === 2) return '#3ddc97';
        if (t === 3) return '#b08cff';
        return '#ffb020';
    }

    function toast(text, ms) {
        elToast.textContent = text;
        elToast.hidden = false;
        setTimeout(function () { elToast.hidden = true; }, ms || 1600);
    }

    function vibrate() {
        try { navigator.vibrate([150, 120, 150]); } catch (e) { /* ignore */ }
        try {
            if (window.tizen && tizen.feedback) tizen.feedback.play('VIBRATION');
        } catch (e) { /* ignore */ }
    }

    function notify(title, text) {
        try {
            if (window.tizen && tizen.notification) {
                var n = new tizen.StatusNotification('SIMPLE', title, { content: text });
                tizen.notification.post(n);
            }
        } catch (e) { /* ignore */ }
    }

    // ------------------------------------------------------------- список

    function rowWidth(centerY) {
        var far = Math.abs(centerY - CX);
        var half = Math.sqrt(Math.max(0, R * R - far * far));
        return Math.min(330, 2 * (half - 9));
    }

    function renderList(items) {
        elList.innerHTML = '';
        var top = LIST_TOP;
        for (var i = 0; i < items.length; i++) {
            var h = SLOT[i] - GAP;
            var cy = top + SLOT[i] / 2;
            var w = rowWidth(cy);
            var s = SCALE[i];

            var row = document.createElement('div');
            row.className = 'row' + (items[i].selected ? ' selected' : '') +
                (items[i].user ? ' user' : '');
            row.style.top = (top + GAP / 2) + 'px';
            row.style.height = h + 'px';
            row.style.width = w + 'px';
            row.style.fontSize = (13 * s) + 'px';
            row.innerHTML = items[i].html;
            row.dataset.index = i;

            elList.appendChild(row);
            top += SLOT[i];
        }
        renderScrollbar(items.length);
    }

    var scrollEl = null;

    function renderScrollbar(n) {
        if (n <= 5) {
            if (scrollEl) { scrollEl.remove(); scrollEl = null; }
            return;
        }
        if (!scrollEl) {
            scrollEl = document.createElement('div');
            scrollEl.className = 'scrollbar';
            scrollEl.innerHTML = '<div></div>';
            document.getElementById('app').appendChild(scrollEl);
        }
        var total = document.getElementById('list').children.length;
        var frac = total ? 1 : 0;
        scrollEl.style.display = frac ? 'block' : 'none';
    }

    function renderRoutes() {
        state.screen = 'routes';
        elHint.textContent = 'колёсико — выбор · нажатие — открыть';
        var start = clamp(state.sel - 2, 0, Math.max(0, state.routes.length - 5));
        var items = [];
        for (var i = 0; i < 5; i++) {
            var r = state.routes[start + i];
            if (!r) break;
            var selected = (start + i) === state.sel;
            items.push({
                selected: selected,
                html: '<div class="badge" style="width:' + (28 * SCALE[i]) + 'px;height:' +
                    (28 * SCALE[i]) + 'px;background:' + routeColor(r.type) +
                    ';font-size:' + (12 * SCALE[i]) + 'px">' + escapeHtml(r.name) + '</div>' +
                    '<div class="title">' + escapeHtml(r.title || C.typeLabel(r.type)) +
                    '<div class="sub">' + C.typeLabel(r.type) + '</div></div>'
            });
        }
        renderList(items);
    }

    function renderStops() {
        state.screen = 'stops';
        elHint.textContent = '⇄ направление · удержание — меню остановки';
        var rows = state.board;
        var start = clamp(state.stopSel - 2, 0, Math.max(0, rows.length - 5));
        var items = [];
        for (var i = 0; i < 5; i++) {
            var row = rows[start + i];
            if (!row) break;
            var selected = (start + i) === state.stopSel;
            var right;
            if (row.here > 0) {
                var dots = '';
                for (var d = 0; d < Math.min(row.here, 3); d++) dots += '<span class="dot"></span>';
                right = '<div class="dots">' + dots + '</div>';
            }
            else if (row.eta > 0) right = '<div class="right">~' + row.eta + '′</div>';
            else right = '<div class="right" style="color:#5d7387">—</div>';

            var left = row.user
                ? '<div class="mark">вы</div>'
                : '<div class="idx">' + (row.index + 1) + '</div>';

            items.push({
                selected: selected,
                user: row.user,
                html: left + '<div class="title">' + escapeHtml(row.stop.name) + '</div>' + right
            });
        }
        renderList(items);
    }

    function renderMenu() {
        state.screen = 'menu';
        elHint.textContent = 'колёсико — выбор · нажатие — действие';
        var items = [];
        for (var i = 0; i < state.menu.length; i++) {
            items.push({
                selected: i === state.menuSel,
                html: '<div class="title" style="text-align:center;margin:0">' +
                    escapeHtml(state.menu[i].label) + '</div>'
            });
        }
        renderList(items);
    }

    function escapeHtml(s) {
        return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
            return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
        });
    }

    // ------------------------------------------------------------- данные

    function loadJson(url) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', url, true);
            xhr.onreadystatechange = function () {
                if (xhr.readyState === 4) {
                    if (xhr.status === 200 || xhr.status === 0) {
                        try { resolve(JSON.parse(xhr.responseText)); }
                        catch (e) { reject(e); }
                    } else {
                        reject(new Error('HTTP ' + xhr.status));
                    }
                }
            };
            xhr.onerror = function () { reject(new Error('network')); };
            xhr.send();
        });
    }

    function initData() {
        Promise.all([loadJson('data/routes.json'), loadJson('data/route_stops.json')])
            .then(function (res) {
                var all = res[0] || [];
                state.routes = all.filter(function (r) {
                    return r.type === 0 || r.type === 1 || r.type === 2 || r.type === 3;
                }).sort(function (a, b) {
                    if (a.type !== b.type) return a.type - b.type;
                    var na = parseInt(a.name, 10), nb = parseInt(b.name, 10);
                    if (!isNaN(na) && !isNaN(nb) && na !== nb) return na - nb;
                    return String(a.name).localeCompare(String(b.name));
                });
                state.stopsById = res[1] || {};
                renderRoutes();
            })
            .catch(function () {
                elList.innerHTML = '';
                toast('Нет данных каталога', 3000);
            });
    }

    // ------------------------------------------------------------- фид

    function openRoute(index) {
        var route = state.routes[index];
        if (!route) return;
        state.route = route;
        state.dir = 0;
        state.stopSel = 0;
        state.board = [];
        renderStops();
        refreshFeed();
        if (state.timer) clearInterval(state.timer);
        state.timer = setInterval(refreshFeed, 10000);
    }

    function stopsFor(route, dir) {
        var rs = state.stopsById[String(route.id)];
        if (!rs) return [];
        var list = dir === 0 ? rs.dir0 : rs.dir1;
        if (!list || !list.length) list = rs.dir0;
        return list || [];
    }

    function refreshFeed() {
        if (!state.route) return;
        var route = state.route;
        var xhr = new XMLHttpRequest();
        xhr.open('GET', FEED + route.id + '&t=' + Date.now(), true);
        xhr.timeout = 8000;
        xhr.onreadystatechange = function () {
            if (xhr.readyState !== 4) return;
            if (xhr.status !== 200) { toast('Нет связи', 1500); return; }
            var vehicles = C.parseFeed(xhr.responseText);
            state.buses = vehicles.filter(function (v) { return !v.sleep; }).length;
            state.lastFeed = vehicles;
            state.board = C.buildBoard(stopsFor(route, state.dir), vehicles, state.dir, state.lat, state.lon);
            if (state.screen === 'stops') renderStops();
            checkAlert(vehicles);
        };
        xhr.send();
    }

    // ------------------------------------------------------------- ожидание

    function checkAlert(vehicles) {
        var a = state.alert;
        if (!a) return;
        if (a.routeId !== state.route.id || a.dir !== state.dir) return;
        var stops = stopsFor(state.route, state.dir);
        for (var i = 0; i < vehicles.length; i++) {
            var v = vehicles[i];
            if (v.dir !== a.dir || v.sleep) continue;
            if (C.resolveOrder(v, stops) === a.stopIndex) {
                triggerAlert(a);
                return;
            }
        }
    }

    function triggerAlert(a) {
        vibrate();
        notify('Автобус прибывает',
            'Маршрут №' + state.route.name + ' на остановке «' + a.stopName + '»');
        toast('Автобус на остановке!\n' + a.stopName, 2600);
        state.alert = null;
    }

    // ------------------------------------------------------------- навигация

    function move(delta) {
        if (state.screen === 'routes') {
            state.sel = clamp(state.sel + delta, 0, state.routes.length - 1);
            renderRoutes();
        } else if (state.screen === 'stops') {
            state.stopSel = clamp(state.stopSel + delta, 0, state.board.length - 1);
            renderStops();
        } else if (state.screen === 'menu') {
            state.menuSel = clamp(state.menuSel + delta, 0, state.menu.length - 1);
            renderMenu();
        }
        vibrateTick();
    }

    function vibrateTick() {
        try {
            if (window.tizen && tizen.feedback) tizen.feedback.play('HAPTIC');
        } catch (e) { /* ignore */ }
    }

    function activate() {
        if (state.screen === 'routes') {
            openRoute(state.sel);
        } else if (state.screen === 'stops') {
            openStopMenu();
        } else if (state.screen === 'menu') {
            var item = state.menu[state.menuSel];
            if (item && item.action) item.action();
        }
    }

    function goBack() {
        if (state.screen === 'menu') {
            state.screen = 'stops';
            renderStops();
        } else if (state.screen === 'stops') {
            state.screen = 'routes';
            state.route = null;
            if (state.timer) { clearInterval(state.timer); state.timer = null; }
            renderRoutes();
        } else {
            try { tizen.application.getCurrentApplication().exit(); } catch (e) { /* ignore */ }
        }
    }

    function openStopMenu() {
        var row = state.board[state.stopSel];
        if (!row) return;
        var isHere = state.manual &&
            C.distance(state.manual.lat, state.manual.lon, row.stop.lat, row.stop.lon) < 60;
        var armed = state.alert && state.alert.stopIndex === row.index &&
            state.route && state.alert.routeId === state.route.id;

        state.menu = [
            {
                label: isHere ? '✓ Я здесь (убрать)' : 'Я здесь',
                action: function () {
                    if (isHere) {
                        state.manual = null;
                        state.locLabel = 'GPS';
                    } else {
                        state.manual = { lat: row.stop.lat, lon: row.stop.lon };
                        state.locLabel = 'РУЧ';
                        state.lat = row.stop.lat;
                        state.lon = row.stop.lon;
                    }
                    state.board = C.buildBoard(stopsFor(state.route, state.dir),
                        state.lastFeed || [], state.dir, state.lat, state.lon);
                    state.screen = 'stops';
                    renderStops();
                }
            },
            {
                label: armed ? '✓ Жду (снять)' : 'Ждать автобус',
                action: function () {
                    if (armed) {
                        state.alert = null;
                        toast('Ожидание снято', 1500);
                    } else {
                        state.alert = {
                            routeId: state.route.id,
                            dir: state.dir,
                            stopIndex: row.index,
                            stopName: row.stop.name
                        };
                        toast('Жду автобус\n' + row.stop.name, 2000);
                    }
                    state.screen = 'stops';
                    renderStops();
                }
            },
            {
                label: 'Назад',
                action: function () { state.screen = 'stops'; renderStops(); }
            }
        ];
        state.menuSel = 0;
        renderMenu();
    }

    function toggleDirection() {
        if (!state.route) return;
        var rs = state.stopsById[String(state.route.id)];
        if (!rs || !rs.dir0.length || !rs.dir1.length) return;
        state.dir = state.dir === 0 ? 1 : 0;
        state.stopSel = 0;
        state.board = C.buildBoard(stopsFor(state.route, state.dir), state.lastFeed || [], state.dir, state.lat, state.lon);
        renderStops();
        refreshFeed();
    }

    // ------------------------------------------------------------- ввод

    document.addEventListener('rotarydetent', function (ev) {
        try {
            var dir = ev.detail && ev.detail.direction;
            move(dir === 'CCW' ? -1 : 1);
        } catch (e) { /* ignore */ }
    });

    window.addEventListener('tizenhwkey', function (ev) {
        if (ev.keyName === 'back') goBack();
        else if (ev.keyName === 'menu') activate();
    });

    var touch = null;

    elList.addEventListener('touchstart', function (e) {
        var t = e.touches[0];
        touch = { x: t.clientX, y: t.clientY, time: Date.now(), moved: false };
        state._pressTimer = setTimeout(function () {
            if (touch && !touch.moved && state.screen === 'stops') openStopMenu();
        }, 600);
    }, false);

    elList.addEventListener('touchmove', function (e) {
        if (!touch) return;
        var t = e.touches[0];
        if (Math.abs(t.clientX - touch.x) > 8 || Math.abs(t.clientY - touch.y) > 8) {
            touch.moved = true;
            clearTimeout(state._pressTimer);
        }
    }, false);

    elList.addEventListener('touchend', function (e) {
        clearTimeout(state._pressTimer);
        if (!touch) return;
        var t = e.changedTouches[0];
        var dx = t.clientX - touch.x;
        var dy = t.clientY - touch.y;

        if (dx > 40 && Math.abs(dx) > Math.abs(dy) * 1.2) {
            goBack();
        } else if (!touch.moved) {
            var el = e.target.closest ? e.target.closest('.row') : null;
            if (el) {
                var idx = parseInt(el.dataset.index, 10);
                if (state.screen === 'routes') {
                    var start = clamp(state.sel - 2, 0, Math.max(0, state.routes.length - 5));
                    state.sel = start + idx;
                    openRoute(state.sel);
                } else if (state.screen === 'stops') {
                    var s = clamp(state.stopSel - 2, 0, Math.max(0, state.board.length - 5));
                    state.stopSel = s + idx;
                    renderStops();
                } else if (state.screen === 'menu') {
                    state.menuSel = idx;
                    renderMenu();
                    activate();
                }
            }
        }
        touch = null;
    }, false);

    // Разделитель управления направлением: верхняя треть экрана, тап по центру
    document.addEventListener('click', function (e) {
        if (state.screen !== 'stops') return;
        var y = e.clientY || 0;
        if (y < 92 && e.target === elList) toggleDirection();
    }, false);

    // ------------------------------------------------------------- часы/гео

    function tickClock() {
        var d = new Date();
        var hh = ('0' + d.getHours()).slice(-2);
        var mm = ('0' + d.getMinutes()).slice(-2);
        elClock.textContent = hh + ':' + mm;
    }

    function initGeo() {
        if (!navigator.geolocation) {
            state.locLabel = 'ЦЕНТР';
            updateLocLabel();
            return;
        }
        navigator.geolocation.watchPosition(function (pos) {
            if (state.manual) return;
            state.lat = pos.coords.latitude;
            state.lon = pos.coords.longitude;
            state.locLabel = 'GPS';
            updateLocLabel();
            if (state.screen === 'stops' && state.route) {
                state.board = buildBoard(stopsFor(state.route, state.dir),
                    state.lastFeed || [], state.dir);
                renderStops();
            }
        }, function () {
            state.locLabel = state.manual ? 'РУЧН' : 'ЦЕНТР';
            updateLocLabel();
        }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 10000 });
    }

    function updateLocLabel() {
        elLoc.textContent = state.locLabel;
        elLoc.className = state.locLabel === 'РУЧН' ? 'manual' : (state.locLabel === 'GPS' ? 'good' : '');
    }

    // ------------------------------------------------------------- старт

    tickClock();
    setInterval(tickClock, 15000);
    initData();
    initGeo();
})();
