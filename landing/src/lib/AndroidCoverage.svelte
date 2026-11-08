<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import inventory from './android-inventory.json';
  import support from './android-support.json';
  let query = $state('');
  let status = $state('All');
  let scope = $state('All');
  const scopes = [...new Set(support.areas.map(area => area.scope))];
  const filtered = $derived(support.areas.filter(area =>
    (status === 'All' || area.status === status) && (scope === 'All' || area.scope === scope) &&
    `${area.name} ${area.notes} ${area.verification}`.toLowerCase().includes(query.trim().toLowerCase())
  ));
  const classes = inventory.inventory;
  const percent = (100 * classes.present / classes.reference).toFixed(1);
  const states = ['PASS','FAIL','UNSUPPORTED','SKIPPED','TIMEOUT','NOT_RUN'];
  const snapshot = inventory.probeSnapshot;
  const total = state => snapshot.categories.reduce((sum,row) => sum + row.counts[state],0);
  const report = path => 'https://github.com/spectre-oss/shiraz/blob/main/' + path;
</script>
<section id="android-tracker" class="android-coverage" aria-labelledby="android-coverage-title">
  <h2 id="android-coverage-title">How much of Android works?</h2>
  <p class="coverage-intro">Android compatibility has more than one measure. Track the API surface, subsystem limits and recorded tests here. A class existing or a probe passing does not establish complete Android behavior.</p>
  <p class="coverage-date">Development snapshot · {inventory.updated} UTC · runtime {inventory.runtimeVersion} · source <a href={'https://github.com/spectre-oss/shiraz/commit/' + inventory.sourceCommit}>{inventory.sourceCommit.slice(0,7)}</a>, with local changes</p>

  <h3>Subsystem support</h3>
  <p>{support.note}</p>
  <p class="coverage-date">Support notes reviewed {support.updated}.</p>
  <div class="coverage-filters">
    <label>Search Android support<input type="search" bind:value={query} placeholder="Networking, media, storage…" /></label>
    <label>Support status<select bind:value={status}><option>All</option><option>Partial</option><option>Unsupported</option><option>Unverified</option></select></label>
    <label>Host scope<select bind:value={scope}><option>All</option>{#each scopes as item}<option>{item}</option>{/each}</select></label>
  </div>
  <p class="coverage-caption" role="status">{filtered.length} of {support.areas.length} subsystems shown</p>
  <p id="android-table-hint" class="coverage-caption">Tables scroll sideways on smaller screens.</p>
  <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
  <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Android subsystem coverage" tabindex="0">
    <table class="coverage-table"><caption class="visually-hidden">Maintained Android subsystem support and verification limits</caption>
      <thead><tr><th scope="col">Subsystem</th><th scope="col">Host scope</th><th scope="col">Support</th><th scope="col">Verification and remaining work</th><th scope="col">Evidence</th></tr></thead>
      <tbody>{#each filtered as area (area.id)}<tr><th scope="row">{area.name}</th><td>{area.scope}</td><td><span class="coverage-state" class:unsupported={area.status === 'Unsupported'} class:unverified={area.status === 'Unverified'}>{area.status}</span></td><td><strong>{area.verification}.</strong> {area.notes}</td><td><a href={report(area.evidence)} aria-label={'Read evidence for ' + area.name}>Source</a></td></tr>{/each}
        {#if !filtered.length}<tr><td colspan="5">No matching subsystems. <button type="button" onclick={() => { query='';status='All';scope='All'; }}>Reset support filters</button></td></tr>{/if}
      </tbody>
    </table>
  </div>

  <h3>API class inventory</h3>
  <p><strong>{classes.present.toLocaleString('en')} of {classes.reference.toLocaleString('en')} class names present ({percent}%).</strong> This compares the built Java framework with Android {classes.androidVersion} / API {classes.apiLevel}, excluding resource classes. It measures names only, not working methods, signatures or behavior.</p>
  <figure class="coverage-figure">
    <div class="coverage-bar" role="img" aria-label={`${classes.present} class names present; ${classes.reference-classes.present} absent from the API ${classes.apiLevel} reference`}><span style:width={percent + '%'}></span></div>
    <figcaption><span>Present: {classes.present.toLocaleString('en')}</span><span>Not present: {(classes.reference-classes.present).toLocaleString('en')}</span></figcaption>
  </figure>
  <p>{inventory.scope} <strong>No whole-Android compatibility percentage has been established.</strong></p>
  <details class="coverage-details"><summary>Class inventory by Android package</summary>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
    <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Android API class inventory" tabindex="0"><table class="coverage-table coverage-numbers"><caption class="visually-hidden">API 35 class-name presence by Android package</caption><thead><tr><th scope="col">Package</th><th scope="col">Present</th><th scope="col">Reference</th><th scope="col">Class names present</th></tr></thead><tbody>{#each classes.packages as item}<tr><th scope="row">{item.package}</th><td>{item.present}</td><td>{item.reference}</td><td><meter min="0" max={item.reference} value={item.present} aria-label={`${item.package}: ${item.present} of ${item.reference} class names`}></meter> {(100*item.present/item.reference).toFixed(1)}%</td></tr>{/each}</tbody></table></div>
  </details>

  <h3>Behavioral probe tracker</h3>
  <p>The current catalog defines <strong>{inventory.probeCatalog.total} assertions</strong>. A planned assertion has no result until it is run. The recorded snapshot below is separate from this current plan.</p>
  <details class="coverage-details"><summary>Current planned assertions by category</summary><ul class="coverage-catalog">{#each inventory.probeCatalog.categories as item}<li><strong>{item.name}</strong><span>{item.automatic} automatic · {item.manual} manual</span></li>{/each}</ul></details>
  {#if snapshot}
  <h4>{snapshot.label}</h4>
  <p>{snapshot.catalogSize}-case suite {snapshot.suiteVersion} · {snapshot.host}. {snapshot.note}</p>
  <ul class="coverage-totals" aria-label="Recorded probe status totals">{#each states as state}<li><strong>{total(state)}</strong> {state.replaceAll('_',' ')}</li>{/each}</ul>
  <!-- svelte-ignore a11y_no_noninteractive_tabindex (keyboard access to horizontally scrollable table) -->
  <div class="coverage-scroll" role="region" aria-describedby="android-table-hint" aria-label="Historical probe results" tabindex="0"><table class="coverage-table coverage-numbers"><caption class="visually-hidden">Historical assertion results, with unrun cases kept separate</caption><thead><tr><th scope="col">Category</th>{#each states as state}<th scope="col">{state.replaceAll('_',' ')}</th>{/each}</tr></thead><tbody>{#each snapshot.categories as item}<tr><th scope="row">{item.name}</th>{#each states as state}<td>{item.counts[state]}</td>{/each}</tr>{/each}</tbody></table></div>
  <p class="coverage-caption">The import checks the report against its exact source fingerprint. It publishes only category totals, with no private result details. <a href={report('tests/api-probe/catalog.json')}>View current probe definitions</a>.</p>
  {:else}<p>No probe results have been imported. No behavioral pass rate is available.</p>{/if}
  <p class="coverage-caption">Maintainers: update <a href={report('landing/src/lib/android-support.json')}>subsystem notes</a> and regenerate inventories using <a href={report('landing/README.md')}>the tracker workflow</a> when support changes.</p>
</section>
