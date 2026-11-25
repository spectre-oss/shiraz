<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { tick } from 'svelte';
  import { resolve } from '$app/paths';
  import Footer from '#lib/Footer.svelte';
  import SiteNav from '#lib/SiteNav.svelte';
  import AndroidCoverage from '#lib/AndroidCoverage.svelte';
  import { applications } from '#lib/compatibility.js';
  import inventory from '#lib/android-inventory.json';
  import ndk from '#lib/ndk-inventory.json';
  import hostPlatforms from '#lib/host-platforms.json';
  import { ROBOTS_INDEX, compatibilityFaqs, compatibilityJsonLd, serializeJsonLd } from '#lib/seo.js';
  import '../../styles.css';
  let dialog;
  let opener;
  let selectedApp = $state(null);
  // Safari does not focus a button on click, so the dialog cannot restore focus by itself.
  async function openLimitations(app, trigger) {
    opener = trigger;
    selectedApp = app;
    await tick();
    if (!dialog.open) dialog.showModal();
  }
  let query = $state('');
  let status = $state('All');
  let framework = $state('All');
  let letter = $state('All');
  const alphabet = ['#', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'];
  const initial = name => (/^[0-9]/.test(name) ? '#' : name[0].toUpperCase());
  const availableLetters = new Set(applications.map(app => initial(app.name)));
  const frameworks = [...new Set(applications.map(app => app.framework))].sort();
  const engineCount = new Set(frameworks.map(name => name.replace(/\s*\+.*$/, ''))).size;
  const statuses = ['All', 'Partial', 'Blocked', 'Historical'];
  const statusCounts = {
    Partial: applications.filter(app => app.status === 'Partial').length,
    Blocked: applications.filter(app => app.status === 'Blocked').length,
    Historical: applications.filter(app => app.status === 'Historical').length
  };
  const frameworkCards = frameworks
    .map(name => {
      const items = applications.filter(app => app.framework === name);
      return {
        name,
        total: items.length,
        partial: items.filter(a => a.status === 'Partial').length,
        blocked: items.filter(a => a.status === 'Blocked').length,
        historical: items.filter(a => a.status === 'Historical').length
      };
    })
    .sort((a, b) => b.total - a.total || a.name.localeCompare(b.name));
  const classes = inventory.inventory;
  const classRatio = classes.present / classes.reference;
  const ndkRatio = ndk.symbolsPresent / ndk.symbolsReference;
  const ringRadius = 26;
  const ringCircumference = 2 * Math.PI * ringRadius;
  const results = $derived(
    applications
      .filter(
        app =>
          (status === 'All' || app.status === status) &&
          (framework === 'All' || app.framework === framework) &&
          (letter === 'All' || initial(app.name) === letter) &&
          `${app.name} ${app.framework} ${app.observed} ${app.limitations}`.toLowerCase().includes(query.trim().toLowerCase())
      )
      .sort((a, b) => a.name.localeCompare(b.name, 'en', { numeric: true }))
  );
  const pct = (part, whole) => (whole ? (100 * part) / whole : 0).toFixed(1) + '%';
</script>

<svelte:head>
  <title>App compatibility &amp; Android™ API 35 coverage — Shiraz</title>
  <meta name="description" content="Explore recorded Shiraz app compatibility across 14 apps, Linux/macOS/Windows host OS matrices, 4,445 API 35 framework classes, 15 NDK libraries (887 C symbols), and public test reports." />
  <meta name="robots" content={ROBOTS_INDEX} />
  <link rel="canonical" href="https://shiraz.foo/compatibility/" />
  <meta property="og:title" content="App compatibility &amp; Android™ API 35 coverage — Shiraz" />
  <meta property="og:description" content="Recorded app walkthroughs, Linux/macOS/Windows host backend progress, 4,445 API 35 classes, and 15/15 NDK libraries (887 C symbols)." />
  <meta property="og:url" content="https://shiraz.foo/compatibility/" />
  <meta property="og:type" content="website" />
  <meta property="og:locale" content="en_US" />
  <meta property="og:site_name" content="Shiraz" />
  <meta property="og:image" content="https://shiraz.foo/social-card.png" />
  <meta property="og:image:type" content="image/png" />
  <meta property="og:image:width" content="1200" />
  <meta property="og:image:height" content="630" />
  <meta property="og:image:alt" content="Shiraz app compatibility and Android API 35 coverage across Linux, macOS, and Windows." />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="App compatibility &amp; Android™ API 35 coverage — Shiraz" />
  <meta name="twitter:description" content="Recorded app runs, 3-OS host progress, 4,445 API 35 classes, and 15/15 NDK libraries with explicit limits." />
  <meta name="twitter:image" content="https://shiraz.foo/social-card.png" />
  <meta name="twitter:image:alt" content="Shiraz app compatibility and Android API 35 coverage across Linux, macOS, and Windows." />
  {@html serializeJsonLd(
    compatibilityJsonLd({
      version: inventory.runtimeVersion,
      updated: inventory.updated,
      appCount: applications.length,
      classPresent: classes.present,
      classReference: classes.reference,
      ndkLibs: ndk.librariesPresent,
      ndkSymbols: ndk.symbolsPresent
    })
  )}
</svelte:head>
<a class="skip-link" href="#compatibility-main">Skip to app results</a>
<header id="top" class="compat-header wrap">
  <a class="brand" href={resolve('/')}>
    <img src={resolve('/').replace(/\/$/, '') + '/spectre.svg'} width="42" height="42" alt="" />
    <span>shiraz</span>
  </a>
  <SiteNav />
</header>
<main id="compatibility-main" class="compat-main wrap">
  <div class="compat-hero">
    <nav class="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        <li><a href={resolve('/')}>Home</a></li>
        <li aria-current="page">Compatibility</li>
      </ol>
    </nav>
    <header class="compat-intro">
      <p class="eyebrow">Progress snapshot · v{inventory.runtimeVersion} · updated {inventory.updated}</p>
      <h1>
        A little clarity.
        <br />
        <span>Before you build.</span>
      </h1>
      <p>
        Track how far Shiraz has come across Android™ API 35 classes, NDK libraries, three desktop OS backends, and real apps.
        <br />
        Every metric stays separate, with unfinished parts left in.
      </p>
      <p class="compat-invitation">
        You can add to the experiment, too. <a href="https://github.com/spectre-oss/shiraz/edit/main/landing/src/lib/compatibility.json">Add your app’s test results</a> through a pull request.
      </p>
    </header>

    <div class="progress-overview" aria-label="Progress across Shiraz compatibility layers">
      <a class="progress-tile tile-saffron" href="#api-inventory">
        <div class="tile-top">
          <svg class="tile-ring" viewBox="0 0 64 64" width="60" height="60" aria-hidden="true">
            <circle class="ring-track" cx="32" cy="32" r={ringRadius} />
            <circle
              class="ring-fill"
              cx="32"
              cy="32"
              r={ringRadius}
              stroke-dasharray={ringCircumference}
              stroke-dashoffset={ringCircumference * (1 - classRatio)}
            />
          </svg>
          <div class="tile-metric">
            <strong>{classes.present.toLocaleString('en')}</strong>
            <span>of {classes.reference.toLocaleString('en')} API 35 classes</span>
          </div>
        </div>
        <h3>Java framework surface</h3>
        <p>100% of Android 15 class names across 49 packages compiled; behavior grows subsystem by subsystem.</p>
      </a>

      <a class="progress-tile tile-teal" href="#ndk-inventory">
        <div class="tile-top">
          <svg class="tile-ring" viewBox="0 0 64 64" width="60" height="60" aria-hidden="true">
            <circle class="ring-track" cx="32" cy="32" r={ringRadius} />
            <circle
              class="ring-fill"
              cx="32"
              cy="32"
              r={ringRadius}
              stroke-dasharray={ringCircumference}
              stroke-dashoffset={ringCircumference * (1 - ndkRatio)}
            />
          </svg>
          <div class="tile-metric">
            <strong>{ndk.librariesPresent}/{ndk.librariesTotal}</strong>
            <span>{ndk.symbolsPresent.toLocaleString('en')} C symbols exported</span>
          </div>
        </div>
        <h3>NDK native libraries</h3>
        <p>All 15 tracked API 35 shared libraries audited with <code>nm</code> on Linux &amp; macOS; Windows DLLs in CI.</p>
      </a>

      <a class="progress-tile tile-emerald" href="#host-platforms">
        <div class="tile-bars" aria-hidden="true">
          {#each hostPlatforms.platforms as platform (platform.id)}
            {@const total = platform.progress.verified + platform.progress.partial + platform.progress.open}
            <div class="mini-os-row">
              <span>{platform.name}</span>
              <span class="mini-os-track">
                <i class="seg-verified" style:width={pct(platform.progress.verified, total)}></i>
                <i class="seg-partial" style:width={pct(platform.progress.partial, total)}></i>
              </span>
              <em>{platform.progress.verified}/{total}</em>
            </div>
          {/each}
        </div>
        <h3>Three desktop OS backends</h3>
        <p>Linux primary (6/6 layers); macOS ARM64 (46 verified, 19 partial) &amp; Windows (32 verified, 2 partial).</p>
      </a>

      <a class="progress-tile tile-rose" href="#app-results-title">
        <div class="tile-app-top">
          <div class="tile-metric">
            <strong>{applications.length}</strong>
            <span>apps across {engineCount} engines</span>
          </div>
          <div class="mini-app-bar" aria-hidden="true">
            <i class="seg-verified" style:width={pct(statusCounts.Partial, applications.length)}></i>
            <i class="seg-blocked" style:width={pct(statusCounts.Blocked, applications.length)}></i>
            <i class="seg-historical" style:width={pct(statusCounts.Historical, applications.length)}></i>
          </div>
        </div>
        <h3>Recorded app runs</h3>
        <p>{statusCounts.Partial} Partial · {statusCounts.Blocked} Blocked · {statusCounts.Historical} Historical — including live OsmAnd+ &amp; Telegram on macOS.</p>
      </a>
    </div>

    <aside class="compat-caveat">
      <strong>Experimental, app by app.</strong> These are recorded observations, not fresh tests or full compatibility guarantees. Results depend on the APK version, architecture, runtime, and test environment. Read the report before relying on a feature.
    </aside>
    <nav class="compat-section-nav" aria-label="Compatibility sections">
      <a href="#app-results-title">App results</a>
      <a href="#android-tracker">Android coverage tracker</a>
      <a href="#host-platforms">Host OS matrix</a>
      <a href="#subsystem-support">Subsystems</a>
      <a href="#api-inventory">API 35 classes</a>
      <a href="#ndk-inventory">NDK libraries</a>
    </nav>
  </div>

  <section aria-labelledby="app-results-title" class="compat-results">
    <h2 id="app-results-title">Find an app</h2>
    <div class="compat-summary" aria-label="Recorded app status counts">
      <p><strong>{applications.length}</strong><span>Recorded apps</span></p>
      {#each statuses.slice(1) as item}
        <p><strong>{statusCounts[item]}</strong><span>{item}</span></p>
      {/each}
    </div>

    <div class="compat-visual-strip">
      <div class="status-meter-wrap">
        <div
          class="stacked-bar"
          role="img"
          aria-label={`Recorded app status split: ${statusCounts.Partial} Partial, ${statusCounts.Blocked} Blocked, ${statusCounts.Historical} Historical out of ${applications.length} apps`}
        >
          <span class="seg-verified" style:width={pct(statusCounts.Partial, applications.length)}></span>
          <span class="seg-blocked" style:width={pct(statusCounts.Blocked, applications.length)}></span>
          <span class="seg-historical" style:width={pct(statusCounts.Historical, applications.length)}></span>
        </div>
        <div class="compat-legend">
          <span><i class="dot dot-verified" aria-hidden="true"></i><b>Partial ({statusCounts.Partial})</b> — some flows verified</span>
          <span><i class="dot dot-blocked" aria-hidden="true"></i><b>Blocked ({statusCounts.Blocked})</b> — a known blocker</span>
          <span><i class="dot dot-historical" aria-hidden="true"></i><b>Historical ({statusCounts.Historical})</b> — earlier observations</span>
        </div>
      </div>

      <div class="framework-chips" role="group" aria-label="Quick filter by app engine">
        {#each frameworkCards as item (item.name)}
          <button
            type="button"
            class="framework-chip"
            aria-pressed={framework === item.name}
            onclick={() => (framework = framework === item.name ? 'All' : item.name)}
          >
            <span class="chip-name">{item.name}</span>
            <span class="chip-pips" aria-hidden="true">
              {#each Array(item.partial) as _}<i class="pip pip-verified"></i>{/each}
              {#each Array(item.blocked) as _}<i class="pip pip-blocked"></i>{/each}
              {#each Array(item.historical) as _}<i class="pip pip-historical"></i>{/each}
            </span>
            <span class="chip-count">{item.total}</span>
          </button>
        {/each}
      </div>
    </div>

    <div class="compat-filters">
      <label class="compat-search">
        Search apps
        <input type="search" bind:value={query} placeholder="App name, feature, or framework" />
      </label>
      <div class="compat-framework">
        <label for="framework-filter">Framework</label>
        <select id="framework-filter" bind:value={framework}>
          <option>All</option>
          {#each frameworks as item}<option>{item}</option>{/each}
        </select>
      </div>
    </div>
    <div class="compat-statuses" role="group" aria-label="Filter by recorded status">
      {#each statuses as item}
        <button
          type="button"
          class:partial={item === 'Partial'}
          class:blocked={item === 'Blocked'}
          class:historical={item === 'Historical'}
          aria-pressed={status === item}
          onclick={() => (status = item)}
        >
          {#if item !== 'All'}<span class="status-shape" aria-hidden="true"></span>{/if}{item}
        </button>
      {/each}
    </div>
    <p class="compat-count" role="status" aria-live="polite">{results.length} of {applications.length} apps shown</p>
    <nav class="compat-alphabet" aria-label="Filter apps by first letter">
      <button type="button" aria-pressed={letter === 'All'} onclick={() => (letter = 'All')}>All</button>
      {#each alphabet as item}
        <button type="button" disabled={!availableLetters.has(item)} aria-pressed={letter === item} onclick={() => (letter = item)}>{item}</button>
      {/each}
    </nav>
    <p class="compat-table-hint" id="app-table-hint">Apps are sorted A–Z. Scroll sideways on smaller screens.</p>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="compat-table-scroll" role="region" aria-label="App compatibility results" aria-describedby="app-table-hint" tabindex="0">
      <table class="compat-table">
        <caption class="visually-hidden">Recorded app compatibility, sorted alphabetically</caption>
        <thead>
          <tr>
            <th scope="col">App</th>
            <th scope="col">Framework</th>
            <th scope="col">Status</th>
            <th scope="col">Observed behavior &amp; limitations</th>
            <th scope="col">Evidence</th>
          </tr>
        </thead>
        <tbody>
          {#each results as app (app.name)}
            <tr>
              <th scope="row">{app.name}</th>
              <td>
                {app.framework}
                {#if app.version}<small class="app-meta">v{app.version}</small>{/if}
              </td>
              <td>
                <span class="compat-badge" class:blocked={app.status === 'Blocked'} class:historical={app.status === 'Historical'}>
                  <span class="status-shape" aria-hidden="true"></span>{app.status}
                </span>
              </td>
              <td>
                <p class="compat-observed">{app.observed}</p>
                <button class="limitations-link" type="button" aria-haspopup="dialog" onclick={event => openLimitations(app, event.currentTarget)}>Known limitations</button>
              </td>
              <td>
                <a class="compat-report" href={app.url} aria-label={`Read ${app.name} test report`}>
                  Report
                  <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg>
                </a>
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    {#if results.length === 0}
      <div class="compat-empty">
        <h3>No matching apps</h3>
        <p>Try a different search or reset the filters.</p>
        <button
          type="button"
          onclick={() => {
            query = '';
            status = 'All';
            framework = 'All';
            letter = 'All';
          }}
        >
          Reset filters
        </button>
      </div>
    {/if}
  </section>
  <AndroidCoverage />
  <section class="faq-block faq-subpage" aria-labelledby="compat-faq-title">
    <p class="eyebrow">Compatibility questions</p>
    <h2 id="compat-faq-title">How to read these numbers.</h2>
    {#each compatibilityFaqs as item (item.q)}
      <details class="source-build faq-item">
        <summary>{item.q}<span class="disclosure-icon" aria-hidden="true"><svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h14" /><path d="M12 5v14" /></svg></span></summary>
        <div class="build-content"><p>{item.a}</p></div>
      </details>
    {/each}
  </section>
  <section class="compat-contribute">
    <h2>Your app isn’t listed?</h2>
    <p>
      That means we have no result here, not that it works or fails.
      <br />
      Help us record a reproducible test. Each app is one entry in our
      <a href="https://github.com/spectre-oss/shiraz/blob/main/landing/src/lib/compatibility.json">shared JSON catalog</a>.
    </p>
    <a class="button primary" href="https://github.com/spectre-oss/shiraz/blob/main/docs/AI_CONTRIBUTING.md">
      Contribute a test report
      <svg class="icon" viewBox="0 0 24 24" aria-hidden="true"><path d="m4.5 19.5 15-15m0 0H8.25m11.25 0v11.25" /></svg>
    </a>
  </section>
</main>
<dialog class="limitations-dialog" bind:this={dialog} onclose={() => opener?.focus()} aria-labelledby="limitations-title" aria-describedby="limitations-description">
  {#if selectedApp}
    <p class="dialog-app-name">{selectedApp.name}</p>
    <h2 id="limitations-title">Known limitations</h2>
    <p id="limitations-description">{selectedApp.limitations}</p>
    <div class="dialog-actions">
      <a href={selectedApp.url}>Read test report</a>
      <button type="button" onclick={() => dialog.close()}>Close</button>
    </div>
  {/if}
</dialog>
<Footer />
