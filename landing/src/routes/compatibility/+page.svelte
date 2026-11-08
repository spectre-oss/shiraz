<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { tick } from 'svelte';
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import AndroidCoverage from '#lib/AndroidCoverage.svelte';
  import { applications } from '#lib/compatibility.js';
  import '../../styles.css';
  let dialog;
  let selectedApp = $state(null);
  async function openLimitations(app) {
    selectedApp = app;
    await tick();
    if (!dialog.open) dialog.showModal();
  }
  let query = $state('');
  let status = $state('All');
  let framework = $state('All');
  let letter = $state('All');
  const alphabet = ['#', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'];
  const initial = name => /^[0-9]/.test(name) ? '#' : name[0].toUpperCase();
  const availableLetters = new Set(applications.map(app => initial(app.name)));
  const frameworks = [...new Set(applications.map(app => app.framework))].sort();
  const statuses = ['All','Partial','Blocked','Historical'];
  const results = $derived(applications.filter(app =>
    (status === 'All' || app.status === status) &&
    (framework === 'All' || app.framework === framework) &&
    (letter === 'All' || initial(app.name) === letter) &&
    `${app.name} ${app.framework} ${app.observed} ${app.limitations}`.toLowerCase().includes(query.trim().toLowerCase())
  ).sort((a,b) => a.name.localeCompare(b.name, 'en', {numeric:true})));
</script>
<svelte:head>
  <title>App compatibility — Shiraz</title>
  <meta name="description" content="Explore recorded Shiraz app compatibility: tested features, known limitations, and public test reports." />
  <link rel="canonical" href="https://spectre-oss.github.io/shiraz/compatibility/" />
  <meta property="og:title" content="Shiraz app compatibility" />
  <meta property="og:description" content="What has been tested, what is partial, and what is still blocked." />
  <meta property="og:url" content="https://spectre-oss.github.io/shiraz/compatibility/" />
  <meta property="og:type" content="website" />
  <meta property="og:image" content="https://spectre-oss.github.io/shiraz/social-card.png" />
  <meta name="twitter:card" content="summary_large_image" />
</svelte:head>
<a class="skip-link" href="#compatibility-main">Skip to app results</a>
<header id="top" class="compat-header wrap"><a class="brand" href={resolve('/')}><img src={resolve('/').replace(/\/$/, '') + '/spectre.svg'} width="42" height="42" alt="" /><span>shiraz</span></a><nav aria-label="Main navigation"><a href={resolve('/')}>Home</a><a href={resolve('/') + '#builder'}>Open the builder <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg></a></nav></header>
<main id="compatibility-main" class="compat-main wrap">
  <div class="compat-hero">
  <header class="compat-intro"><h1>A little clarity.<br><span>Before you build.</span></h1><p>Explore what has been observed running with Shiraz.<br>Real test notes, with the unfinished parts left in.</p><p class="compat-invitation">You can add to the experiment, too. <a href="https://github.com/spectre-oss/shiraz/edit/main/landing/src/lib/compatibility.json">Add your app’s test results</a> through a pull request.</p></header>
  <div class="compat-summary" aria-label="Recorded app status counts">
    <p><strong>{applications.length}</strong><span>Recorded apps</span></p>
    {#each statuses.slice(1) as item}<p><strong>{applications.filter(app => app.status === item).length}</strong><span>{item}</span></p>{/each}
  </div>
  <aside class="compat-caveat"><strong>Experimental, app by app.</strong> These are recorded observations, not fresh tests or full compatibility guarantees. Results depend on the APK version, architecture, runtime, and test environment. Read the report before relying on a feature.</aside>
  <nav class="compat-section-nav" aria-label="Compatibility sections"><a href="#app-results-title">App results</a><a href="#android-tracker">Android coverage tracker</a></nav>
  </div>
  <section aria-labelledby="app-results-title" class="compat-results">
    <h2 id="app-results-title">Find an app</h2>
    <div class="compat-filters"><label class="compat-search">Search apps<input type="search" bind:value={query} placeholder="App name, feature, or framework" /></label><div class="compat-framework"><label for="framework-filter">Framework</label><select id="framework-filter" bind:value={framework}><option>All</option>{#each frameworks as item}<option>{item}</option>{/each}</select></div></div>
    <div class="compat-statuses" role="group" aria-label="Filter by recorded status">{#each statuses as item}<button type="button" class:partial={item === 'Partial'} class:blocked={item === 'Blocked'} class:historical={item === 'Historical'} aria-pressed={status === item} onclick={() => status = item}>{#if item !== 'All'}<span class="status-shape" aria-hidden="true"></span>{/if}{item}</button>{/each}</div>
    <p class="compat-count" role="status" aria-live="polite">{results.length} of {applications.length} apps shown</p>
    <div class="compat-legend"><span><b>Partial</b> — some flows verified</span><span><b>Blocked</b> — a known blocker</span><span><b>Historical</b> — earlier observations</span></div>
    <nav class="compat-alphabet" aria-label="Filter apps by first letter">
      <button type="button" aria-pressed={letter === 'All'} onclick={() => letter = 'All'}>All</button>
      {#each alphabet as item}<button type="button" disabled={!availableLetters.has(item)} aria-pressed={letter === item} onclick={() => letter = item}>{item}</button>{/each}
    </nav>
    <p class="compat-table-hint" id="app-table-hint">Apps are sorted A–Z. Scroll sideways on smaller screens.</p>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="compat-table-scroll" role="region" aria-label="App compatibility results" aria-describedby="app-table-hint" tabindex="0">
      <table class="compat-table"><caption class="visually-hidden">Recorded app compatibility, sorted alphabetically</caption><thead><tr><th scope="col">App</th><th scope="col">Framework</th><th scope="col">Status</th><th scope="col">Observed behavior &amp; limitations</th><th scope="col">Evidence</th></tr></thead><tbody>
      {#each results as app (app.name)}
        <tr><th scope="row">{app.name}</th><td>{app.framework}</td><td><span class="compat-badge" class:blocked={app.status === 'Blocked'} class:historical={app.status === 'Historical'}><span class="status-shape" aria-hidden="true"></span>{app.status}</span></td><td><p class="compat-observed">{app.observed}</p><button class="limitations-link" type="button" aria-haspopup="dialog" onclick={() => openLimitations(app)}>Known limitations</button></td><td><a class="compat-report" href={app.url} aria-label={`Read ${app.name} test report`}>Report <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg></a></td></tr>
      {/each}
      </tbody></table>
    </div>
    {#if results.length === 0}<div class="compat-empty"><h3>No matching apps</h3><p>Try a different search or reset the filters.</p><button type="button" onclick={() => {query = ''; status = 'All'; framework = 'All'; letter = 'All';}}>Reset filters</button></div>{/if}
  </section>
  <AndroidCoverage />
  <section class="compat-contribute"><h2>Your app isn’t listed?</h2><p>That means we have no result here, not that it works or fails.<br>Help us record a reproducible test. Each app is one entry in our <a href="https://github.com/spectre-oss/shiraz/blob/main/landing/src/lib/compatibility.json">shared JSON catalog</a>.</p><a class="button primary" href="https://github.com/spectre-oss/shiraz/blob/main/docs/AI_CONTRIBUTING.md">Contribute a test report <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg></a></section>
</main>
<dialog class="limitations-dialog" bind:this={dialog} aria-labelledby="limitations-title" aria-describedby="limitations-description">
  {#if selectedApp}
    <p class="dialog-app-name">{selectedApp.name}</p>
    <h2 id="limitations-title">Known limitations</h2>
    <p id="limitations-description">{selectedApp.limitations}</p>
    <div class="dialog-actions"><a href={selectedApp.url}>Read test report</a><button type="button" onclick={() => dialog.close()}>Close</button></div>
  {/if}
</dialog>
<Footer />
