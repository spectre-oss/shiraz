// SPDX-License-Identifier: GPL-3.0-only
// Generated Go assets remain ignored; APKs and runtime bases are never source assets.
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../../', import.meta.url));
const wasm = `${root}go-wasm/shiraz-assemble.wasm`;
if (process.env.SHIRAZ_PREBUILT_WASM !== '1') {
  execFileSync('go', ['build', '-o', wasm, '.'], {cwd: `${root}go-wasm`, env: {...process.env, GOOS:'js', GOARCH:'wasm'}, stdio:'inherit'});
  const goroot = execFileSync('go', ['env', 'GOROOT'], {encoding:'utf8'}).trim();
  const runtime = existsSync(`${goroot}/lib/wasm/wasm_exec.js`) ? `${goroot}/lib/wasm/wasm_exec.js` : `${goroot}/misc/wasm/wasm_exec.js`;
  copyFileSync(runtime, `${root}go-wasm/wasm_exec.js`);
}
for (const name of ['shiraz-assemble.wasm', 'wasm_exec.js']) copyFileSync(`${root}go-wasm/${name}`, `${root}landing/static/${name}`);

// Copy unkernel's prebuilt Linux-in-Wasm bundle from node_modules into ignored static/.
const unkernelWasm = `${root}landing/node_modules/unkernel/dist/unkernel.wasm`;
if (existsSync(unkernelWasm)) {
  mkdirSync(`${root}landing/static/unkernel`, { recursive: true });
  copyFileSync(unkernelWasm, `${root}landing/static/unkernel/unkernel.wasm`);
}

// Raster preview for Open Graph clients and PNG touch/manifest icons from SVG sources.
const { default: sharp } = await import('sharp');
await sharp(`${root}landing/src/lib/social-card.svg`).png().toFile(`${root}landing/static/social-card.png`);
const ghostSvg = readFileSync(`${root}landing/static/spectre.svg`, 'utf8');
for (const [name, size] of [['apple-touch-icon.png', 180], ['icon-192.png', 192], ['icon-512.png', 512]]) {
  const pad = Math.round(size * 0.16);
  const inner = size - pad * 2;
  const icon = await sharp(Buffer.from(ghostSvg)).resize(inner, inner, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } }).png().toBuffer();
  await sharp({ create: { width: size, height: size, channels: 4, background: '#2b0f1c' } })
    .composite([{ input: icon, left: pad, top: pad }])
    .png()
    .toFile(`${root}landing/static/${name}`);
}

// Keep llms.txt and llms-full.txt synchronized with current VERSION and compatibility JSONs.
execFileSync(process.execPath, [`${root}landing/scripts/build-seo.mjs`], { stdio: 'inherit' });

// The launcher film and its poster are release assets, never source. media.json
// pins their size and SHA-256. A file with other bytes stops the build. A file
// that is absent and cannot be fetched is left out, and the page then shows no player.
const media = JSON.parse(readFileSync(`${root}landing/src/lib/media.json`, 'utf8'));
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
mkdirSync(`${root}landing/static/media`, { recursive: true });
for (const { file, bytes, sha256 } of [media.video, media.poster]) {
  const path = `${root}landing/static/media/${file}`;
  if (existsSync(path)) {
    if (digest(readFileSync(path)) !== sha256) throw new Error(`${path} does not match media.json; run scripts/make-media.mjs again or delete the file`);
    continue;
  }
  if (process.env.SHIRAZ_OFFLINE) { console.warn(`MEDIA_MISSING ${file} (offline)`); continue; }
  try {
    const response = await fetch(`https://github.com/spectre-oss/shiraz/releases/download/${media.tag}/${file}`, { signal: AbortSignal.timeout(60000) });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    // Refuse before reading when the announced size is not the pinned one.
    if (Number(response.headers.get('content-length')) !== bytes) throw new Error('unexpected size');
    const body = Buffer.from(await response.arrayBuffer());
    if (body.length !== bytes || digest(body) !== sha256) throw new Error('checksum mismatch');
    writeFileSync(path, body);
    console.log(`MEDIA_READY ${file}`);
  } catch (error) {
    console.warn(`MEDIA_MISSING ${file} (${error.message})`);
  }
}
