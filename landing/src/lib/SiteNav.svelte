<script>
  // SPDX-License-Identifier: GPL-3.0-only
  // One navigation for every route, so each page reaches the others the same way.
  // On phones Builder and Compare fold away; the hero and the footer still link to both.
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import GitHubLink from '#lib/GitHubLink.svelte';
  const home = resolve('/');
  const links = [
    { href: resolve('/run/'), label: 'Run', path: resolve('/run/'), narrow: 'nav-run' },
    { href: home + '#builder', label: 'Builder', path: null, narrow: 'nav-builder' },
    { href: resolve('/compatibility/'), label: 'Compatibility', path: resolve('/compatibility/') },
    { href: resolve('/compare/'), label: 'Compare', path: resolve('/compare/'), narrow: 'nav-compare' }
  ];
</script>
<nav aria-label="Main navigation">
  {#each links as link}<a href={link.href} class={link.narrow} aria-current={link.path && page.url.pathname === link.path ? 'page' : undefined}>{link.label}</a>{/each}
  <GitHubLink />
</nav>
