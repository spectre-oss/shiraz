<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onNavigate } from '$app/navigation';
  // The headline animates on arrival, so its face must not swap in mid-flight.
  import displayFont from '@fontsource-variable/bricolage-grotesque/files/bricolage-grotesque-latin-wdth-normal.woff2?url';
  let { children } = $props();
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
<svelte:head><link rel="preload" href={displayFont} as="font" type="font/woff2" crossorigin="anonymous" /></svelte:head>
{@render children()}
