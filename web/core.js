// Pure logic of the web app, ported from the Android RouteCatalog.kt / RouteHistory.kt.
// No DOM access here, so it runs both in the browser and under `node --test`.

export const Transport = Object.freeze({ BUS: 'bus', TROLLEYBUS: 'trolleybus' });

const SEARCH_WORD = { [Transport.BUS]: 'автобус', [Transport.TROLLEYBUS]: 'троллейбус' };
const NUMBER = /^[1-9][0-9]{0,2}$/; // 1..999, no leading zero
const DIRECT_URL = /^https:\/\/2gis\.kz\/almaty\/route\/[0-9]+$/;

export const HISTORY_LIMIT = 6;

export const isValidNumber = (number) => NUMBER.test(number);

/** Strict TSV (kind, number, url, description) so a row cannot inject an unchecked URL. */
export function parseTsv(text) {
  return text.split(/\r?\n/)
    .slice(1) // header
    .filter((line) => line.trim() !== '' && !line.startsWith('#'))
    .map((line) => line.split('\t'))
    .filter((cols) => cols.length === 4)
    .filter(([kind, number, url]) =>
      Object.values(Transport).includes(kind) && isValidNumber(number) && DIRECT_URL.test(url))
    .map(([transport, number, url, description]) => ({ transport, number, url, description }));
}

/** Unknown number gives a 2ГИС search, never a fake link to someone else's route. */
export function resolve(transport, number, routes) {
  if (!isValidNumber(number)) return null;
  const route = routes.find((r) => r.transport === transport && r.number === number);
  if (route) return { url: route.url, isDirect: true, description: route.description };
  const query = encodeURIComponent(`${SEARCH_WORD[transport]} ${number}`);
  return { url: `https://2gis.kz/almaty/search/${query}`, isDirect: false, description: 'Поиск в 2ГИС' };
}

const sameRoute = (a, b) => a.transport === b.transport && a.number === b.number;

const unique = (routes) =>
  routes.filter((route, index) => routes.findIndex((other) => sameRoute(other, route)) === index);

/** Newest first; unique by transport and number; at most HISTORY_LIMIT entries. */
export function recordHistory(existing, opened) {
  if (!isValidNumber(opened.number)) return existing.slice(0, HISTORY_LIMIT);
  return unique([opened, ...existing.filter((r) => !sameRoute(r, opened))]).slice(0, HISTORY_LIMIT);
}

/** Same compact format as the Android app: "b12,t8". */
export function encodeHistory(routes) {
  return routes.slice(0, HISTORY_LIMIT)
    .map((r) => `${r.transport === Transport.BUS ? 'b' : 't'}${r.number}`)
    .join(',');
}

export function decodeHistory(value) {
  const routes = (value ?? '').split(',').flatMap((token) => {
    const transport = { b: Transport.BUS, t: Transport.TROLLEYBUS }[token[0]];
    const number = token.slice(1);
    return transport && isValidNumber(number) ? [{ transport, number }] : [];
  });
  return unique(routes).slice(0, HISTORY_LIMIT);
}
