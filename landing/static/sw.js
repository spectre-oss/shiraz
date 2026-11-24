// SPDX-License-Identifier: GPL-3.0-only
// Minimal, zero-telemetry, same-origin service worker for Shiraz PWA installability.
// Uses network-first for lightweight shell documents only and never touches cross-origin
// URLs, HTTP Range requests, or large runtime / WebAssembly binaries.

const CACHE_NAME = 'shiraz-pwa-shell-v1';
const SKIP_EXTENSIONS = /\.(?:wasm|erofs|img|ext4|appimage|flatpak-base|mp4|apk)$/i;

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches
      .keys()
      .then(keys => Promise.all(keys.filter(key => key !== CACHE_NAME).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const { request } = event;
  if (request.method !== 'GET' || request.headers.has('range')) return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (SKIP_EXTENSIONS.test(url.pathname)) return;
  if (request.mode !== 'navigate' && !url.pathname.endsWith('.webmanifest')) return;

  event.respondWith(
    fetch(request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, copy)).catch(() => {});
        }
        return response;
      })
      .catch(() => caches.match(request))
  );
});
