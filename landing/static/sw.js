// SPDX-License-Identifier: GPL-3.0-only
// Minimal, zero-telemetry, same-origin service worker for Shiraz:
//   1. PWA installability shell caching for lightweight navigation/manifest files.
//   2. Cross-Origin Isolation (COOP/COEP) headers ONLY on /run/ so SharedArrayBuffer
//      and multi-core SMP work on static hosts like GitHub Pages without affecting /.
//   3. Immutable HTTP Range block caching for versioned Shiraz-Browser-Runtime-*.{erofs,img}
//      disk images so repeat browser sessions reuse downloaded 4 KiB blocks locally.

const SHELL_CACHE = 'shiraz-pwa-shell-v2';
const BLOCK_CACHE = 'shiraz-runtime-blocks-v1';
const KEEP_CACHES = new Set([SHELL_CACHE, BLOCK_CACHE]);
const RUNTIME_IMAGE_RE = /\/Shiraz-Browser-Runtime-[^/]+\.(?:erofs|img)$/i;
const RUN_ROUTE_RE = /(?:^|\/)run(?:\/(?:index\.html)?)?$/;
const SKIP_SHELL_EXTENSIONS = /\.(?:wasm|erofs|img|ext4|appimage|flatpak-base|mp4|apk)$/i;

let activeRuntimePath = '';

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches
      .keys()
      .then(keys => Promise.all(keys.filter(key => !KEEP_CACHES.has(key)).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('message', event => {
  if (event.data?.type === 'CLEAR_RUNTIME_CACHE') {
    activeRuntimePath = '';
    const clearing = caches.delete(BLOCK_CACHE).catch(() => {});
    if (event.ports?.[0]) {
      event.waitUntil(clearing.then(() => event.ports[0].postMessage({ ok: true })));
    } else {
      event.waitUntil(clearing);
    }
  }
});

function withRunIsolationHeaders(response) {
  if (!response || response.status === 0 || (response.status >= 300 && response.status < 400)) {
    return response;
  }
  const headers = new Headers(response.headers);
  headers.set('Cross-Origin-Opener-Policy', 'same-origin');
  headers.set('Cross-Origin-Embedder-Policy', 'require-corp');
  headers.set('Cross-Origin-Resource-Policy', 'same-origin');
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

function withSameOriginCorp(response) {
  if (!response || response.status === 0 || (response.status >= 300 && response.status < 400)) {
    return response;
  }
  const headers = new Headers(response.headers);
  headers.set('Cross-Origin-Resource-Policy', 'same-origin');
  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

async function evictOtherRuntimeVersions(cache, currentPath) {
  try {
    const keys = await cache.keys();
    await Promise.all(
      keys
        .filter(req => new URL(req.url).pathname !== currentPath)
        .map(req => cache.delete(req))
    );
  } catch {
    // Ignore quota/storage cleanup errors.
  }
}

async function handleRuntimeBlockRange(request, url) {
  const range = request.headers.get('range');
  const cacheKey = new Request(`${url.origin}${url.pathname}?__range=${encodeURIComponent(range)}`);
  try {
    const cache = await caches.open(BLOCK_CACHE);
    const hit = await cache.match(cacheKey);
    if (hit) {
      const headers = new Headers(hit.headers);
      headers.set('Cross-Origin-Resource-Policy', 'same-origin');
      return new Response(hit.body, {
        status: 206,
        statusText: 'Partial Content',
        headers
      });
    }

    const response = await fetch(request);
    if (response.status === 206) {
      // Cache.put() rejects HTTP 206 responses by spec; store body as 200 with
      // original Content-Range/ETag headers and reconstruct 206 on cache hit.
      const toStore = response.clone();
      toStore
        .arrayBuffer()
        .then(buf => {
          const storeHeaders = new Headers(toStore.headers);
          storeHeaders.set('Cross-Origin-Resource-Policy', 'same-origin');
          return cache.put(cacheKey, new Response(buf, { status: 200, headers: storeHeaders }));
        })
        .catch(() => {});
      if (activeRuntimePath !== url.pathname) {
        activeRuntimePath = url.pathname;
        evictOtherRuntimeVersions(cache, url.pathname);
      }
    }
    return withSameOriginCorp(response);
  } catch {
    return fetch(request);
  }
}

self.addEventListener('fetch', event => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;

  // Cache immutable HTTP Range blocks for versioned Shiraz browser runtime images.
  if (request.headers.has('range')) {
    if (RUNTIME_IMAGE_RE.test(url.pathname)) {
      event.respondWith(handleRuntimeBlockRange(request, url));
    }
    return;
  }

  if (SKIP_SHELL_EXTENSIONS.test(url.pathname)) return;
  if (request.mode !== 'navigate' && !url.pathname.endsWith('.webmanifest')) return;

  const isolate = request.mode === 'navigate' && RUN_ROUTE_RE.test(url.pathname);
  event.respondWith(
    fetch(request)
      .then(response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(SHELL_CACHE).then(cache => cache.put(request, copy)).catch(() => {});
        }
        return isolate ? withRunIsolationHeaders(response) : response;
      })
      .catch(async () => {
        const cached = await caches.match(request);
        if (!cached) return Response.error();
        return isolate ? withRunIsolationHeaders(cached) : cached;
      })
  );
});
