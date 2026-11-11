/* Тесты чистой логики Tizen-порта (Node, без DOM). */
'use strict';
const fs = require('fs');
const path = require('path');
const C = require('./app/core.js');

let passed = 0, failed = 0;
function check(name, ok) {
    if (ok) { passed++; console.log('  ok   ' + name); }
    else { failed++; console.log('  FAIL ' + name); }
}

const stopsAll = JSON.parse(fs.readFileSync(path.join(__dirname, 'app/data/route_stops.json'), 'utf8'));
const feedPath = fs.existsSync(path.join(__dirname, 'fixtures/bus_534.json'))
    ? path.join(__dirname, 'fixtures/bus_534.json')
    : path.join(__dirname, '../../tests/fixtures/bus_534.json');
const feedFixture = JSON.parse(fs.readFileSync(feedPath, 'utf8'));
const feedText = JSON.stringify(feedFixture);
const dir0 = stopsAll['534'].dir0;
const dir1 = stopsAll['534'].dir1;

// геодезия
check('distance 1km', Math.abs(C.distance(56.0106, 92.8526, 56.0196, 92.8526) - 1000) < 25);
check('format 850m', C.fmtDistance(850) === '850 м');
check('format 1234m', C.fmtDistance(1234) === '1,2 км');
check('eta 1km', C.etaMinutes(1000) === 4);
check('type labels', C.typeLabel(1) === 'Троллейбус' && C.typeLabel(2) === 'Трамвай');

// фид
const vehicles = C.parseFeed(feedText);
check('feed parses 17', vehicles.length === 17);
check('feed has sleep flag', vehicles.some(v => v.sleep));
check('feed has stop order', vehicles.some(v => v.order >= 0));
check('feed coords in city', vehicles.every(v => v.lat > 55.9 && v.lat < 56.2 && v.lon > 92.5 && v.lon < 93.2));
check('broken feed empty', C.parseFeed('not json').length === 0);

// доска
const now = 1794275200;
const b0 = C.buildBoard(dir0, vehicles, 0, 56.0106, 92.8526, now);
check('board rows = stops', b0.length === dir0.length);
check('board has bus here', b0.some(r => r.here > 0));
check('board has eta', b0.some(r => r.eta > 0));
check('board marks user stop', b0.some(r => r.user));
const b1 = C.buildBoard(dir1, vehicles, 1, 56.0106, 92.8526, now);
check('board dir1', b1.length === dir1.length);

// один автобус ровно на остановке 5
const one = [{
    dir: 0, sleep: false, ts: now, order: 5, stopId: dir0[5].id,
    lat: dir0[5].lat, lon: dir0[5].lon
}];
const single = C.buildBoard(dir0, one, 0, 56.0106, 92.8526, now);
check('bus exactly here', single[5].here === 1 && single[5].eta === 0);
check('eta after bus', single[6].eta >= 1);
check('stale bus dropped', C.buildBoard(dir0, [{ dir: 0, sleep: false, ts: now - 1000, order: 3 }], 0, 56.0106, 92.8526, now).every(r => r.here === 0));

// направление
check('destination dir0', C.destination({ title: 'A ↔ B' }, 0) === 'B');
check('destination dir1', C.destination({ title: 'A ↔ B' }, 1) === 'A');

console.log('\nTIZEN CORE: ' + passed + ' passed, ' + failed + ' failed');
if (failed) process.exit(1);
