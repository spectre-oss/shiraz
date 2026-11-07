// SPDX-License-Identifier: GPL-3.0-only
import { test, expect } from '@playwright/test';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);

test('responsive site keeps table columns aligned and has no accessibility violations', async ({page}) => {
  for (const width of [1182, 390, 320]) {
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
