<script>
  // SPDX-License-Identifier: GPL-3.0-only
  import { onMount } from 'svelte';
  import { resolve } from '$app/paths';
  const base = resolve('/').replace(/\/$/, '');
  let format = $state('appimage');
  let architecture = $state('x86_64');
  let allowNetwork = $state(true);
  let sandboxMac = $state(true);
  let apkFile = $state();
  let baseFile = $state();
  let baseInput = $state();
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

  function formatLabel(kind) {
    if (kind === 'flatpak') return 'Flatpak';
    if (kind === 'macos') return 'macOS (.app sidecar)';
    return 'AppImage';
  }

  function expectedBaseFile(version, arch, kind) {
    if (kind === 'flatpak') return `base/Shiraz-Flatpak-Base-${version}-${arch}.flatpak-base`;
    if (kind === 'macos') return `base/Shiraz-macOS-Base-${version}-arm64.zip`;
    return `base/Shiraz-Browser-Base-${version}-${arch}.AppImage`;
  }

  function resolveEntry(data, arch, kind) {
    if (kind === 'macos') {
      return data.architectures?.arm64?.formats?.macos ?? data.architectures?.aarch64?.formats?.macos ?? null;
    }
    const archEntry = data.architectures?.[arch];
    if (!archEntry) return null;
    if (archEntry.formats?.[kind]) return archEntry.formats[kind];
    if (kind === 'appimage' && archEntry.base_file) return archEntry;
    return null;
  }

  function onFormatChange() {
    baseFile = undefined;
    if (baseInput) baseInput.value = '';
    loadRelease();
  }

  async function loadRelease() {
    const current = ++request;
    const selectedFormat = format;
    const selectedArch = selectedFormat === 'macos' ? 'arm64' : architecture;
    manifest = null;
    releaseVersion = 'Checking release…';
    try {
      const response = await fetch(`${base}/release.json`, {cache: 'no-store'});
      if (!response.ok) throw new Error('No published base manifest.');
      const data = await response.json();
      const entry = resolveEntry(data, selectedArch, selectedFormat);
      if (!/^\d+\.\d+\.\d+$/.test(data.version) || !entry ||
          entry.base_file !== expectedBaseFile(data.version, selectedArch, selectedFormat) ||
          !/^[0-9a-f]{64}$/.test(entry.base_sha256) || !Number.isSafeInteger(entry.base_size) || entry.base_size < 1) {
        throw new Error('Invalid runtime manifest.');
      }
      if (current !== request) return;
      manifest = {...entry, version: data.version, format: selectedFormat, architecture: selectedArch};
      releaseVersion = `v${data.version}`;
      releaseDetail = `${selectedArch} · ${formatLabel(selectedFormat)} · ${(entry.base_size / 1048576).toFixed(0)} MiB · checksum verified before packaging`;
    } catch {
      if (current !== request) return;
      releaseVersion = 'Local base available';
      if (selectedFormat === 'flatpak') {
        releaseDetail = 'Select a local Shiraz Flatpak base (.flatpak-base) below, or use the published site once a Flatpak release is available.';
      } else if (selectedFormat === 'macos') {
        releaseDetail = 'Select a local signed Shiraz macOS base (.zip containing Shiraz.app) below. Shiraz.app stays byte-for-byte untouched.';
      } else {
        releaseDetail = 'Select a local Shiraz browser base below, or use the published site for automatic runtime downloads.';
      }
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
    // Go registers the synchronous assembly entry points before yielding.
    go.run(instance).catch(() => {});
    if (typeof globalThis.shirazBuildFromApk !== 'function' || typeof globalThis.shirazBuildFlatpakFromApk !== 'function') {
      throw new Error('Builder did not initialize.');
    }
  }

  async function assemble() {
    if (!apkFile?.[0]) { status = 'Select an APK to begin.'; state = 'error'; return; }
    const selectedFormat = format;
    const selectedArch = selectedFormat === 'macos' ? 'arm64' : architecture;
    const selectedNetwork = allowNetwork;
    const selectedSandbox = sandboxMac;
    busy = true;
    state = '';
    try {
      status = 'Preparing builder…';
      wasmReady ??= loadWasm().catch(error => { wasmReady = null; throw error; });
      await wasmReady;
      if (selectedFormat === 'macos' && typeof globalThis.shirazBuildMacAppFromApk !== 'function') {
        throw new Error('Builder runtime does not support macOS sidecar packaging.');
      }
      let version, bytes;
      const local = baseFile?.[0];
      if (local) {
        if (selectedFormat === 'macos') {
          const match = /^Shiraz-(?:macOS-Base-)?(\d+\.\d+\.\d+)(?:-macos)?-arm64\.zip$/.exec(local.name);
          if (!match) {
            throw new Error('Select a versioned Shiraz macOS base archive (Shiraz-macOS-Base-<ver>-arm64.zip).');
          }
          version = match[1];
        } else {
          const pattern = selectedFormat === 'flatpak'
            ? /^Shiraz-Flatpak-Base-(\d+\.\d+\.\d+)-(x86_64|aarch64)\.flatpak-base$/
            : /^Shiraz-Browser-Base-(\d+\.\d+\.\d+)-(x86_64|aarch64)\.AppImage$/;
          const match = pattern.exec(local.name);
          if (!match) {
            throw new Error(selectedFormat === 'flatpak'
              ? 'Select a versioned Shiraz Flatpak base (.flatpak-base).'
              : 'Select a versioned Shiraz browser base AppImage.');
          }
          if (match[2] !== selectedArch) throw new Error('Local base does not match the selected architecture.');
          version = match[1];
        }
        status = 'Reading local base…';
        bytes = await local.arrayBuffer();
      } else {
        if (!manifest || manifest.format !== selectedFormat || manifest.architecture !== selectedArch) {
          if (selectedFormat === 'flatpak') {
            throw new Error('Select a local Flatpak base (.flatpak-base). The published Flatpak base is unavailable.');
          }
          if (selectedFormat === 'macos') {
            throw new Error('Select a local macOS base (.zip). A published macOS base is unavailable.');
          }
          throw new Error('Select a local base AppImage. The published base is unavailable.');
        }
        const selected = manifest;
        version = selected.version;
        status = `Downloading ${formatLabel(selectedFormat)} runtime v${version}…`;
        const response = await fetch(`${base}/${selected.base_file}`);
        if (!response.ok) throw new Error(`Base download failed: HTTP ${response.status}`);
        bytes = await response.arrayBuffer();
        if (bytes.byteLength !== selected.base_size) throw new Error('Base download is incomplete.');
        const hash = await crypto.subtle.digest('SHA-256', bytes);
        const digest = [...new Uint8Array(hash)].map(byte => byte.toString(16).padStart(2, '0')).join('');
        if (digest !== selected.base_sha256) throw new Error('Base checksum mismatch.');
      }
      status = 'Reading APK…';
      const apk = await apkFile[0].arrayBuffer();
      if (apk.byteLength !== apkFile[0].size || apk.byteLength < 4 || new Uint8Array(apk)[0] !== 80 || new Uint8Array(apk)[1] !== 75) {
        throw new Error('Select a complete APK/ZIP file.');
      }
      status = selectedFormat === 'flatpak'
        ? 'Assembling Flatpak…'
        : selectedFormat === 'macos'
          ? 'Assembling macOS sidecar ZIP…'
          : 'Assembling AppImage…';
      await new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 0)));
      const result = selectedFormat === 'flatpak'
        ? globalThis.shirazBuildFlatpakFromApk(new Uint8Array(bytes), new Uint8Array(apk), version, {allowNetwork: selectedNetwork})
        : selectedFormat === 'macos'
          ? globalThis.shirazBuildMacAppFromApk(new Uint8Array(bytes), new Uint8Array(apk), version, {allowNetwork: selectedNetwork, sandbox: selectedSandbox})
          : globalThis.shirazBuildFromApk(new Uint8Array(bytes), new Uint8Array(apk), version);
      if (result.error) throw new Error(result.error);
      if (result.architecture !== selectedArch) throw new Error('Base architecture does not match the selected device.');
      const filename = selectedFormat === 'flatpak'
        ? (result.filename || `${result.appId}-Shiraz-${version}-${selectedArch}.flatpak`)
        : selectedFormat === 'macos'
          ? (result.filename || `${result.package}-macos-arm64.zip`)
          : `${result.package}-Shiraz-${version}-${selectedArch}.AppImage`;
      if (downloadUrl) URL.revokeObjectURL(downloadUrl);
      downloadUrl = URL.createObjectURL(new Blob([result.bytes], {type: 'application/octet-stream'}));
      const link = document.createElement('a');
      link.href = downloadUrl;
      link.download = filename;
      link.click();
      clearTimeout(revokeTimer);
      revokeTimer = setTimeout(() => { URL.revokeObjectURL(downloadUrl); downloadUrl = null; }, 60000);
      state = 'success';
      status = selectedFormat === 'flatpak'
        ? `Flatpak ready. SHA-256: ${result.sha256}. On Linux, install with: flatpak install --user ./${filename} && flatpak run ${result.appId}`
        : selectedFormat === 'macos'
          ? `macOS sidecar ZIP ready. SHA-256: ${result.sha256}. Unzip on Apple Silicon macOS — Shiraz.app remains byte-for-byte untouched beside AppData/original.apk.`
          : `AppImage ready. SHA-256: ${result.sha256}. On Linux, make it executable with chmod +x before running.`;
    } catch (error) { state = 'error'; status = error.message || String(error); }
    finally { busy = false; }
  }
  onMount(() => {
    loadRelease();
    return () => { request++; clearTimeout(revokeTimer); if (downloadUrl) URL.revokeObjectURL(downloadUrl); };
  });
</script>

<section class="builder" id="builder" aria-labelledby="builder-title">
  <header class="builder-heading"><h2 id="builder-title">Your APK.<br><span>A desktop app.</span></h2><p>Drop in a supported APK. Get a Linux AppImage, sandboxed Flatpak, or macOS sidecar ZIP back.<br>Built here, in your browser. Nothing is uploaded.</p></header>
  <form class="builder-panel" onsubmit={(event) => { event.preventDefault(); assemble(); }} aria-busy={busy}>
    <fieldset disabled={busy}>
      <legend class="visually-hidden">Desktop package build options</legend>
      <fieldset class="format-options">
        <legend>Package format</legend>
        <div class="format-cards">
          <label class="format-card"><input type="radio" name="format" value="appimage" bind:group={format} onchange={onFormatChange} /><span><strong>AppImage</strong><small>Linux portable single file · chmod +x and run</small></span></label>
          <label class="format-card"><input type="radio" name="format" value="flatpak" bind:group={format} onchange={onFormatChange} /><span><strong>Flatpak</strong><small>Linux sandboxed .flatpak · flatpak install --user</small></span></label>
          <label class="format-card format-card-wide"><input type="radio" name="format" value="macos" bind:group={format} onchange={onFormatChange} /><span><strong>macOS (.app sidecar)</strong><small>Apple Silicon ZIP · keeps signed Shiraz.app byte-for-byte untouched beside AppData/</small><em>Experimental</em></span></label>
        </div>
      </fieldset>
      {#if format !== 'macos'}
      <fieldset class="architecture-options">
        <legend>Linux device architecture</legend>
        <div class="architecture-cards">
          <label class="architecture-card"><input type="radio" name="architecture" value="x86_64" bind:group={architecture} onchange={loadRelease} /><span><strong>x86_64</strong><small>Intel / AMD</small></span></label>
          <label class="architecture-card"><input type="radio" name="architecture" value="aarch64" bind:group={architecture} onchange={loadRelease} /><span><strong>ARM64</strong><small>Linux phones / ARM computers</small><em>Experimental</em></span></label>
        </div>
      </fieldset>
      {/if}
      {#if format === 'flatpak'}
        <label class="builder-checkbox" for="flatpak-network"><input id="flatpak-network" type="checkbox" bind:checked={allowNetwork} /><span>Allow network access in the Flatpak sandbox</span></label>
      {:else if format === 'macos'}
        <label class="builder-checkbox" for="macos-sandbox"><input id="macos-sandbox" type="checkbox" bind:checked={sandboxMac} /><span>Enable macOS Seatbelt sandbox by default</span></label>
      {/if}
      <div class="builder-base"><strong>{releaseVersion}</strong><p>{releaseDetail}</p></div>
      <details><summary>{format === 'flatpak' ? 'Use a local Flatpak base' : format === 'macos' ? 'Use a local macOS base (.zip)' : 'Use a local base AppImage'}</summary><label for="base">{format === 'flatpak' ? 'Shiraz Flatpak base (.flatpak-base)' : format === 'macos' ? 'Shiraz macOS base archive (.zip)' : 'Shiraz browser base (.AppImage)'}</label><input id="base" type="file" accept={format === 'flatpak' ? '.flatpak-base' : format === 'macos' ? '.zip' : '.AppImage'} bind:files={baseFile} bind:this={baseInput} /></details>
      <label for="apk">Android™ APK</label><input id="apk" type="file" accept=".apk" bind:files={apkFile} />
      <button class="button primary" id="assemble" type="submit">{busy ? 'Building…' : format === 'flatpak' ? 'Build Flatpak' : format === 'macos' ? 'Build macOS ZIP' : 'Build AppImage'}<svg class="icon" data-icon="arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="M13.5 4.5 21 12m0 0-7.5 7.5"/><path d="M21 12H3"/></svg></button>
    </fieldset>
    <p class="builder-status" id="status" data-state={state} role="status" aria-live="polite">{status}</p>
    <p class="builder-note">Packaged locally with WebAssembly. Compatibility, Linux phones, and macOS sidecar packaging are experimental. Run only APKs you trust.</p>
  </form>
</section>
