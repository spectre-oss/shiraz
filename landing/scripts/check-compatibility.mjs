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
