const BASE = new URL(self.registration.scope).pathname
const CACHE_NAME = `expense-tracker${BASE.replace(/\//g, '_')}-__BUILD_VERSION__`
const INDEX = `${BASE}index.html`
const APP_SHELL = [
  BASE,
  INDEX,
  `${BASE}manifest.webmanifest`,
  `${BASE}favicon.svg`,
  `${BASE}favicon.ico`,
  `${BASE}icon-192.png`,
  `${BASE}icon-512.png`,
  `${BASE}icon-512-maskable.png`,
  `${BASE}apple-touch-icon.png`,
]

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  )
})

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)

  // API selalu lewat network (jangan pernah di-cache).
  if (url.pathname.startsWith(`${BASE}api/`)) {
    return
  }

  // Navigasi: network-first, fallback ke app shell cache (offline).
  if (event.request.mode === 'navigate') {
    event.respondWith(
      fetch(event.request)
        .then((response) => {
          const clone = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(INDEX, clone))
          return response
        })
        .catch(() => caches.match(INDEX)),
    )
    return
  }

  // Asset (JS/CSS ber-nama-hash, gambar): network-first supaya versi terbaru
  // selalu dimuat; cache hanya sebagai fallback offline.
  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok && url.origin === self.location.origin) {
          const clone = response.clone()
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone))
        }
        return response
      })
      .catch(() => caches.match(event.request)),
  )
})
