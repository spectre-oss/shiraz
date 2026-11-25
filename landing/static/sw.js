// SPDX-License-Identifier: GPL-3.0-only
// Minimal, zero-telemetry, same-origin service worker for Shiraz:
//   1. PWA installability shell caching for lightweight navigation/manifest files.
//   2. Cross-Origin Isolation (COOP/COEP) headers ONLY on /run/ so SharedArrayBuffer
//      and multi-core SMP work on static hosts like GitHub Pages without affecting /.
//   3. Immutable HTTP Range block caching for versioned Shiraz-Browser-Runtime-*.{erofs,img}
//      disk images so repeat browser sessions reuse downloaded 4 KiB blocks locally.

const SHELL_CACHE = 'shiraz-pwa-shell-v2';
const BLOCK_CACHE = 'shiraz-runtime-blocks-v1';
const PWA_APPS_CACHE = 'shiraz-pwa-apps-v1';
const KEEP_CACHES = new Set([SHELL_CACHE, BLOCK_CACHE, PWA_APPS_CACHE]);
const RUNTIME_IMAGE_RE = /\/Shiraz-Browser-Runtime-[^/]+\.(?:erofs|img)$/i;
const PWA_APP_ASSET_RE = /^(.*\/)?run\/pwa\/[^/]+\/(manifest\.webmanifest|icon-(?:192|512)\.png)$/i;
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

function sanitizeAppSlug(raw) {
  return String(raw || '')
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 120);
}

async function rewriteRunManifestForApp(response, url) {
  const slug = sanitizeAppSlug(url.searchParams.get('app'));
  if (!slug || !response || !response.ok) return response;
  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('text/html')) return response;
  const html = await response.text();
  const basePrefix = url.pathname.replace(/run(?:\/(?:index\.html)?)?$/, '');
  const manifestUrl = `${basePrefix}run/pwa/${encodeURIComponent(slug)}/manifest.webmanifest`;
  const rewritten = html.replace(
    /<link\s+rel=["']manifest["']\s+href=["'][^"']*["']\s*\/?>/i,
    `<link rel="manifest" href="${manifestUrl}">`
  );
  const headers = new Headers(response.headers);
  headers.delete('content-length');
  return new Response(rewritten, {
    status: response.status,
    statusText: response.statusText,
    headers
  });
}

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

  // Serve locally cached per-APK PWA manifests and original app PNG icons same-origin.
  const pwaMatch = PWA_APP_ASSET_RE.exec(url.pathname);
  if (pwaMatch) {
    const basePrefix = pwaMatch[1] || '/';
    const assetName = pwaMatch[2].toLowerCase();
    event.respondWith(
      caches
        .open(PWA_APPS_CACHE)
        .then(cache => cache.match(new Request(`${url.origin}${url.pathname}`)))
        .then(hit => {
          if (hit) return withSameOriginCorp(hit);
          const fallbackPath =
            assetName === 'manifest.webmanifest'
              ? `${basePrefix}run-manifest.webmanifest`
              : `${basePrefix}${assetName}`;
          return fetch(new Request(`${url.origin}${fallbackPath}`)).then(withSameOriginCorp);
        })
        .catch(() => Response.error())
    );
    return;
  }

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
      .then(async response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(SHELL_CACHE).then(cache => cache.put(request, copy)).catch(() => {});
        }
        if (!isolate) return response;
        const rewritten = await rewriteRunManifestForApp(response, url);
        return withRunIsolationHeaders(rewritten);
      })
      .catch(async () => {
        const cached =
          (await caches.match(request)) ||
          (isolate ? await caches.match(new Request(`${url.origin}${url.pathname}`)) : null);
        if (!cached) return Response.error();
        if (!isolate) return cached;
        const rewritten = await rewriteRunManifestForApp(cached, url);
        return withRunIsolationHeaders(rewritten);
      })
  );
});
