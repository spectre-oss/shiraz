<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import hostPlatforms from './host-platforms.json';
  import inventory from './android-inventory.json';
  import ndk from './ndk-inventory.json';
  import support from './android-support.json';
  let query = $state('');
  let status = $state('All');
  let scope = $state('All');
  const scopes = [...new Set(support.areas.map(area => area.scope))];
  const filtered = $derived(
    support.areas.filter(
      area =>
        (status === 'All' || area.status === status) &&
        (scope === 'All' || area.scope === scope) &&
        `${area.name} ${area.notes} ${area.verification}`.toLowerCase().includes(query.trim().toLowerCase())
    )
  );
  const supportCounts = {
    Partial: support.areas.filter(a => a.status === 'Partial').length,
    Unsupported: support.areas.filter(a => a.status === 'Unsupported').length,
    Unverified: support.areas.filter(a => a.status === 'Unverified').length
  };
  const scopeBreakdown = scopes.map(name => {
    const items = support.areas.filter(a => a.scope === name);
    return {
      name,
      total: items.length,
      partial: items.filter(a => a.status === 'Partial').length,
      unsupported: items.filter(a => a.status === 'Unsupported').length,
      unverified: items.filter(a => a.status === 'Unverified').length
    };
  });

  const classes = inventory.inventory;
  const percent = ((100 * classes.present) / classes.reference).toFixed(1);
  const ndkPercent = ((100 * ndk.symbolsPresent) / ndk.symbolsReference).toFixed(1);
  const states = ['PASS', 'FAIL', 'UNSUPPORTED', 'SKIPPED', 'TIMEOUT', 'NOT_RUN'];
  const snapshot = inventory.probeSnapshot;
  const total = state => snapshot.categories.reduce((sum, row) => sum + row.counts[state], 0);
  const report = path =>
    'https://github.com/spectre-oss/shiraz/blob/' + (path.startsWith('docs/') || path.startsWith('landing/') ? 'main' : inventory.sourceCommit) + '/' + path;
  const pct = (part, whole) => (whole ? (100 * part) / whole : 0).toFixed(1) + '%';

  const layerStateMeta = {
    ready: { label: 'Verified / Primary', cls: 'ready' },
    partial: { label: 'Partial / Opt-in', cls: 'partial' },
    progress: { label: 'In progress', cls: 'progress' }
  };

  // Group all 49 API 35 packages into 5 readable architectural domains for visual scanning.
  const domainSpecs = [
    {
      id: 'ui',
      name: 'UI, views & graphics',
      color: 'rose',
      packages: ['android.view', 'android.graphics', 'android.widget', 'android.text', 'android.opengl', 'android.animation', 'android.transition', 'android.inputmethodservice', 'android.window', 'android.appwidget']
    },
    {
      id: 'core',
      name: 'Core app, OS & data',
      color: 'saffron',
      packages: ['android.app', 'android.provider', 'android.icu', 'android.content', 'android.service', 'android.os', 'android.util', 'android.database', 'android.preference', 'android.system', 'android.accounts', 'android.sax', 'android.annotation', 'android']
    },
    {
      id: 'media',
      name: 'Media, hardware & compute',
      color: 'teal',
      packages: ['android.media', 'android.hardware', 'android.renderscript', 'android.speech', 'android.drm', 'android.gesture', 'android.mtp']
    },
    {
      id: 'net',
      name: 'Network, comms & security',
      color: 'lapis',
      packages: ['android.net', 'android.telephony', 'android.bluetooth', 'android.security', 'android.telecom', 'android.location', 'android.nfc', 'android.companion', 'android.credentials', 'android.se', 'android.crypto']
    },
    {
      id: 'platform',
      name: 'Web, health & services',
      color: 'emerald',
      packages: ['android.health', 'android.adservices', 'android.webkit', 'android.accessibilityservice', 'android.print', 'android.printservice', 'android.devicelock']
    }
  ];
  const pkgByName = new Map(classes.packages.map(row => [row.package, row]));
  const assignedPkgs = new Set(domainSpecs.flatMap(d => d.packages));
  const unassignedPkgs = classes.packages.filter(row => !assignedPkgs.has(row.package)).map(row => row.package);
  const packageDomains = [
    ...domainSpecs.map(spec => {
      const items = spec.packages
        .map(name => pkgByName.get(name))
        .filter(Boolean)
        .sort((a, b) => b.reference - a.reference);
      return {
        ...spec,
        items,
        present: items.reduce((s, r) => s + r.present, 0),
        reference: items.reduce((s, r) => s + r.reference, 0)
      };
    }),
    ...(unassignedPkgs.length
      ? [
          {
            id: 'other',
            name: 'Other packages',
            color: 'saffron',
            items: unassignedPkgs.map(name => pkgByName.get(name)).sort((a, b) => b.reference - a.reference),
            present: unassignedPkgs.reduce((s, n) => s + pkgByName.get(n).present, 0),
            reference: unassignedPkgs.reduce((s, n) => s + pkgByName.get(n).reference, 0)
          }
        ]
      : [])
  ];
  const maxPkgRef = Math.max(...classes.packages.map(p => p.reference));

  // Sort NDK libraries largest-first for visual bar clarity.
  const ndkSorted = [...ndk.libraries].sort((a, b) => b.reference - a.reference);
  const maxNdkSymbols = Math.max(...ndkSorted.map(l => l.reference));

  // Sort planned probe categories largest-first for the horizontal breakdown chart.
  const probeCatalogSorted = [...inventory.probeCatalog.categories]
    .map(c => ({ ...c, total: c.automatic + c.manual }))
    .sort((a, b) => b.total - a.total);
  const maxProbeCat = Math.max(...probeCatalogSorted.map(c => c.total));
  const snapshotPass = snapshot ? total('PASS') : 0;
  const snapshotNotRun = snapshot ? total('NOT_RUN') : 0;
  const snapshotOther = snapshot ? snapshot.catalogSize - snapshotPass - snapshotNotRun : 0;
</script>

<section id="android-tracker" class="android-coverage" aria-labelledby="android-coverage-title">
  <h2 id="android-coverage-title">How much of Android works?</h2>
  <p class="coverage-intro">
    Android compatibility has more than one measure. Track host operating systems, subsystem limits, API 35 framework and NDK coverage, and recorded tests here. A class existing or a probe passing does not establish complete Android behavior.
  </p>
  <p class="coverage-date">
    Development snapshot · {inventory.updated} UTC · runtime {inventory.runtimeVersion} · source baseline
    <a href={'https://github.com/spectre-oss/shiraz/commit/' + inventory.sourceCommit}>{inventory.sourceCommit.slice(0, 7)}</a>, with local changes
  </p>

  <h3 id="host-platforms">Host OS compatibility</h3>
  <p>{hostPlatforms.note}</p>
  <p class="coverage-date">Host matrix reviewed {hostPlatforms.updated}.</p>
  <div class="host-platform-cards" aria-label="Supported host operating systems">
    {#each hostPlatforms.platforms as platform (platform.id)}
      {@const prog = platform.progress}
      {@const progTotal = prog.verified + prog.partial + prog.open}
      <article class="host-platform-card" class:host-primary={platform.id === 'linux'}>
        <header>
          <div>
            <h4>{platform.name}</h4>
            <p class="host-platform-arch">{platform.architectures}</p>
          </div>
          <span class="coverage-state" class:unverified={platform.id === 'windows'} class:host-port={platform.id === 'macos'}>{platform.maturity}</span>
        </header>
        <div class="host-progress">
          <div class="host-progress-head">
            <span>{prog.label}</span>
            <strong>{prog.verified}/{progTotal} verified{#if prog.partial} · {prog.partial} partial{/if}{#if prog.open} · {prog.open} open{/if}</strong>
          </div>
          <div
            class="stacked-bar"
            role="img"
            aria-label={`${platform.name}: ${prog.verified} verified, ${prog.partial} partial, ${prog.open} open out of ${progTotal}`}
          >
            <span class="seg-verified" style:width={pct(prog.verified, progTotal)}></span>
            <span class="seg-partial" style:width={pct(prog.partial, progTotal)}></span>
            <span class="seg-open" style:width={pct(prog.open, progTotal)}></span>
          </div>
          <p class="host-progress-note">{prog.note}</p>
          <details class="host-breakdown">
            <summary>Area breakdown ({prog.areas.length} areas)</summary>
            <ul class="area-bars">
              {#each prog.areas as area (area.name)}
                {@const areaTotal = area.verified + area.partial + area.open}
                <li>
                  <div class="area-bar-head">
                    <span>{area.name}</span>
                    <em>{area.verified}/{areaTotal}{#if area.partial} (+{area.partial}~){/if}</em>
                  </div>
                  <div class="stacked-bar stacked-bar-sm" aria-hidden="true">
                    <span class="seg-verified" style:width={pct(area.verified, areaTotal)}></span>
                    <span class="seg-partial" style:width={pct(area.partial, areaTotal)}></span>
                    <span class="seg-open" style:width={pct(area.open, areaTotal)}></span>
                  </div>
                </li>
              {/each}
            </ul>
          </details>
        </div>
        <p>{platform.summary}</p>
        <a href={report(platform.evidence)} aria-label={'Read host specification for ' + platform.name}>Read {platform.name} spec</a>
      </article>
    {/each}
  </div>

  <div class="matrix-legend" aria-label="Host capability status legend">
    <span><i class="layer-dot ready" aria-hidden="true"></i>Verified / Primary</span>
    <span><i class="layer-dot partial" aria-hidden="true"></i>Partial / Opt-in</span>
    <span><i class="layer-dot progress" aria-hidden="true"></i>In progress</span>
  </div>
  <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
  <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Host OS capability matrix" tabindex="0">
    <table class="coverage-table host-matrix-table">
      <caption class="visually-hidden">Shiraz native host backend layers across Linux, macOS ARM64 and Windows x64/ARM64</caption>
      <thead>
        <tr>
          <th scope="col">Layer</th>
          <th scope="col">Linux (x86_64 · ARM64)</th>
          <th scope="col">macOS (Apple Silicon ARM64)</th>
          <th scope="col">Windows (x64 · ARM64)</th>
        </tr>
      </thead>
      <tbody>
        {#each hostPlatforms.layers as row (row.id)}
          <tr>
            <th scope="row">{row.layer}</th>
            {#each ['linux', 'macos', 'windows'] as os}
              {@const meta = layerStateMeta[row.status[os]]}
              <td>
                <span class="layer-chip {meta.cls}"><i class="layer-dot {meta.cls}" aria-hidden="true"></i>{meta.label}</span>
                <p class="layer-detail">{row[os]}</p>
              </td>
            {/each}
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  <h3 id="subsystem-support">Subsystem support</h3>
  <p>{support.note}</p>
  <p class="coverage-date">Support notes reviewed {support.updated}.</p>

  <div class="subsystem-visual" aria-label="Subsystem support distribution">
    <div
      class="stacked-bar"
      role="img"
      aria-label={`Subsystem support split: ${supportCounts.Partial} Partial, ${supportCounts.Unsupported} Unsupported, ${supportCounts.Unverified} Unverified out of ${support.areas.length} subsystems`}
    >
      <span class="seg-verified" style:width={pct(supportCounts.Partial, support.areas.length)}></span>
      <span class="seg-blocked" style:width={pct(supportCounts.Unsupported, support.areas.length)}></span>
      <span class="seg-open" style:width={pct(supportCounts.Unverified, support.areas.length)}></span>
    </div>
    <div class="subsystem-scope-grid" role="group" aria-label="Filter subsystems by platform">
      {#each scopeBreakdown as item (item.name)}
        <button
          type="button"
          class="scope-pill"
          aria-pressed={scope === item.name}
          onclick={() => (scope = scope === item.name ? 'All' : item.name)}
        >
          <span class="scope-pill-head">
            <strong>{item.name}</strong>
            <em>{item.total}</em>
          </span>
          <span class="stacked-bar stacked-bar-xs" aria-hidden="true">
            <span class="seg-verified" style:width={pct(item.partial, item.total)}></span>
            <span class="seg-blocked" style:width={pct(item.unsupported, item.total)}></span>
            <span class="seg-open" style:width={pct(item.unverified, item.total)}></span>
          </span>
        </button>
      {/each}
    </div>
  </div>

  <div class="coverage-filters">
    <label>Search Android support<input type="search" bind:value={query} placeholder="Networking, media, macOS, Windows…" /></label>
    <label>
      Support status
      <select bind:value={status}>
        <option>All</option>
        <option>Partial</option>
        <option>Unsupported</option>
        <option>Unverified</option>
      </select>
    </label>
    <label>
      Host scope
      <select bind:value={scope}>
        <option>All</option>
        {#each scopes as item}<option>{item}</option>{/each}
      </select>
    </label>
  </div>
  <p class="coverage-caption" role="status">{filtered.length} of {support.areas.length} subsystems shown</p>
  <p id="android-table-hint" class="coverage-caption">Tables scroll sideways on smaller screens.</p>
  <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
  <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Android subsystem coverage" tabindex="0">
    <table class="coverage-table">
      <caption class="visually-hidden">Maintained Android subsystem support and verification limits</caption>
      <thead>
        <tr>
          <th scope="col">Subsystem</th>
          <th scope="col">Host scope</th>
          <th scope="col">Support</th>
          <th scope="col">Verification and remaining work</th>
          <th scope="col">Evidence</th>
        </tr>
      </thead>
      <tbody>
        {#each filtered as area (area.id)}
          <tr>
            <th scope="row">{area.name}</th>
            <td>{area.scope}</td>
            <td><span class="coverage-state" class:unsupported={area.status === 'Unsupported'} class:unverified={area.status === 'Unverified'}>{area.status}</span></td>
            <td><strong>{area.verification}.</strong> {area.notes}</td>
            <td><a href={report(area.evidence)} aria-label={'Read evidence for ' + area.name}>Source</a></td>
          </tr>
        {/each}
        {#if !filtered.length}
          <tr>
            <td colspan="5">
              No matching subsystems.
              <button
                type="button"
                onclick={() => {
                  query = '';
                  status = 'All';
                  scope = 'All';
                }}
              >
                Reset support filters
              </button>
            </td>
          </tr>
        {/if}
      </tbody>
    </table>
  </div>

  <h3 id="api-inventory">API class inventory</h3>
  <p>
    <strong>{classes.present.toLocaleString('en')} of {classes.reference.toLocaleString('en')} class names present ({percent}%).</strong>
    This compares the built Java framework with Android {classes.androidVersion} / API {classes.apiLevel}, excluding resource classes. It measures names only, not working methods, signatures or behavior.
  </p>
  <figure class="coverage-figure">
    <div
      class="coverage-bar"
      role="img"
      aria-label={`${classes.present} class names present; ${classes.reference - classes.present} absent from the API ${classes.apiLevel} reference`}
    >
      <span style:width={percent + '%'}></span>
    </div>
    <figcaption>
      <span>Present: {classes.present.toLocaleString('en')} across {classes.packages.length} packages</span>
      <span>Not present: {(classes.reference - classes.present).toLocaleString('en')}</span>
    </figcaption>
  </figure>
  <p>{inventory.scope} <strong>No whole-Android compatibility percentage has been established.</strong></p>

  <div class="domain-mosaic" aria-label="API 35 packages grouped by architectural domain">
    {#each packageDomains as domain (domain.id)}
      <article class="domain-card domain-{domain.color}">
        <header class="domain-head">
          <h4>{domain.name}</h4>
          <strong>{domain.present.toLocaleString('en')} <span>classes</span></strong>
        </header>
        <p class="domain-share">{domain.items.length} packages · {pct(domain.reference, classes.reference)} of API 35 surface</p>
        <ul class="pkg-bars">
          {#each domain.items as item (item.package)}
            <li>
              <span class="pkg-name">{item.package.replace(/^android\./, '')}</span>
              <span class="pkg-track" aria-hidden="true">
                <i style:width={Math.max(4, (100 * item.reference) / maxPkgRef).toFixed(1) + '%'}></i>
              </span>
              <span class="pkg-val">{item.present}</span>
            </li>
          {/each}
        </ul>
      </article>
    {/each}
  </div>

  <details class="coverage-details">
    <summary>Class inventory by Android package (alphabetical table)</summary>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Android API class inventory" tabindex="0">
      <table class="coverage-table coverage-numbers">
        <caption class="visually-hidden">API 35 class-name presence by Android package</caption>
        <thead>
          <tr>
            <th scope="col">Package</th>
            <th scope="col">Present</th>
            <th scope="col">Reference</th>
            <th scope="col">Class names present</th>
          </tr>
        </thead>
        <tbody>
          {#each classes.packages as item}
            <tr>
              <th scope="row">{item.package}</th>
              <td>{item.present}</td>
              <td>{item.reference}</td>
              <td>
                <meter min="0" max={item.reference} value={item.present} aria-label={`${item.package}: ${item.present} of ${item.reference} class names`}></meter>
                {((100 * item.present) / item.reference).toFixed(1)}%
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </details>

  <h3 id="ndk-inventory">NDK native library inventory</h3>
  <p>
    <strong>{ndk.librariesPresent} of {ndk.librariesTotal} NDK libraries built; {ndk.symbolsPresent.toLocaleString('en')} of {ndk.symbolsReference.toLocaleString('en')} cataloged C symbols exported ({ndkPercent}%).</strong>
    Audited from compiled shared libraries with <code>nm</code> against the Android {ndk.androidVersion} / API {ndk.apiLevel} NDK catalog and exercised by 9 native behavioral test suites. Hardware drivers such as host Vulkan ICDs and physical Camera2 capture remain host-dependent.
    <a href={report('docs/ndk_final_goal.md')}>Read the NDK roadmap</a>.
  </p>
  <figure class="coverage-figure">
    <div
      class="coverage-bar coverage-bar-teal"
      role="img"
      aria-label={`${ndk.symbolsPresent} NDK symbols exported; ${ndk.symbolsReference - ndk.symbolsPresent} absent from the API ${ndk.apiLevel} NDK catalog`}
    >
      <span style:width={ndkPercent + '%'}></span>
    </div>
    <figcaption>
      <span>Exported: {ndk.symbolsPresent.toLocaleString('en')} ({ndk.librariesPresent}/{ndk.librariesTotal} libs)</span>
      <span>Missing: {(ndk.symbolsReference - ndk.symbolsPresent).toLocaleString('en')}</span>
    </figcaption>
  </figure>

  <ul class="ndk-bars" aria-label="Exported C symbols per NDK library">
    {#each ndkSorted as item (item.library)}
      {@const shownFamilies = item.families.slice(0, 4)}
      {@const extraFamilies = item.families.length - shownFamilies.length}
      <li>
        <div class="ndk-bar-row">
          <code class="ndk-lib-name">{item.library}</code>
          <span class="ndk-track" aria-hidden="true">
            <i style:width={Math.max(4, (100 * item.present) / maxNdkSymbols).toFixed(1) + '%'}></i>
          </span>
          <strong class="ndk-count">{item.present}/{item.reference}</strong>
        </div>
        <div class="ndk-families">
          {#each shownFamilies as fam}<span>{fam}</span>{/each}
          {#if extraFamilies > 0}<span class="ndk-more">+{extraFamilies} more</span>{/if}
        </div>
      </li>
    {/each}
  </ul>

  <details class="coverage-details">
    <summary>NDK symbol inventory by shared library (full table)</summary>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Android NDK symbol inventory" tabindex="0">
      <table class="coverage-table coverage-numbers">
        <caption class="visually-hidden">API 35 NDK C symbol presence by shared library</caption>
        <thead>
          <tr>
            <th scope="col">Library</th>
            <th scope="col">Present</th>
            <th scope="col">Reference</th>
            <th scope="col">Symbols exported</th>
          </tr>
        </thead>
        <tbody>
          {#each ndk.libraries as item}
            <tr>
              <th scope="row">{item.library}</th>
              <td>{item.present}</td>
              <td>{item.reference}</td>
              <td>
                <meter min="0" max={item.reference} value={item.present} aria-label={`${item.library}: ${item.present} of ${item.reference} symbols`}></meter>
                {((100 * item.present) / item.reference).toFixed(1)}%
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </details>

  <h3>Behavioral probe tracker</h3>
  <p>
    The recorded development catalog defines <strong>{inventory.probeCatalog.total} assertions</strong>. A planned assertion has no result until it is run. It is a versioned snapshot, not a live inventory of every branch. The behavioral run below is a separate measurement.
  </p>

  <details class="coverage-details">
    <summary>Planned assertions by category ({inventory.probeCatalog.total} total)</summary>
    <div class="probe-legend">
      <span><i class="dot dot-teal" aria-hidden="true"></i>Automatic</span>
      <span><i class="dot dot-saffron" aria-hidden="true"></i>Manual</span>
    </div>
    <ul class="coverage-catalog probe-bars">
      {#each probeCatalogSorted as item (item.name)}
        <li>
          <span class="probe-cat-name"><strong>{item.name}</strong></span>
          <span class="probe-track" aria-hidden="true">
            <i class="seg-auto" style:width={((100 * item.automatic) / maxProbeCat).toFixed(1) + '%'}></i>
            <i class="seg-partial" style:width={((100 * item.manual) / maxProbeCat).toFixed(1) + '%'}></i>
          </span>
          <span class="probe-cat-val">{item.automatic} auto{#if item.manual} · {item.manual} manual{/if}</span>
        </li>
      {/each}
    </ul>
  </details>

  {#if snapshot}
    <h4>{snapshot.label}</h4>
    <p>{snapshot.catalogSize}-case suite {snapshot.suiteVersion} · {snapshot.host}. {snapshot.note}</p>
    <figure class="coverage-figure">
      <div
        class="stacked-bar"
        role="img"
        aria-label={`Historical probe run: ${snapshotPass} PASS, ${snapshotNotRun} NOT RUN out of ${snapshot.catalogSize} assertions`}
      >
        <span class="seg-verified" style:width={pct(snapshotPass, snapshot.catalogSize)}></span>
        {#if snapshotOther > 0}<span class="seg-blocked" style:width={pct(snapshotOther, snapshot.catalogSize)}></span>{/if}
        <span class="seg-open" style:width={pct(snapshotNotRun, snapshot.catalogSize)}></span>
      </div>
      <figcaption>
        <span>Verified in targeted run: {snapshotPass} PASS ({pct(snapshotPass, snapshot.catalogSize)})</span>
        <span>Not run in this snapshot: {snapshotNotRun} ({pct(snapshotNotRun, snapshot.catalogSize)})</span>
      </figcaption>
    </figure>
    <ul class="coverage-totals" aria-label="Recorded probe status totals">
      {#each states as state}
        <li class:total-pass={state === 'PASS' && total(state) > 0} class:total-notrun={state === 'NOT_RUN' && total(state) > 0}>
          <strong>{total(state)}</strong> {state.replaceAll('_', ' ')}
        </li>
      {/each}
    </ul>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Historical probe results" tabindex="0">
      <table class="coverage-table coverage-numbers probe-results-table">
        <caption class="visually-hidden">Historical assertion results, with unrun cases kept separate</caption>
        <thead>
          <tr>
            <th scope="col">Category</th>
            <th scope="col">Run share</th>
            {#each states as state}<th scope="col">{state.replaceAll('_', ' ')}</th>{/each}
          </tr>
        </thead>
        <tbody>
          {#each snapshot.categories as item}
            {@const rowTotal = states.reduce((s, st) => s + item.counts[st], 0)}
            <tr>
              <th scope="row">{item.name}</th>
              <td>
                <span class="stacked-bar stacked-bar-xs probe-inline-bar" aria-hidden="true">
                  <span class="seg-verified" style:width={pct(item.counts.PASS, rowTotal)}></span>
                  <span class="seg-blocked" style:width={pct(item.counts.FAIL + item.counts.TIMEOUT, rowTotal)}></span>
                  <span class="seg-open" style:width={pct(item.counts.NOT_RUN, rowTotal)}></span>
                </span>
              </td>
              {#each states as state}
                <td class:cell-pass={state === 'PASS' && item.counts[state] > 0} class:cell-muted={item.counts[state] === 0}>{item.counts[state]}</td>
              {/each}
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    <p class="coverage-caption">
      The import checks the report against its exact source fingerprint. It publishes only category totals, with no private result details.
      <a href={report('tests/api-probe/catalog.json')}>View probe source at the baseline</a>.
    </p>
  {:else}
    <p>No probe results have been imported. No behavioral pass rate is available.</p>
  {/if}
  <p class="coverage-caption">
    Maintainers: update <a href={report('landing/src/lib/android-support.json')}>subsystem notes</a> and regenerate inventories using
    <a href={report('landing/README.md')}>the tracker workflow</a> when support changes.
  </p>
</section>
