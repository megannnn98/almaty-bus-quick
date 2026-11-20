// Offline support. Stale-while-revalidate: answer instantly from cache (a bus stop
// often has a weak signal) and refresh the cache in the background, so a new
// version shows up on the next launch.
const CACHE = 'bus-quick-v1';
const ASSETS = [
  './',
  'index.html',
  'style.css',
  'app.js',
  'core.js',
  'routes.tsv',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'icons/apple-touch-icon.png',
  'icons/favicon-32.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) {
    return; // 2ГИС links and anything else go straight to the network
  }
  event.respondWith(caches.open(CACHE).then(async (cache) => {
    const cached = await cache.match(request, { ignoreSearch: true });
    const fresh = fetch(request)
      .then((response) => {
        if (response.ok) {
          cache.put(request, response.clone());
        }
        return response;
      })
      .catch(() => cached);
    return cached ?? fresh;
  }));
});
