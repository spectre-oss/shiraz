// SPDX-License-Identifier: GPL-3.0-only
// Strictly client-side, zero-telemetry per-app PWA manifest + icon + APK storage
// for Shiraz browser runner (/run/). Keeps each Android app installable as a
// standalone PWA titled "<App Name> - Shiraz" with its original Android app icon.

const PWA_APPS_CACHE = 'shiraz-pwa-apps-v1';
const APK_DB_NAME = 'shiraz-pwa-apks-v1';
const APK_STORE_NAME = 'apks';

export function sanitizeAppSlug(pkg) {
  const cleaned = String(pkg || '')
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 120);
  return cleaned || 'app';
}

export function formatAppPwaName(label, pkg) {
  const baseName = String(label || pkg || 'Shiraz').trim() || 'Shiraz';
  if (/\s[-–—]\s*shiraz$/i.test(baseName) || baseName.toLowerCase() === 'shiraz') {
    return baseName;
  }
  return `${baseName} - Shiraz`;
}

function base64ToUint8Array(b64) {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function canvasToPngBlob(canvas) {
  return new Promise(resolve => {
    canvas.toBlob(blob => resolve(blob || null), 'image/png');
  });
}

async function blobToDataUrl(blob) {
  return await new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(reader.error || new Error('Icon read failed'));
    reader.readAsDataURL(blob);
  });
}

async function loadImageFromBlob(blob) {
  const url = URL.createObjectURL(blob);
  try {
    const img = new Image();
    img.decoding = 'async';
    await new Promise((resolve, reject) => {
      img.onload = resolve;
      img.onerror = () => reject(new Error('Image decode failed'));
      img.src = url;
    });
    return img;
  } finally {
    URL.revokeObjectURL(url);
  }
}

function drawContainedIcon(img, size, padRatio = 0) {
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  if (!ctx) return null;
  ctx.clearRect(0, 0, size, size);
  const pad = Math.round(size * padRatio);
  const box = size - pad * 2;
  const srcW = img.naturalWidth || img.width || size;
  const srcH = img.naturalHeight || img.height || size;
  const scale = Math.min(box / srcW, box / srcH);
  const drawW = Math.max(1, Math.round(srcW * scale));
  const drawH = Math.max(1, Math.round(srcH * scale));
  const dx = Math.round((size - drawW) / 2);
  const dy = Math.round((size - drawH) / 2);
  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = 'high';
  ctx.drawImage(img, dx, dy, drawW, drawH);
  return canvas;
}

/**
 * Decode an APK's extracted PNG/WebP icon bytes into crisp 192x192 and 512x512 PNG blobs.
 * Returns null if no raster icon is present or decoding fails.
 */
export async function renderAppIconPngs(iconBase64, iconMime) {
  if (!iconBase64 || typeof document === 'undefined') return null;
  try {
    const bytes = base64ToUint8Array(iconBase64);
    const sourceBlob = new Blob([bytes], { type: iconMime || 'image/png' });
    const img = await loadImageFromBlob(sourceBlob);
    const canvas192 = drawContainedIcon(img, 192, 0);
    const canvas512 = drawContainedIcon(img, 512, 0);
    if (!canvas192 || !canvas512) return null;
    const [blob192, blob512] = await Promise.all([
      canvasToPngBlob(canvas192),
      canvasToPngBlob(canvas512)
    ]);
    if (!blob192 || !blob512) return null;
    const dataUrl = await blobToDataUrl(blob192);
    return { blob192, blob512, dataUrl };
  } catch {
    return null;
  }
}

export function applyDocumentAppIdentity({ title, iconDataUrl, manifestHref, themeColor }) {
  if (typeof document === 'undefined') return;
  if (title) document.title = title;
  if (manifestHref) {
    let link = document.querySelector('link[rel="manifest"]');
    if (!link) {
      link = document.createElement('link');
      link.rel = 'manifest';
      document.head.append(link);
    }
    link.href = manifestHref;
  }
  if (iconDataUrl) {
    let iconLink = document.querySelector('link[rel="icon"][data-shiraz-app-icon]');
    if (!iconLink) {
      iconLink = document.createElement('link');
      iconLink.rel = 'icon';
      iconLink.type = 'image/png';
      iconLink.setAttribute('data-shiraz-app-icon', '1');
      document.head.append(iconLink);
    }
    iconLink.href = iconDataUrl;

    let appleLink = document.querySelector('link[rel="apple-touch-icon"][data-shiraz-app-icon]');
    if (!appleLink) {
      appleLink = document.createElement('link');
      appleLink.rel = 'apple-touch-icon';
      appleLink.setAttribute('data-shiraz-app-icon', '1');
      document.head.append(appleLink);
    }
    appleLink.href = iconDataUrl;
  }
  if (themeColor) {
    let meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'theme-color';
      document.head.append(meta);
    }
    meta.content = themeColor;
  }
}

/**
 * Publish a per-APK Web App Manifest + PNG icons to same-origin Cache Storage so
 * the service worker serves real HTTPS manifest/icon URLs for native PWA install.
 */
export async function publishPerAppPwa({ base, pkg, label, iconBase64, iconMime }) {
  const slug = sanitizeAppSlug(pkg);
  const pwaName = formatAppPwaName(label, pkg);
  const origin = window.location.origin;
  const runPrefix = `${base}/run`;
  const appAssetBase = `${runPrefix}/pwa/${encodeURIComponent(slug)}`;
  const startUrl = `${runPrefix}/?standalone=1&app=${encodeURIComponent(slug)}`;
  const manifestPath = `${appAssetBase}/manifest.webmanifest`;
  const icon192Path = `${appAssetBase}/icon-192.png`;
  const icon512Path = `${appAssetBase}/icon-512.png`;

  const rendered = await renderAppIconPngs(iconBase64, iconMime);
  const iconDataUrl = rendered?.dataUrl || `${base}/icon-192.png`;

  const manifestObj = {
    id: `${runPrefix}/?app=${encodeURIComponent(slug)}`,
    name: pwaName,
    short_name: pwaName,
    description: `Run ${label || pkg} locally in Shiraz with pithings/unkernel. Zero backend, zero telemetry.`,
    start_url: startUrl,
    scope: `${base}/`,
    display: 'standalone',
    display_override: ['standalone'],
    orientation: 'any',
    background_color: '#000000',
    theme_color: '#000000',
    icons: rendered
      ? [
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: 'icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: 'icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
        ]
      : [
          { src: `${base}/icon-192.png`, sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: `${base}/icon-192.png`, sizes: '192x192', type: 'image/png', purpose: 'maskable' },
          { src: `${base}/icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'any' },
          { src: `${base}/icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'maskable' }
        ]
  };

  let cachedInSw = false;
  if (typeof caches !== 'undefined') {
    try {
      const cache = await caches.open(PWA_APPS_CACHE);
      const corpHeaders = contentType => ({
        'Content-Type': contentType,
        'Cache-Control': 'no-cache',
        'Cross-Origin-Resource-Policy': 'same-origin'
      });
      const puts = [
        cache.put(
          new Request(`${origin}${manifestPath}`),
          new Response(JSON.stringify(manifestObj, null, 2), {
            status: 200,
            headers: corpHeaders('application/manifest+json; charset=utf-8')
          })
        )
      ];
      if (rendered) {
        puts.push(
          cache.put(
            new Request(`${origin}${icon192Path}`),
            new Response(rendered.blob192, { status: 200, headers: corpHeaders('image/png') })
          ),
          cache.put(
            new Request(`${origin}${icon512Path}`),
            new Response(rendered.blob512, { status: 200, headers: corpHeaders('image/png') })
          )
        );
      }
      await Promise.all(puts);
      cachedInSw = Boolean(navigator.serviceWorker?.controller);
    } catch {
      cachedInSw = false;
    }
  }

  let manifestHref = `${manifestPath}?v=${Date.now()}`;
  if (!cachedInSw) {
    // Fallback before SW takes control: inline data URL manifest with absolute start_url & icons.
    const inlineManifest = {
      ...manifestObj,
      id: `${origin}${manifestObj.id}`,
      start_url: `${origin}${startUrl}`,
      scope: `${origin}${base}/`,
      icons: rendered
        ? [
            { src: rendered.dataUrl, sizes: '192x192', type: 'image/png', purpose: 'any' },
            { src: rendered.dataUrl, sizes: '192x192', type: 'image/png', purpose: 'maskable' }
          ]
        : [
            { src: `${origin}${base}/icon-192.png`, sizes: '192x192', type: 'image/png', purpose: 'any' },
            { src: `${origin}${base}/icon-512.png`, sizes: '512x512', type: 'image/png', purpose: 'any' }
          ]
    };
    manifestHref = `data:application/manifest+json;charset=utf-8,${encodeURIComponent(JSON.stringify(inlineManifest))}`;
  }

  applyDocumentAppIdentity({
    title: pwaName,
    iconDataUrl,
    manifestHref,
    themeColor: '#000000'
  });

  return {
    slug,
    pwaName,
    iconDataUrl,
    manifestUrl: manifestPath,
    standaloneUrl: startUrl
  };
}

function openApkDb() {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB unavailable'));
      return;
    }
    const req = indexedDB.open(APK_DB_NAME, 1);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(APK_STORE_NAME)) {
        db.createObjectStore(APK_STORE_NAME, { keyPath: 'slug' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error || new Error('IndexedDB open failed'));
  });
}

/** Persist an APK locally in IndexedDB so an installed per-app PWA launches directly. */
export async function saveAppApkRecord({ pkg, label, fileName, iconDataUrl, bytes }) {
  try {
    const slug = sanitizeAppSlug(pkg);
    const db = await openApkDb();
    try {
      await new Promise((resolve, reject) => {
        const tx = db.transaction(APK_STORE_NAME, 'readwrite');
        tx.objectStore(APK_STORE_NAME).put({
          slug,
          pkg,
          label: label || pkg,
          fileName: fileName || `${slug}.apk`,
          iconDataUrl: iconDataUrl || '',
          bytes: bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes),
          savedAt: Date.now()
        });
        tx.oncomplete = resolve;
        tx.onerror = () => reject(tx.error || new Error('IndexedDB write failed'));
      });
    } finally {
      db.close();
    }
  } catch {
    // Non-fatal: quota or private browsing may disallow IndexedDB persistence.
  }
}

export async function loadAppApkRecord(slugOrPkg) {
  try {
    const slug = sanitizeAppSlug(slugOrPkg);
    const db = await openApkDb();
    try {
      return await new Promise((resolve, reject) => {
        const tx = db.transaction(APK_STORE_NAME, 'readonly');
        const req = tx.objectStore(APK_STORE_NAME).get(slug);
        req.onsuccess = () => resolve(req.result || null);
        req.onerror = () => reject(req.error || new Error('IndexedDB read failed'));
      });
    } finally {
      db.close();
    }
  } catch {
    return null;
  }
}

export async function clearSavedAppPwas() {
  if (typeof caches !== 'undefined') {
    await caches.delete(PWA_APPS_CACHE).catch(() => {});
  }
  if (typeof indexedDB !== 'undefined') {
    await new Promise(resolve => {
      const req = indexedDB.deleteDatabase(APK_DB_NAME);
      req.onsuccess = resolve;
      req.onerror = resolve;
      req.onblocked = resolve;
    });
  }
}
