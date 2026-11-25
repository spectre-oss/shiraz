<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onMount } from 'svelte';
  import { browser } from '$app/environment';
  import { onNavigate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  // The headline animates on arrival, so its face must not swap in mid-flight.
  import displayFont from '@fontsource-variable/bricolage-grotesque/files/bricolage-grotesque-latin-wdth-normal.woff2?url';
  let { children } = $props();
  const base = resolve('/').replace(/\/$/, '');
  const isRunRoute = $derived(page.route.id === '/run' || /(?:^|\/)run\/?$/.test(page.url.pathname));
  const appSlug = $derived(
    browser && isRunRoute
      ? (new URLSearchParams(window.location.search).get('app') || '')
          .trim()
          .replace(/[^A-Za-z0-9._-]+/g, '_')
          .replace(/^_+|_+$/g, '')
          .slice(0, 120)
      : ''
  );
  const manifestHref = $derived(
    isRunRoute
      ? appSlug
        ? `${base}/run/pwa/${encodeURIComponent(appSlug)}/manifest.webmanifest`
        : `${base}/run-manifest.webmanifest`
      : `${base}/manifest.webmanifest`
  );
  // Register minimal zero-telemetry same-origin SW so /run/ opens with Cross-Origin
  // Isolation (SMP multi-core) and runtime block caching on first navigation.
  onMount(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register(`${base}/sw.js`, { scope: `${base}/` }).catch(() => {});
    }
  });
  // Cross-fade between the two routes where the browser supports it.
  onNavigate((navigation) => {
    if (!document.startViewTransition || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    if (navigation.from?.url.pathname === navigation.to?.url.pathname) return;
    return new Promise((resolve) => {
      document.startViewTransition(async () => {
        resolve();
        await navigation.complete;
      });
    });
  });
</script>
<svelte:head>
  <link rel="manifest" href={manifestHref} />
  <link rel="preload" href={displayFont} as="font" type="font/woff2" crossorigin="anonymous" />
</svelte:head>
{@render children()}
