// SPDX-License-Identifier: GPL-3.0-only
import { test, expect } from '@playwright/test';
import { createRequire } from 'node:module';
import catalog from '../src/lib/compatibility.json' with {type:'json'};
import media from '../src/lib/media.json' with {type:'json'};
import { existsSync } from 'node:fs';
const require = createRequire(import.meta.url);

// No test may depend on GitHub's live API or its current star count.
test.beforeEach(async ({page}) => {
  await page.route('https://api.github.com/**', route => route.abort());
});

test('header links to GitHub with its mark and a star count that refreshes once per session', async ({page}) => {
  let calls = 0;
  await page.route('https://api.github.com/repos/spectre-oss/shiraz', route => { calls++; return route.fulfill({json:{stargazers_count:1234}}); });
  for (const path of ['/', '/compatibility/']) {
    await page.goto(path);
    const link = page.getByRole('link', {name:'GitHub repository, 1234 stars'});
    await expect(link).toHaveAttribute('href', 'https://github.com/spectre-oss/shiraz');
    await expect(link.locator('.github-stars')).toHaveText('1.2k');
    await expect(link.locator('.github-mark')).toBeVisible();
  }
  expect(calls).toBe(1);
});

test('GitHub link stays usable when the star count cannot be refreshed', async ({page}) => {
  await page.goto('/');
  const link = page.locator('.site-header .github-link');
  await expect(link).toHaveAttribute('href', 'https://github.com/spectre-oss/shiraz');
  await expect(link).toHaveAccessibleName(/^GitHub repository/);
  await page.setViewportSize({width: 320, height: 700});
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const box = await link.boundingBox();
  expect(box.height).toBeGreaterThanOrEqual(44);
  expect(box.x + box.width).toBeLessThanOrEqual(320);
});

test('each command block copies its own commands', async ({page, context, browserName}) => {
  test.skip(browserName !== 'chromium', 'clipboard permissions are Chromium-only in Playwright');
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/');
  await page.getByText('Get an app from F-Droid').click();
  await page.getByRole('button', {name:'Copy F-Droid commands'}).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('python3 -m shiraz fdroid fetch');
  await page.getByText('Build from source').click();
  await page.getByRole('button', {name:'Copy build commands'}).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('make build\nmake appimage APK=/path/to/app.apk\nmake flatpak APK=/path/to/app.apk');
});

test('responsive site keeps table columns aligned and has no accessibility violations', async ({page}) => {
  for (const width of [2560, 1182, 390, 320]) {
    await page.setViewportSize({width, height: 881});
    await page.goto('/');
    await expect(page.locator('#builder')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const cells = await page.locator('.comparison-table tbody tr').evaluateAll(rows => rows.filter(row => row.getClientRects().length).map(row => [...row.children].map(cell => Math.round(cell.getBoundingClientRect().left))));
    cells.forEach(row => expect(row).toEqual(cells[0]));
    await page.addScriptTag({path: require.resolve('axe-core/axe.min.js')});
    const violations = await page.evaluate(async () => (await window.axe.run(document, {runOnly: {type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}})).violations);
    expect(violations).toEqual([]);
  }
});

test('builder anchor stays on the page and missing APK fails before loading WASM', async ({page}) => {
  let wasmRequests = 0;
  page.on('request', request => { if (request.url().endsWith('.wasm')) wasmRequests++; });
  await page.goto('/');
  await page.getByRole('link', {name:'Open the builder'}).click();
  await expect(page).toHaveURL(/#builder$/);
  await page.locator('#assemble').click();
  await expect(page.locator('#status')).toHaveText('Select an APK to begin.');
  expect(wasmRequests).toBe(0);
});

test('downloaded runtime with a bad checksum is rejected', async ({page}) => {
  await page.route('**/release.json', route => route.fulfill({json:{version:'1.2.3',architectures:{x86_64:{base_file:'base/Shiraz-Browser-Base-1.2.3-x86_64.AppImage',base_size:4,base_sha256:'0'.repeat(64)}}}}));
  await page.route('**/base/*.AppImage', route => route.fulfill({body:Buffer.from([1,2,3,4])}));
  await page.goto('/');
  await expect(page.locator('.builder-base strong')).toHaveText('v1.2.3');
  await page.locator('#apk').setInputFiles({name:'fixture.apk',mimeType:'application/zip',buffer:Buffer.from('PKfixture')});
  await page.locator('#assemble').click();
  await expect(page.locator('#status')).toHaveText('Base checksum mismatch.', {timeout:30000});
  await expect(page.locator('#assemble')).toBeEnabled();
});

test('reduced motion leaves the drawing static and the stack fully readable', async ({page}) => {
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.goto('/');
  const art = page.locator('.hero-art *, .hero h1 .line > span, .stack-diagram .slab');
  expect(await art.count()).toBeGreaterThan(30);
  expect(await art.evaluateAll(parts => parts.every(part => part.getAnimations().length === 0))).toBe(true);
  // The whole stack stays readable and unselected; nothing waits on scroll.
  await page.locator('.stack-diagram').scrollIntoViewIfNeeded();
  await expect(page.locator('.stack-diagram li.is-active')).toHaveCount(0);
  for (const name of ['Your APK', 'ART', 'Native bridges']) await expect(page.locator('.stack-diagram').getByText(name, {exact: false}).first()).toBeVisible();
});

test('hero drawing arrives once and the stack follows the reading position', async ({page}) => {
  await page.setViewportSize({width: 1280, height: 800});
  await page.goto('/');
  await expect(page.locator('.glass-pane')).toHaveCount(15);
  await expect(page.locator('.light-patch')).toHaveCount(15);
  await expect.poll(() => page.locator('.landing-window').evaluate(node => getComputedStyle(node).opacity)).toBe('1');
  await expect(page.locator('.site-header')).not.toHaveClass(/is-scrolled/);
  await page.locator('[data-step="3"]').evaluate(step => step.scrollIntoView({block: 'center', behavior: 'instant'}));
  await expect(page.locator('[data-layer="3"]')).toHaveClass(/is-active/);
  await expect(page.locator('.stack-diagram li.is-active')).toHaveCount(1);
  await expect(page.locator('.site-header')).toHaveClass(/is-scrolled/);
  const progress = await page.locator('.stack-diagram').evaluate(node => Number(node.style.getPropertyValue('--stack-progress')));
  expect(progress).toBeGreaterThan(.6);
  expect(progress).toBeLessThan(.9);
});

test('architecture cards support radio keyboard selection and refresh the runtime', async ({page}) => {
  await page.route('**/release.json', route => route.fulfill({json:{version:'1.2.3',architectures:Object.fromEntries(['x86_64','aarch64'].map(arch => [arch,{base_file:`base/Shiraz-Browser-Base-1.2.3-${arch}.AppImage`,base_size:4,base_sha256:'0'.repeat(64)}]))}}));
  await page.goto('/');
  const intel = page.getByRole('radio', {name:'x86_64 Intel / AMD'});
  const arm = page.getByRole('radio', {name:/ARM64 Linux phones/});
  await expect(intel).toBeChecked();
  await intel.focus();
  await page.keyboard.press('ArrowRight');
  await expect(arm).toBeChecked();
  await expect(page.locator('.builder-base p')).toContainText('aarch64');
});

test('package format cards switch between AppImage and Flatpak with keyboard and reject bad Flatpak checksums', async ({page}) => {
  const flatpakBody = Buffer.from('SHIRAZFPtest');
  await page.route('**/release.json', route => route.fulfill({json:{
    version:'1.2.3',
    architectures:Object.fromEntries(['x86_64','aarch64'].map(arch => [arch,{
      base_file:`base/Shiraz-Browser-Base-1.2.3-${arch}.AppImage`,
      base_size:4,
      base_sha256:'0'.repeat(64),
      formats:{
        appimage:{base_file:`base/Shiraz-Browser-Base-1.2.3-${arch}.AppImage`,base_size:4,base_sha256:'0'.repeat(64)},
        flatpak:{base_file:`base/Shiraz-Flatpak-Base-1.2.3-${arch}.flatpak-base`,base_size:flatpakBody.length,base_sha256:'f'.repeat(64)}
      }
    }]))
  }}));
  await page.route('**/base/*.flatpak-base', route => route.fulfill({body:flatpakBody}));
  await page.goto('/');
  const appimage = page.getByRole('radio', {name:/AppImage/});
  const flatpak = page.getByRole('radio', {name:/Flatpak/});
  await expect(appimage).toBeChecked();
  await expect(page.locator('#assemble')).toContainText('Build AppImage');
  await expect(page.locator('#flatpak-network')).toHaveCount(0);

  await appimage.focus();
  await page.keyboard.press('ArrowRight');
  await expect(flatpak).toBeChecked();
  await expect(page.locator('#assemble')).toContainText('Build Flatpak');
  await expect(page.locator('.builder-base p')).toContainText('Flatpak');
  await expect(page.locator('#base')).toHaveAttribute('accept', '.flatpak-base');
  await expect(page.locator('#flatpak-network')).toBeChecked();

  await page.locator('#apk').setInputFiles({name:'fixture.apk',mimeType:'application/zip',buffer:Buffer.from('PKfixture')});
  await page.locator('#assemble').click();
  await expect(page.locator('#status')).toHaveText('Base checksum mismatch.', {timeout:30000});
  await expect(page.locator('#assemble')).toBeEnabled();
});


test('social previews are present before JavaScript and use a real PNG card', async ({request}) => {
  const html = await (await request.get('/')).text();
  expect(html).toContain('property="og:image"');
  expect(html).toContain('name="twitter:card" content="summary_large_image"');
  const response = await request.get('/social-card.png');
  expect(response.ok()).toBe(true);
  const png = await response.body();
  expect(png.subarray(1,4).toString()).toBe('PNG');
  expect(png.readUInt32BE(16)).toBe(1200);
  expect(png.readUInt32BE(20)).toBe(630);
});

test('poem translates only the hovered verse and supports keyboard focus', async ({page}) => {
  await page.goto('/');
  const lines = page.locator('.verse-line');
  await lines.nth(1).hover();
  await expect(lines.nth(1)).toHaveClass(/verse-translated/);
  await expect(page.locator('.verse-translated')).toHaveCount(1);
  await lines.nth(2).hover();
  await expect(lines.nth(1)).not.toHaveClass(/verse-translated/);
  await expect(lines.nth(2)).toHaveClass(/verse-translated/);
  await lines.nth(0).focus();
  await expect(lines.nth(0)).toHaveClass(/verse-translated/);
});

test('compatibility directory filters recorded evidence and handles empty searches', async ({page}) => {
  await page.goto('/compatibility/');
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(catalog.applications.length);
  await page.getByRole('button',{name:'Blocked',exact:true}).click();
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(1);
  await page.getByRole('group',{name:'Filter by recorded status'}).getByRole('button',{name:'All',exact:true}).click();
  await page.getByLabel('Framework',{exact:true}).selectOption('Flutter');
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(2);
  await page.getByLabel('Search apps',{exact:true}).fill('no matching application');
  await expect(page.getByRole('heading',{name:'No matching apps'})).toBeVisible();
  await page.getByRole('button',{name:'Reset filters'}).click();
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(catalog.applications.length);
  await page.getByLabel('Search apps',{exact:true}).fill('Joplin');
  await page.getByRole('button',{name:'Known limitations',exact:true}).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByText(/Body editing is unsupported/)).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('button',{name:'Known limitations',exact:true})).toBeFocused();
  await page.getByLabel('Search apps',{exact:true}).fill('Telegram');
  await expect(page.getByRole('rowheader',{name:'Telegram',exact:true})).toBeVisible();
  await expect(page.locator('.compat-table tbody tr')).toContainText('macOS ARM64');
  await expect(page.getByRole('region',{name:'App compatibility results'}).getByRole('cell',{name:'Partial',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Known limitations',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('saved login across restart');
  await expect(page.getByRole('dialog')).toContainText('physical microphone/camera capture remain unverified');
});

test('compatibility mobile view stays within the viewport and is accessible', async ({page}) => {
  for (const width of [1280, 390, 320]) {
    await page.setViewportSize({width, height: 844});
    await page.goto('/compatibility/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.addScriptTag({path: require.resolve('axe-core/axe.min.js')});
    const violations = await page.evaluate(async () => (await window.axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa']}})).violations);
    expect(violations).toEqual([]);
  }
});

test('compatibility visualizations render progress tiles, engine chips, domain mosaic and NDK chart', async ({page}) => {
  await page.goto('/compatibility/');
  await expect(page.locator('.progress-tile')).toHaveCount(4);
  await expect(page.locator('.progress-overview')).toContainText('4,445');
  await expect(page.locator('.progress-overview')).toContainText('15/15');
  // Engine chip quick-filters the app table and toggles cleanly back to All.
  const flutterChip = page.getByRole('group', {name: 'Quick filter by app engine'}).getByRole('button', {name: /Flutter/});
  await flutterChip.click();
  await expect(flutterChip).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(2);
  await flutterChip.click();
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(catalog.applications.length);
  // Host OS progress cards expose audited checklist counts and expandable areas.
  const macCard = page.locator('.host-platform-card', {hasText: 'macOS'});
  await expect(macCard).toContainText('46/91 verified');
  await macCard.locator('.host-breakdown summary').click();
  await expect(macCard.locator('.area-bars li')).toHaveCount(8);
  const winCard = page.locator('.host-platform-card', {hasText: 'Windows'});
  await expect(winCard).toContainText('32/37 verified');
  // 5-domain API 35 mosaic & 15-library NDK bar landscape render all tracked data.
  await expect(page.locator('.domain-card')).toHaveCount(5);
  await expect(page.locator('.ndk-bars > li')).toHaveCount(15);
  await expect(page.locator('.ndk-bars')).toContainText('316/316');
});

test('compare page renders visual heatmap, runtime pipelines, head-to-head sync and passes accessibility', async ({page}) => {
  for (const width of [1280, 390, 320]) {
    await page.setViewportSize({width, height: 844});
    await page.goto('/compare/');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.addScriptTag({path: require.resolve('axe-core/axe.min.js')});
    const violations = await page.evaluate(async () => (await window.axe.run(document, {runOnly: {type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa']}})).violations);
    expect(violations).toEqual([]);
  }
  await page.setViewportSize({width: 1280, height: 844});
  await page.goto('/compare/');
  await expect(page.locator('.heatmap tbody tr')).toHaveCount(9);
  await expect(page.locator('.pipeline-row')).toHaveCount(7);
  await expect(page.locator('.pillar-col')).toHaveCount(7);
  // Selecting a peer from the heatmap updates the head-to-head comparison and scorecard.
  await page.locator('.heatmap').getByRole('button', {name: 'Mac.apk', exact: true}).click();
  await expect(page.locator('.duel-scorecard')).toContainText('Mac.apk');
  await expect(page.locator('.verdict')).toContainText('Where Mac.apk is ahead');
});


test('compatibility alphabet filters and preserves alphabetical row order', async ({page}) => {
  await page.goto('/compatibility/');
  const names = await page.locator('.compat-table tbody th').allTextContents();
  expect(names).toEqual([...names].sort((a,b)=>a.localeCompare(b,'en',{numeric:true})));
  await page.getByRole('navigation',{name:'Filter apps by first letter'}).getByRole('button',{name:'S',exact:true}).click();
  await expect(page.locator('.compat-table tbody tr')).toHaveCount(2);
  await expect(page.locator('.compat-table tbody th').first()).toHaveText('SEN-214 Calculator');
});

test('limitations dialog can reverse an interrupted close and honors reduced motion', async ({page}) => {
  await page.goto('/compatibility/');
  await page.locator('.limitations-link').first().click();
  await page.waitForTimeout(50);
  await page.locator('dialog').evaluate(dialog => dialog.querySelector('button').click());
  await page.waitForTimeout(40);
  await page.locator('.limitations-link').first().evaluate(button => button.click());
  await expect(page.locator('dialog')).toHaveAttribute('open','');
  await expect.poll(()=>page.locator('dialog').evaluate(dialog=>Number(getComputedStyle(dialog).opacity))).toBe(1);
  await page.emulateMedia({reducedMotion:'reduce'});
  expect(await page.locator('dialog').evaluate(dialog=>getComputedStyle(dialog).transitionDuration)).toBe('0s');
  await page.keyboard.press('Escape');
  await expect(page.locator('dialog')).not.toBeVisible();
});

test('Android tracker separates class inventory, subsystem support and historical probe results', async ({page}) => {
  await page.goto('/compatibility/');
  await page.getByRole('link',{name:'Android coverage tracker',exact:true}).click();
  const coverage=page.locator('#android-tracker');
  await expect(coverage.getByRole('heading',{name:'How much of Android works?'})).toBeVisible();
  await expect(coverage.getByText('No whole-Android compatibility percentage has been established.',{exact:true})).toBeVisible();
  await expect(coverage.getByRole('img',{name:/class names present; .* absent from the API 35 reference/})).toBeVisible();
  await expect(coverage.getByRole('heading',{name:'NDK native library inventory'})).toBeVisible();
  await expect(coverage.getByRole('img',{name:/NDK symbols exported; 0 absent from the API 35 NDK catalog/})).toBeVisible();
  await coverage.getByLabel('Search Android support').fill('microphone');
  const table=coverage.getByRole('region',{name:'Android subsystem coverage'});
  await expect(table.getByRole('rowheader',{name:'Microphone and camera'})).toBeVisible();
  await expect(table).toContainText('Hardware unverified');
  await coverage.getByLabel('Support status').selectOption('Unsupported');
  await expect(table).toContainText('No matching subsystems.');
  await coverage.getByRole('button',{name:'Reset support filters'}).click();
  await coverage.getByLabel('Host scope').selectOption('macOS ARM64');
  await coverage.getByLabel('Support status').selectOption('Unsupported');
  await expect(table.getByRole('rowheader',{name:'Video recording',exact:true})).toBeVisible();
  await expect(table.getByRole('rowheader',{name:'Files and preferences',exact:true})).toHaveCount(0);
  await coverage.getByLabel('Support status').selectOption('Partial');
  await expect(table.getByRole('rowheader',{name:'VideoToolbox hardware decode',exact:true})).toBeVisible();
  await expect(table.getByRole('rowheader',{name:'Signed macOS .app bundle builder',exact:true})).toBeVisible();
  await coverage.getByLabel('Host scope').selectOption('Windows x64 / ARM64');
  await expect(table.getByRole('rowheader',{name:'Windows Win32 host primitives and IPC',exact:true})).toBeVisible();
  await expect(table.getByRole('rowheader',{name:'Windows portable and MSIX bundler',exact:true})).toBeVisible();
  await expect(coverage.getByRole('heading',{name:'Host OS compatibility'})).toBeVisible();
  const hostMatrix = coverage.getByRole('region',{name:'Host OS capability matrix'});
  await expect(hostMatrix).toContainText('VideoToolbox');
  await expect(hostMatrix).toContainText('WASAPI');
  await expect(coverage.getByRole('heading',{name:'Historical targeted network run'})).toBeVisible();
  await expect(coverage.getByRole('list',{name:'Recorded probe status totals'})).toContainText('NOT RUN');
  await expect(coverage.getByRole('region',{name:'Historical probe results'})).toContainText('Network');
});

test('unknown addresses get the site error page with a way back', async ({ page }) => {
  const response = await page.goto('/no-such-page/');
  expect(response.status()).toBe(404);
  await expect(page.getByRole('heading',{level:1,name:'This page is not here.'})).toBeVisible();
  await page.addScriptTag({path:require.resolve('axe-core/axe.min.js')});
  const violations = await page.evaluate(async()=> (await window.axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}})).violations);
  expect(violations).toEqual([]);
  await page.getByRole('link',{name:'Go to the home page'}).click();
  await expect(page.getByRole('heading',{level:1})).toContainText('Your Android');
});

test('stack labels jump to their explanation and the last sheet lights its window', async ({page}) => {
  await page.setViewportSize({width: 1280, height: 860});
  await page.goto('/');
  await page.locator('.stack-diagram').getByRole('link', {name:/Native bridges/}).click();
  await expect(page).toHaveURL(/#step-4$/);
  await expect(page.locator('[data-layer="3"]')).toHaveClass(/is-active/);
  await page.locator('.stack-diagram').getByRole('link', {name:/desktop/}).click();
  await expect(page.locator('[data-layer="4"]')).toHaveClass(/is-active/);
  await expect.poll(() => page.locator('[data-layer="4"] .slab').evaluate(node => getComputedStyle(node, '::after').opacity)).toBe('1');
  // On phones the collapsed bar exposes only the current label to the keyboard.
  await page.setViewportSize({width: 390, height: 800});
  await page.locator('#step-2').scrollIntoViewIfNeeded();
  await expect(page.locator('.stack-diagram a:visible')).toHaveCount(1);
});

test('launcher film loads nothing before play and carries captions', async ({page}) => {
  const built = [media.video.file, media.poster.file].every(file => existsSync(new URL(`../build/media/${file}`, import.meta.url)));
  const requested = [];
  page.on('request', request => { if (request.url().endsWith('.mp4')) requested.push(request.url()); });
  await page.goto('/');
  const video = page.locator('#mac video');
  // A build without the pinned film must not show an empty player.
  if (!built) { await expect(video).toHaveCount(0); return; }
  await video.scrollIntoViewIfNeeded();
  await expect(video).toHaveAttribute('preload', 'none');
  await expect(video).toHaveJSProperty('controls', true);
  await expect(video).toHaveJSProperty('autoplay', false);
  await expect(video).toHaveJSProperty('paused', true);
  await expect(video).toHaveAccessibleName(/Shiraz Launcher on a Mac/);
  await expect(video.locator('track[kind="captions"][srclang="en"]')).toHaveCount(1);
  expect(await page.evaluate(async src => (await (await fetch(src)).text()).startsWith('WEBVTT'), await video.locator('track').getAttribute('src'))).toBe(true);
  expect(await video.evaluate(node => node.poster && new Promise(done => { const image = new Image(); image.onload = () => done(image.naturalWidth > 0); image.onerror = () => done(false); image.src = node.poster; }))).toBe(true);
  expect(requested).toEqual([]);
  for (const width of [320, 1280]) {
    await page.setViewportSize({width, height: 800});
    const box = await video.boundingBox();
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(width);
    expect(Math.abs(box.width / box.height - 16 / 9)).toBeLessThan(0.02);
  }
});

test('comparison shows six approaches and reveals the rest on request', async ({page}) => {
  await page.goto('/');
  const rows = page.locator('.comparison-table tbody tr');
  const visible = () => rows.evaluateAll(all => all.filter(row => row.getClientRects().length).length);
  expect(await visible()).toBe(6);
  const toggle = page.getByRole('button', {name: 'Show 8 more approaches'});
  await expect(toggle).toHaveAttribute('aria-expanded', 'false');
  await toggle.click();
  expect(await visible()).toBe(14);
  await expect(page.getByRole('button', {name: 'Show fewer'})).toHaveAttribute('aria-expanded', 'true');
});

test('every route shares one navigation and marks the current page', async ({page}) => {
  for (const [path, current] of [['/', null], ['/compatibility/', 'Compatibility'], ['/compare/', 'Compare']]) {
    await page.goto(path);
    const nav = page.getByRole('navigation', {name: 'Main navigation'});
    for (const name of ['Builder', 'Compatibility', 'Compare']) await expect(nav.getByRole('link', {name, exact: true})).toHaveCount(1);
    if (current) await expect(nav.locator('[aria-current=page]')).toHaveText(current);
    else await expect(nav.locator('[aria-current=page]')).toHaveCount(0);
  }
});

test('SEO, GEO and AEO discovery files, JSON-LD graphs and matching FAQs are published on every route', async ({page, request}) => {
  const robots = await request.get('/robots.txt');
  expect(robots.ok()).toBe(true);
  expect(await robots.text()).toContain('Sitemap: https://spectre-oss.github.io/shiraz/sitemap.xml');

  const sitemap = await request.get('/sitemap.xml');
  expect(sitemap.ok()).toBe(true);
  const sitemapXml = await sitemap.text();
  for (const url of ['https://spectre-oss.github.io/shiraz/', 'https://spectre-oss.github.io/shiraz/compatibility/', 'https://spectre-oss.github.io/shiraz/compare/']) {
    expect(sitemapXml).toContain(`<loc>${url}</loc>`);
  }

  for (const path of ['/llms.txt', '/llms-full.txt']) {
    const res = await request.get(path);
    expect(res.ok()).toBe(true);
    const body = await res.text();
    expect(body).toContain('# Shiraz');
    expect(body).toContain('4,445');
  }

  const manifestRes = await request.get('/manifest.webmanifest');
  expect(manifestRes.ok()).toBe(true);
  expect((await manifestRes.json()).short_name).toBe('Shiraz');

  for (const [iconPath, size] of [['/apple-touch-icon.png', 180], ['/icon-192.png', 192], ['/icon-512.png', 512]]) {
    const res = await request.get(iconPath);
    expect(res.ok()).toBe(true);
    const png = await res.body();
    expect(png.subarray(1, 4).toString()).toBe('PNG');
    expect(png.readUInt32BE(16)).toBe(size);
    expect(png.readUInt32BE(20)).toBe(size);
  }

  const expectations = [
    ['/', ['Organization', 'WebSite', 'SoftwareApplication', 'HowTo', 'FAQPage']],
    ['/compatibility/', ['Organization', 'WebSite', 'WebPage', 'BreadcrumbList', 'Dataset', 'FAQPage']],
    ['/compare/', ['Organization', 'WebSite', 'WebPage', 'BreadcrumbList', 'ItemList', 'FAQPage']]
  ];
  for (const [route, expectedTypes] of expectations) {
    await page.goto(route);
    await expect(page.locator('link[rel="alternate"][type="text/plain"][href$="/llms.txt"]')).toHaveCount(1);
    await expect(page.locator('meta[name="robots"][content*="index"]')).toHaveCount(1);
    await expect(page.locator('meta[property="og:locale"][content="en_US"]')).toHaveCount(1);
    await expect(page.locator('meta[name="twitter:title"]')).toHaveCount(1);
    const ldRaw = await page.locator('script[type="application/ld+json"]').textContent();
    const graph = JSON.parse(ldRaw)['@graph'];
    const types = graph.map(node => node['@type']);
    for (const type of expectedTypes) expect(types).toContain(type);
    const faq = graph.find(node => node['@type'] === 'FAQPage');
    for (const entry of faq.mainEntity) {
      await expect(page.locator('summary', {hasText: entry.name})).toBeVisible();
    }
  }
});
