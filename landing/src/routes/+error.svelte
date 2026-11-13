<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { page } from '$app/state';
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import '../styles.css';
  const home = resolve('/');
  const missing = $derived(page.status === 404);
</script>
<svelte:head>
  <title>{missing ? 'Page not found' : 'Something went wrong'} · Shiraz</title>
  <meta name="robots" content="noindex" />
</svelte:head>
<a class="skip-link" href="#main">Skip to content</a>
<header id="top" class="compat-header wrap"><a class="brand" href={home}><img src={home.replace(/\/$/, '') + '/spectre.svg'} width="42" height="42" alt="" /><span>shiraz</span></a><nav aria-label="Main navigation"><a href={home}>Home</a><a href={resolve('/compatibility/')}>Compatibility</a></nav></header>
<main id="main" class="not-found wrap">
  <p class="eyebrow">Error {page.status}</p>
  <h1>{missing ? 'This page is not here.' : 'This page did not load.'}</h1>
  <p>{missing ? 'The address may be mistyped, or the page may have moved.' : 'Reload to try again. If it keeps happening, report it on GitHub.'}</p>
  <div class="actions"><a class="button primary" href={home}>Go to the home page</a><a class="button secondary" href={resolve('/compatibility/')}>See app compatibility</a></div>
</main>
<Footer />
