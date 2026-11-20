// Unit tests for web/core.js; mirror the Kotlin RouteCatalogTest / RouteHistoryTest.
// Run: node --test tests/web/*.test.js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  Transport, parseTsv, resolve, recordHistory, encodeHistory, decodeHistory,
} from '../../web/core.js';

const data = [
  'kind\tnumber\turl\tdescription',
  'bus\t12\thttps://2gis.kz/almaty/route/9430017310195922\tМедеу',
  'bus\t79\thttps://evil.example/a\tподмена',
  'bus\t0\thttps://2gis.kz/almaty/route/1\tноль',
].join('\n');

test('known bus opens exact 2gis route', () => {
  const target = resolve(Transport.BUS, '12', parseTsv(data));
  assert.equal(target.isDirect, true);
  assert.equal(target.url, 'https://2gis.kz/almaty/route/9430017310195922');
  assert.equal(target.description, 'Медеу');
});

test('unknown bus opens search, not a different route', () => {
  const target = resolve(Transport.BUS, '100', parseTsv(data));
  assert.equal(target.isDirect, false);
  assert.equal(target.url, 'https://2gis.kz/almaty/search/%D0%B0%D0%B2%D1%82%D0%BE%D0%B1%D1%83%D1%81%20100');
});

test('bus and trolleybus numbers do not conflict', () => {
  const target = resolve(Transport.TROLLEYBUS, '12', parseTsv(data));
  assert.equal(target.isDirect, false);
  assert.match(decodeURIComponent(target.url), /троллейбус 12$/);
});

test('rejects invalid numbers and untrusted urls', () => {
  assert.equal(resolve(Transport.BUS, '12a', []), null);
  assert.equal(resolve(Transport.BUS, '', []), null);
  assert.equal(resolve(Transport.BUS, '1234', []), null);
  assert.equal(parseTsv(data).length, 1);
});

test('rejects zero and numbers with a leading zero', () => {
  assert.equal(resolve(Transport.BUS, '0', []), null);
  assert.equal(resolve(Transport.BUS, '05', []), null);
  assert.equal(resolve(Transport.TROLLEYBUS, '00', []), null);
  assert.notEqual(resolve(Transport.BUS, '10', []), null);
  assert.notEqual(resolve(Transport.BUS, '105', []), null);
});

test('parses the real catalog shipped with the Android app', () => {
  const tsv = readFileSync(new URL('../../app/src/main/assets/routes.tsv', import.meta.url), 'utf8');
  const routes = parseTsv(tsv);
  assert.ok(routes.length >= 6);
  assert.ok(resolve(Transport.BUS, '12', routes).url.endsWith('9430017310195922'));
});

const bus = (number) => ({ transport: Transport.BUS, number });
const trolley = (number) => ({ transport: Transport.TROLLEYBUS, number });

test('moves reused route to front without duplicates', () => {
  assert.deepEqual(recordHistory([bus('12'), bus('3')], bus('3')), [bus('3'), bus('12')]);
});

test('keeps exactly six latest routes across both modes', () => {
  let history = [];
  for (let i = 1; i <= 10; i++) history = recordHistory(history, bus(String(i)));
  history = recordHistory(history, trolley('10'));
  assert.equal(history.length, 6);
  assert.deepEqual(history[0], trolley('10'));
  assert.deepEqual(history[1], bus('10'));
  assert.ok(!history.some((r) => r.transport === Transport.BUS && r.number === '4'));
});

test('stored history survives roundtrip and uses the Android format', () => {
  const original = [bus('12'), trolley('12'), bus('79')];
  assert.equal(encodeHistory(original), 'b12,t12,b79');
  assert.deepEqual(decodeHistory(encodeHistory(original)), original);
});

test('ignores malformed, zero and duplicate history values', () => {
  assert.deepEqual(decodeHistory('b12,invalid,t8,b12,t9999,b0,t05'), [bus('12'), trolley('8')]);
  assert.deepEqual(decodeHistory(null), []);
  assert.deepEqual(recordHistory([], bus('abc')), []);
  assert.deepEqual(recordHistory([bus('12')], bus('0')), [bus('12')]);
});
