<script>
  // SPDX-License-Identifier: GPL-3.0-only
  // One navigation for every route, so each page reaches the others the same way.
  // On phones Builder and Compare fold away; the hero and the footer still link to both.
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import GitHubLink from '#lib/GitHubLink.svelte';
  const home = resolve('/');
  const runPath = resolve('/run/');
  const onRunRoute = $derived(page.route.id === '/run' || /(?:^|\/)run\/?$/.test(page.url.pathname));
  const links = [
    { href: runPath, label: 'Run', routeId: '/run', narrow: 'nav-run', reload: true },
    { href: home + '#builder', label: 'Builder', routeId: null, narrow: 'nav-builder', reload: false },
    { href: resolve('/compatibility/'), label: 'Compatibility', routeId: '/compatibility', reload: false },
    { href: resolve('/compare/'), label: 'Compare', routeId: '/compare', narrow: 'nav-compare', reload: false }
  ];
</script>
<nav aria-label="Main navigation">
  {#each links as link}<a href={link.href} class={link.narrow} data-sveltekit-reload={link.reload || onRunRoute ? '' : undefined} aria-current={link.routeId && page.route.id === link.routeId ? 'page' : undefined}>{link.label}</a>{/each}
  <GitHubLink />
</nav>
