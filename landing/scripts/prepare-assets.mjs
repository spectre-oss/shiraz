// SPDX-License-Identifier: GPL-3.0-only
// Generated Go assets remain ignored; APKs and runtime bases are never source assets.
import { copyFileSync, existsSync } from 'node:fs';
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

// Raster preview for Open Graph clients that do not support SVG cards.
const { default: sharp } = await import('sharp');
await sharp(`${root}landing/src/lib/social-card.svg`).png().toFile(`${root}landing/static/social-card.png`);
