<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onNavigate } from '$app/navigation';
  import { resolve } from '$app/paths';
  import { page } from '$app/state';
  // The headline animates on arrival, so its face must not swap in mid-flight.
  import displayFont from '@fontsource-variable/bricolage-grotesque/files/bricolage-grotesque-latin-wdth-normal.woff2?url';
  let { children } = $props();
  const base = resolve('/').replace(/\/$/, '');
  const isRunRoute = $derived(page.url.pathname === `${base}/run` || page.url.pathname === `${base}/run/`);
  const manifestHref = $derived(`${base}/${isRunRoute ? 'run-manifest.webmanifest' : 'manifest.webmanifest'}`);
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
