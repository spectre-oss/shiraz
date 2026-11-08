// SPDX-License-Identifier: GPL-3.0-only
import { readFileSync, existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const data = JSON.parse(readFileSync(`${root}landing/src/lib/compatibility.json`, 'utf8'));
if (data.schemaVersion !== 1 || !Array.isArray(data.applications)) throw Error('Invalid compatibility catalog');
const ids = new Set();
for (const app of data.applications) {
  for (const key of ['id','name','framework','status','observed','limitations','source']) {
    if (typeof app[key] !== 'string' || !app[key].trim()) throw Error(`Missing ${key} in compatibility record`);
  }
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(app.id) || ids.has(app.id)) throw Error(`Invalid or duplicate app id: ${app.id}`);
  ids.add(app.id);
  if (!['Partial','Blocked','Historical'].includes(app.status)) throw Error(`Unsupported status: ${app.status}`);
  const source = resolve(root, 'docs', app.source);
  if (!source.startsWith(resolve(root,'docs') + '/') || !source.endsWith('.md') || !existsSync(source)) throw Error(`Invalid evidence source: ${app.source}`);
}
console.log(`Validated ${ids.size} compatibility records`);

const inventory = JSON.parse(readFileSync(`${root}landing/src/lib/android-inventory.json`, 'utf8'));
const support = JSON.parse(readFileSync(`${root}landing/src/lib/android-support.json`, 'utf8'));
const integer = n => Number.isSafeInteger(n) && n >= 0;
if (inventory.schemaVersion !== 1 || !/^\d{4}-\d{2}-\d{2}$/.test(inventory.updated) || !/^[a-f0-9]{40}$/.test(inventory.sourceCommit)) throw Error('Invalid inventory provenance');
const classes = inventory.inventory;
if (classes.apiLevel !== 35 || !/^[a-f0-9]{64}$/.test(classes.referenceJarSha256) || !integer(classes.present) || !integer(classes.reference) || !classes.reference || classes.present > classes.reference) throw Error('Invalid class inventory');
const packages = new Set();
for (const row of classes.packages) {
  if (packages.has(row.package) || !/^android(?:\.[a-zA-Z0-9_]+)*$/.test(row.package) || !integer(row.present) || !integer(row.reference) || row.present > row.reference) throw Error('Invalid class package counts');
  packages.add(row.package);
}
for (const key of ['present','reference']) if (classes.packages.reduce((sum,row) => sum + row[key],0) !== classes[key]) throw Error('Class inventory totals disagree');
const planned = inventory.probeCatalog;
const plannedNames = new Set(); let plannedTotal = 0;
for (const row of planned.categories) {
  if (plannedNames.has(row.name) || !integer(row.automatic) || !integer(row.manual)) throw Error('Invalid planned assertion counts');
  plannedNames.add(row.name); plannedTotal += row.automatic + row.manual;
}
if (!integer(planned.total) || plannedTotal !== planned.total) throw Error('Planned assertion totals disagree');
// The static website can publish a versioned snapshot without the runtime tree.
const catalogPath = `${root}tests/api-probe/catalog.json`;
if (existsSync(catalogPath)) {
  const definitions = JSON.parse(readFileSync(catalogPath, 'utf8'));
  const categories = new Map();
  for (const item of definitions) {
    if (!['automatic','manual'].includes(item.mode)) throw Error('Invalid probe mode');
    const row = categories.get(item.category) || {name:item.category,automatic:0,manual:0};
    row[item.mode]++; categories.set(item.category,row);
  }
  const expected = [...categories.values()].sort((a,b)=>a.name.localeCompare(b.name));
  if (planned.total !== definitions.length || JSON.stringify(expected) !== JSON.stringify(planned.categories)) throw Error('Probe catalog changed: run make landing-compatibility-data');
}
if (support.schemaVersion !== 1 || !Array.isArray(support.areas) || !support.areas.length) throw Error('Missing subsystem support notes');
const areaIds = new Set();
for (const area of support.areas) {
  for (const key of ['id','name','scope','status','verification','notes','evidence']) if (typeof area[key] !== 'string' || !area[key].trim()) throw Error(`Missing subsystem ${key}`);
  if (!/^[a-z0-9-]+$/.test(area.id) || areaIds.has(area.id) || !['Partial','Unsupported','Unverified'].includes(area.status)) throw Error('Invalid subsystem identity/status');
  areaIds.add(area.id);
  const file = resolve(root,area.evidence);
  if (!/^(docs|java|native|tests)\//.test(area.evidence) || !file.startsWith(root) || (area.evidence.startsWith('docs/') && !existsSync(file))) throw Error('Invalid subsystem evidence path');
}
const snapshot = inventory.probeSnapshot;
if (snapshot) {
  const allowed = ['label','suiteVersion','suiteFingerprint','catalogSize','host','note','categories'];
  if (Object.keys(snapshot).some(key=>!allowed.includes(key)) || !/^[a-f0-9]{64}$/.test(snapshot.suiteFingerprint) || !integer(snapshot.catalogSize)) throw Error('Invalid or unsanitized probe snapshot');
  const states = ['PASS','FAIL','UNSUPPORTED','SKIPPED','TIMEOUT','NOT_RUN'];
  const names = new Set(); let total = 0;
  for (const row of snapshot.categories) {
    if (Object.keys(row).sort().join(',') !== 'counts,name' || names.has(row.name) || Object.keys(row.counts).length !== states.length || states.some(state=>!integer(row.counts[state]))) throw Error('Invalid recorded assertion counts');
    names.add(row.name); total += states.reduce((sum,state)=>sum+row.counts[state],0);
  }
  if (total !== snapshot.catalogSize) throw Error('Recorded assertions do not sum to the snapshot catalog');
}
console.log(`Validated ${support.areas.length} subsystem notes, ${classes.reference} reference classes and ${planned.total} planned assertions`);
