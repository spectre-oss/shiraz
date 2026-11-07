<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  const base = resolve('/').replace(/\/$/, '');
  let architecture = $state('x86_64');
  let apkFile = $state();
  let baseFile = $state();
  let manifest = $state(null);
  let releaseVersion = $state('Checking release…');
  let releaseDetail = $state('The matching runtime downloads only when you build.');
  let busy = $state(false);
  let status = $state('Ready. Select an APK to begin.');
  let state = $state('');
  let wasmReady;
  let request = 0;
  let downloadUrl;
  let revokeTimer;

  async function loadRelease() {
    const current = ++request;
    manifest = null;
    releaseVersion = 'Checking release…';
    try {
      const response = await fetch(`${base}/release.json`, {cache: 'no-store'});
      if (!response.ok) throw new Error('No published base manifest.');
      const data = await response.json();
      const entry = data.architectures?.[architecture];
      if (!/^\d+\.\d+\.\d+$/.test(data.version) || !entry ||
          entry.base_file !== `base/Shiraz-Browser-Base-${data.version}-${architecture}.AppImage` ||
          !/^[0-9a-f]{64}$/.test(entry.base_sha256) || !Number.isSafeInteger(entry.base_size) || entry.base_size < 1) {
        throw new Error('Invalid runtime manifest.');
      }
      if (current !== request) return;
      manifest = {...entry, version: data.version};
      releaseVersion = `v${data.version}`;
      releaseDetail = `${architecture} · ${(entry.base_size / 1048576).toFixed(0)} MiB · checksum verified before packaging`;
    } catch {
      if (current !== request) return;
      releaseVersion = 'Local base available';
      releaseDetail = 'Select a local Shiraz browser base below, or use the published site for automatic runtime downloads.';
    }
  }

  async function loadWasm() {
    if (!globalThis.Go) {
      await new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = `${base}/wasm_exec.js`;
        script.onload = resolve;
        script.onerror = () => { script.remove(); reject(new Error('Cannot load the builder runtime. Please reload and try again.')); };
        document.head.append(script);
      });
    }
    const go = new globalThis.Go();
    const response = await fetch(`${base}/shiraz-assemble.wasm`);
    if (!response.ok) throw new Error(`Builder download failed: HTTP ${response.status}`);
    const {instance} = await WebAssembly.instantiate(await response.arrayBuffer(), go.importObject);
    // Go registers the synchronous assembly entry point before yielding.
    go.run(instance).catch(() => {});
    if (typeof globalThis.shirazBuildFromApk !== 'function') throw new Error('Builder did not initialize.');
  }

  async function assemble() {
    if (!apkFile?.[0]) { status = 'Select an APK to begin.'; state = 'error'; return; }
    busy = true;
    state = '';
    try {
      status = 'Preparing builder…';
      wasmReady ??= loadWasm().catch(error => { wasmReady = null; throw error; });
      await wasmReady;
      let version, bytes;
      const local = baseFile?.[0];
      if (local) {
        const match = /^Shiraz-Browser-Base-(\d+\.\d+\.\d+)-(x86_64|aarch64)\.AppImage$/.exec(local.name);
        if (!match) throw new Error('Select a versioned Shiraz browser base AppImage.');
        if (match[2] !== architecture) throw new Error('Local base does not match the selected architecture.');
        version = match[1];
        status = 'Reading local base…';
        bytes = await local.arrayBuffer();
      } else {
        if (!manifest) throw new Error('Select a local base AppImage. The published base is unavailable.');
        const selected = manifest;
        version = selected.version;
        status = `Downloading runtime v${version}…`;
        const response = await fetch(`${base}/${selected.base_file}`);
        if (!response.ok) throw new Error(`Base download failed: HTTP ${response.status}`);
        bytes = await response.arrayBuffer();
        if (bytes.byteLength !== selected.base_size) throw new Error('Base download is incomplete.');
        const hash = await crypto.subtle.digest('SHA-256', bytes);
        const digest = [...new Uint8Array(hash)].map(byte => byte.toString(16).padStart(2,'0')).join('');
        if (digest !== selected.base_sha256) throw new Error('Base checksum mismatch.');
      }
      status = 'Reading APK…';
      const apk = await apkFile[0].arrayBuffer();
      if (apk.byteLength !== apkFile[0].size || apk.byteLength < 4 || new Uint8Array(apk)[0] !== 80 || new Uint8Array(apk)[1] !== 75) throw new Error('Select a complete APK/ZIP file.');
      status = 'Assembling AppImage…';
      await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 0)));
      const result = globalThis.shirazBuildFromApk(new Uint8Array(bytes), new Uint8Array(apk), version);
      if (result.error) throw new Error(result.error);
      if (result.architecture !== architecture) throw new Error('Base architecture does not match the selected device.');
      if (downloadUrl) URL.revokeObjectURL(downloadUrl);
      downloadUrl = URL.createObjectURL(new Blob([result.bytes], {type:'application/octet-stream'}));
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = `${result.package}-Shiraz-${version}-${architecture}.AppImage`;
      link.click();
      clearTimeout(revokeTimer);
      revokeTimer = setTimeout(() => { URL.revokeObjectURL(downloadUrl); downloadUrl = null; }, 60000);
      state = 'success';
      status = `AppImage ready. SHA-256: ${result.sha256}. On Linux, make it executable with chmod +x before running.`;
    } catch (error) { state = 'error'; status = error.message || String(error); }
    finally { busy = false; }
  }
  onMount(() => {
    loadRelease();
    return () => { request++; clearTimeout(revokeTimer); if (downloadUrl) URL.revokeObjectURL(downloadUrl); };
  });
</script>

<section class="builder" id="builder" aria-labelledby="builder-title">
  <header class="builder-heading"><p class="eyebrow">Don’t believe it? Try it now!</p><h2 id="builder-title">Your APK.<br><span>A Linux app.</span></h2><p>Package a supported Android™ app into an AppImage, right here.<br>Your APK stays on your device. The AppImage is built in your browser.</p></header>
  <form class="builder-panel" onsubmit={(event) => { event.preventDefault(); assemble(); }} aria-busy={busy}>
    <fieldset disabled={busy}>
      <legend class="visually-hidden">AppImage build options</legend>
      <fieldset class="architecture-options">
        <legend>Linux device architecture</legend>
        <div class="architecture-cards">
          <label class="architecture-card"><input type="radio" name="architecture" value="x86_64" bind:group={architecture} onchange={loadRelease} /><span><strong>x86_64</strong><small>Intel / AMD</small></span></label>
          <label class="architecture-card"><input type="radio" name="architecture" value="aarch64" bind:group={architecture} onchange={loadRelease} /><span><strong>ARM64</strong><small>Linux phones / ARM computers</small><em>Experimental</em></span></label>
        </div>
      </fieldset>
      <div class="builder-base"><strong>{releaseVersion}</strong><p>{releaseDetail}</p></div>
      <details><summary>Use a local base AppImage</summary><label for="base">Shiraz browser base</label><input id="base" type="file" accept=".AppImage" bind:files={baseFile} /></details>
      <label for="apk">Android™ APK</label><input id="apk" type="file" accept=".apk" bind:files={apkFile} />
      <button class="button primary" id="assemble" type="submit">{busy ? 'Building…' : 'Build AppImage'}<svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></button>
    </fieldset>
    <p class="builder-status" id="status" data-state={state} role="status" aria-live="polite">{status}</p>
    <p class="builder-note">Packaging runs locally in your browser using WebAssembly. App compatibility and Linux phone support remain experimental. Only run APKs you trust.</p>
  </form>
</section>
