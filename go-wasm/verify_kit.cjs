// SPDX-License-Identifier: GPL-3.0-only
const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const version = fs.readFileSync(path.join(root, 'VERSION'), 'utf8').trim();
const base = fs.readFileSync(path.join(root, 'dist', `Shiraz-Browser-Base-${version}-x86_64.AppImage`));
const apk = fs.readFileSync(path.join(root, 'build/apk-fixture/fixture.apk'));
globalThis.crypto = crypto.webcrypto;
require(path.join(__dirname, 'wasm_exec.js'));

(async () => {
  const go = new Go();
  const wasm = fs.readFileSync(path.join(__dirname, 'shiraz-assemble.wasm'));
  const {instance} = await WebAssembly.instantiate(wasm, go.importObject);
  go.run(instance);
  const result = shirazBuildFromApk(new Uint8Array(base), new Uint8Array(apk), version);
  if (result.error) throw new Error(result.error);
  if (result.package !== 'org.shiraz.fixture' || result.bytes.length <= base.length) {
    throw new Error('assembled AppImage is missing the fixture payload');
  }
  console.log(`Browser kit assembled ${result.package}: ${result.bytes.length} bytes, SHA-256 ${result.sha256}`);
  process.exit(0);
})().catch(error => {
  console.error(error);
  process.exit(1);
});
