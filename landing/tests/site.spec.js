// SPDX-License-Identifier: GPL-3.0-only
import { test, expect } from '@playwright/test';
import { createRequire } from 'node:module';
import catalog from '../src/lib/compatibility.json' with {type:'json'};
const require = createRequire(import.meta.url);

test('responsive site keeps table columns aligned and has no accessibility violations', async ({page}) => {
  for (const width of [2560, 1182, 390, 320]) {
    await page.setViewportSize({width, height: 881});
    await page.goto('/');
    await expect(page.locator('#builder')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const cells = await page.locator('.comparison-table tbody tr').evaluateAll(rows => rows.map(row => [...row.children].map(cell => Math.round(cell.getBoundingClientRect().left))));
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

test('reduced motion leaves the decorative shapes static', async ({page}) => {
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.goto('/');
  expect(await page.locator('.tonal-shape').evaluateAll(shapes => shapes.every(shape => shape.getAnimations().length === 0))).toBe(true);
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
  await page.setViewportSize({width:390,height:844});
  await page.goto('/compatibility/');
  expect(await page.evaluate(()=>document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.addScriptTag({path:require.resolve('axe-core/axe.min.js')});
  const violations = await page.evaluate(async()=> (await window.axe.run(document,{runOnly:{type:'tag',values:['wcag2a','wcag2aa','wcag21aa']}})).violations);
  expect(violations).toEqual([]);
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
  await expect(coverage.getByRole('heading',{name:'Historical targeted network run'})).toBeVisible();
  await expect(coverage.getByRole('list',{name:'Recorded probe status totals'})).toContainText('NOT RUN');
  await expect(coverage.getByRole('region',{name:'Historical probe results'})).toContainText('Network');
});
