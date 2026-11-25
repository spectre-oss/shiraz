// SPDX-License-Identifier: GPL-3.0-only
// Client-side browser Android APK runner powered by Shiraz (aarch64 Linux runtime)
// running inside pithings/unkernel (Linux 7.3 + kwemu aarch64 on WebAssembly).
// Nothing is uploaded: the APK stays in browser memory and runs locally.

let unkernelPromise = null;
function loadUnkernel() {
  unkernelPromise ??= import('unkernel/web').catch(error => {
    unkernelPromise = null;
    throw error;
  });
  return unkernelPromise;
}

export const DATA_DISK_NAME = 'shiraz-data';
export const DATA_DISK_VERSION = 'v1';
const OPFS_SNAPSHOT_DIR = 'shiraz-vm-snapshots';
const SNAPSHOT_SCHEMA = 'v1';

const encoder = new TextEncoder();
const decoder = new TextDecoder();

// Reusable booted & mounted Linux VM pool for instant repeat launches / APK switches.
let warmPool = null;

/** Check whether this browser supports WebAssembly JSPI required by unkernel. */
export async function checkBrowserSupport() {
  const { checkSupport } = await loadUnkernel();
  return await checkSupport();
}

/** Report Cross-Origin Isolation / SMP multi-core capability for this tab. */
export function getIsolationCapabilities() {
  const isolated = Boolean(globalThis.crossOriginIsolated);
  const sab = typeof SharedArrayBuffer === 'function';
  const cores = Math.max(1, Number(globalThis.navigator?.hardwareConcurrency) || 1);
  const smpReady = isolated && sab;
  const recommendedCpus = smpReady ? Math.min(4, Math.max(2, Math.floor(cores / 2))) : 1;
  return { isolated, sharedArrayBuffer: sab, hardwareConcurrency: cores, smpReady, recommendedCpus };
}

/** Resolve effective vCPU count given user preference and worker/isolation availability. */
export function resolveCpuCount(cpuSetting, useWorker) {
  if (!useWorker) return 1;
  const caps = getIsolationCapabilities();
  if (!caps.smpReady) return 1;
  if (cpuSetting === 'auto' || cpuSetting === undefined || cpuSetting === null) {
    return caps.recommendedCpus;
  }
  const parsed = Number.parseInt(String(cpuSetting), 10);
  if (!Number.isFinite(parsed) || parsed <= 1) return 1;
  return Math.min(4, Math.max(1, parsed));
}

function semverAtLeast(version, major, minor, patch = 0) {
  const match = /^(\d+)\.(\d+)\.(\d+)$/.exec(String(version || ''));
  if (!match) return false;
  const [a, b, c] = [Number(match[1]), Number(match[2]), Number(match[3])];
  if (a !== major) return a > major;
  if (b !== minor) return b > minor;
  return c >= patch;
}

/** True when a runtime source is known to include direct /dev/fb0 presentation support. */
export function runtimeSupportsDirectFb(runtimeSource) {
  if (!runtimeSource) return false;
  if (Array.isArray(runtimeSource.features) && runtimeSource.features.includes('fb0')) {
    return true;
  }
  return semverAtLeast(runtimeSource.version, 0, 367, 0);
}

/**
 * Decode a binary P6 PPM buffer directly into a reusable RGBA ImageData-compatible buffer.
 * Returns null if the buffer is incomplete or malformed.
 */
export function decodePpmToRgba(bytes, reuseBuffer) {
  if (!bytes || bytes.length < 11 || bytes[0] !== 0x50 || bytes[1] !== 0x36) return null;
  let offset = 2;
  const tokens = [];
  while (tokens.length < 3 && offset < bytes.length) {
    while (offset < bytes.length && bytes[offset] <= 0x20) offset++;
    if (offset < bytes.length && bytes[offset] === 0x23) {
      while (offset < bytes.length && bytes[offset] !== 0x0a) offset++;
      continue;
    }
    const start = offset;
    while (offset < bytes.length && bytes[offset] > 0x20 && bytes[offset] !== 0x23) offset++;
    if (offset === start) return null;
    tokens.push(decoder.decode(bytes.subarray(start, offset)));
  }
  if (tokens.length !== 3) return null;
  // Exactly one whitespace separator byte follows maxval in P6 PPM.
  if (offset >= bytes.length || bytes[offset] > 0x20) return null;
  offset++;
  const width = Number.parseInt(tokens[0], 10);
  const height = Number.parseInt(tokens[1], 10);
  const maxVal = Number.parseInt(tokens[2], 10);
  if (!Number.isSafeInteger(width) || !Number.isSafeInteger(height) || width < 1 || height < 1 || maxVal !== 255) {
    return null;
  }
  const pixelCount = width * height;
  if (offset + pixelCount * 3 > bytes.length) return null;
  const rgba =
    reuseBuffer && reuseBuffer.length === pixelCount * 4
      ? reuseBuffer
      : new Uint8ClampedArray(pixelCount * 4);
  for (let i = 0, s = offset, d = 0; i < pixelCount; i++, s += 3, d += 4) {
    rgba[d] = bytes[s];
    rgba[d + 1] = bytes[s + 1];
    rgba[d + 2] = bytes[s + 2];
    rgba[d + 3] = 255;
  }
  return { width, height, data: rgba };
}

/**
 * Wrap a local browser File/Blob as a read-only unkernel BlockDevice.
 * Only the 4 KiB sectors actually read by Linux/ART are sliced from the File.
 */
export function createBlobBlockDevice(file, readOnlyFlag = 1) {
  const alignedSize = Math.floor(file.size / 512) * 512;
  return {
    size: alignedSize,
    blockSize: 4096,
    flags: readOnlyFlag,
    readOnly: true,
    async read(off, views) {
      let total = 0;
      for (const view of views) total += view.length;
      const chunk = new Uint8Array(await file.slice(off, off + total).arrayBuffer());
      let cursor = 0;
      for (const view of views) {
        const end = Math.min(cursor + view.length, chunk.length);
        if (end > cursor) view.set(chunk.subarray(cursor, end));
        if (end - cursor < view.length) view.fill(0, end - cursor);
        cursor += view.length;
      }
    },
    write() {
      throw new Error('Read-only runtime disk');
    }
  };
}

let goWasmPromise = null;

/** Load Shiraz's Go/WASM APK inspector once per page lifetime. */
export function ensureApkInspector(base) {
  goWasmPromise ??= (async () => {
    if (!globalThis.Go) {
      await new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = `${base}/wasm_exec.js`;
        script.onload = resolve;
        script.onerror = () => {
          script.remove();
          reject(new Error('Cannot load Shiraz APK inspector runtime.'));
        };
        document.head.append(script);
      });
    }
    const go = new globalThis.Go();
    const response = await fetch(`${base}/shiraz-assemble.wasm`);
    if (!response.ok) throw new Error(`Inspector WASM download failed: HTTP ${response.status}`);
    const { instance } = await WebAssembly.instantiate(await response.arrayBuffer(), go.importObject);
    go.run(instance).catch(() => {});
    if (typeof globalThis.shirazPrepareBrowserApk !== 'function' && typeof globalThis.shirazInspectApk !== 'function') {
      throw new Error('Shiraz APK inspector did not initialize.');
    }
  })().catch(error => {
    goWasmPromise = null;
    throw error;
  });
  return goWasmPromise;
}

async function sha256Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
}

/**
 * Inspect an APK locally in WebAssembly and produce launch.properties + application.properties
 * for Shiraz's aarch64 Linux runtime.
 */
export async function inspectApkForBrowser(base, apkBytes, runtimeVersion) {
  if (!apkBytes || apkBytes.byteLength < 4 || apkBytes[0] !== 80 || apkBytes[1] !== 75) {
    throw new Error('Select a valid Android APK (ZIP archive).');
  }
  await ensureApkInspector(base);
  const version = /^\d+\.\d+\.\d+$/.test(runtimeVersion || '') ? runtimeVersion : '0.363.0';
  if (typeof globalThis.shirazPrepareBrowserApk === 'function') {
    const raw = globalThis.shirazPrepareBrowserApk(apkBytes, version);
    if (raw && typeof raw === 'object' && raw.error) throw new Error(raw.error);
    return JSON.parse(raw);
  }
  const raw = globalThis.shirazInspectApk(apkBytes, version, 'aarch64');
  if (raw && typeof raw === 'object' && raw.error) throw new Error(raw.error);
  const meta = JSON.parse(raw);
  const digest = await sha256Hex(apkBytes);
  const launchProperties = [
    `activity=${meta.activity}`,
    'aot=false',
    `application=${meta.application}`,
    'digitalis=false',
    'native=false',
    `package=${meta.package}`,
    `sha256=${digest}`,
    `version=${version}`
  ].join('\n') + '\n';
  return {
    architecture: 'aarch64',
    package: meta.package,
    label: meta.label || meta.package,
    application: meta.application,
    activity: meta.activity,
    version,
    sha256: digest,
    native: false,
    native_libs: [],
    launch_properties: launchProperties,
    application_properties: meta.application_properties
  };
}

/** Resolve a DOM KeyboardEvent into a Shiraz SHIRAZ_LIVE_INPUT command line, if supported. */
export function keyboardEventToLiveCommands(event, isDown) {
  const special = {
    Escape: 'Escape',
    Enter: 'Return',
    Backspace: 'Backspace',
    Tab: 'Tab',
    Delete: 'Delete',
    ArrowUp: 'Up',
    ArrowDown: 'Down',
    ArrowLeft: 'Left',
    ArrowRight: 'Right',
    Home: 'Home',
    End: 'End',
    PageUp: 'PageUp',
    PageDown: 'PageDown',
    ' ': 'Space'
  };
  const sdlName = special[event.key];
  if (sdlName) {
    return [`${isDown ? 'keydown' : 'keyup'} "${sdlName}"\n`];
  }
  if (isDown && !event.ctrlKey && !event.metaKey && !event.altKey && event.key.length === 1) {
    const bytes = encoder.encode(event.key);
    if (bytes.length > 0 && bytes.length < 31) {
      const escaped = event.key.replaceAll('\\', '\\\\').replaceAll('"', '\\"');
      return [`text "${escaped}"\n`];
    }
  }
  return [];
}

// --- OPFS Warm VM Snapshot storage (strictly local, zero telemetry) ---

async function openSnapshotDir(create = false) {
  if (typeof navigator === 'undefined' || typeof navigator.storage?.getDirectory !== 'function') {
    return null;
  }
  try {
    const root = await navigator.storage.getDirectory();
    return await root.getDirectoryHandle(OPFS_SNAPSHOT_DIR, { create });
  } catch {
    return null;
  }
}

async function makeSnapshotFilename(descriptor) {
  const hex = await sha256Hex(encoder.encode(JSON.stringify(descriptor)));
  return `vm-${SNAPSHOT_SCHEMA}-${hex.slice(0, 24)}.snap`;
}

async function loadOpfsSnapshot(filename) {
  const dir = await openSnapshotDir(false);
  if (!dir) return null;
  try {
    const handle = await dir.getFileHandle(filename);
    const file = await handle.getFile();
    if (!file || file.size < 64) return null;
    return new Uint8Array(await file.arrayBuffer());
  } catch {
    return null;
  }
}

async function deleteOpfsSnapshot(filename) {
  const dir = await openSnapshotDir(false);
  if (!dir) return;
  try {
    await dir.removeEntry(filename);
  } catch {
    // Ignore missing entry.
  }
}

async function saveOpfsSnapshot(filename, bytes) {
  const dir = await openSnapshotDir(true);
  if (!dir) return false;
  try {
    // Keep only the current snapshot so OPFS storage remains bounded.
    if (typeof dir.keys === 'function') {
      for await (const name of dir.keys()) {
        if (name !== filename) {
          await dir.removeEntry(name).catch(() => {});
        }
      }
    }
    const handle = await dir.getFileHandle(filename, { create: true });
    const writable = await handle.createWritable();
    await writable.write(bytes);
    await writable.close();
    return true;
  } catch {
    return false;
  }
}

/** Remove all locally stored OPFS warm VM snapshots. */
export async function clearVmSnapshots() {
  if (typeof navigator === 'undefined' || typeof navigator.storage?.getDirectory !== 'function') {
    return;
  }
  try {
    const root = await navigator.storage.getDirectory();
    await root.removeEntry(OPFS_SNAPSHOT_DIR, { recursive: true });
  } catch {
    // Ignore if directory does not exist.
  }
}

async function clearServiceWorkerBlockCache() {
  if (typeof caches !== 'undefined') {
    await caches.delete('shiraz-runtime-blocks-v1').catch(() => {});
  }
  if (typeof navigator !== 'undefined' && navigator.serviceWorker?.controller) {
    try {
      navigator.serviceWorker.controller.postMessage({ type: 'CLEAR_RUNTIME_CACHE' });
    } catch {
      // Ignore if controller is not ready.
    }
  }
}

/** Stop and release any warm reusable Linux VM kept in memory. */
export async function disposeWarmVm() {
  const pool = warmPool;
  warmPool = null;
  if (!pool) return;
  try {
    pool.vm?.stop?.();
  } catch {
    // Ignore if already stopped.
  }
  try {
    pool.audio?.close?.();
  } catch {
    // Ignore if already closed.
  }
}

/** Clear IndexedDB app storage, OPFS warm snapshots, SW runtime block cache, and warm VM. */
export async function clearPersistentStorage() {
  await disposeWarmVm();
  const { resetDataDisk } = await loadUnkernel();
  await Promise.all([
    resetDataDisk({ name: DATA_DISK_NAME, version: DATA_DISK_VERSION }),
    clearVmSnapshots(),
    clearServiceWorkerBlockCache()
  ]);
}

/** Stream arbitrary bytes into a guest path via sh stdin (requires zero 9p mounts). */
async function writeGuestFile(vm, guestPath, bytes) {
  const data = typeof bytes === 'string' ? encoder.encode(bytes) : bytes;
  const proc = await vm.spawn('sh', ['-c', 'cat > "$1"', '--', guestPath], { pty: false });
  proc.stdout.cancel().catch(() => {});
  proc.stderr.cancel().catch(() => {});
  const writer = proc.stdin.getWriter();
  const CHUNK = 256 * 1024;
  try {
    for (let offset = 0; offset < data.byteLength; offset += CHUNK) {
      await writer.write(data.subarray(offset, Math.min(offset + CHUNK, data.byteLength)));
    }
    await writer.close();
  } catch (error) {
    writer.abort().catch(() => {});
    throw error;
  }
  const ended = await proc.ended;
  if (ended.code !== 0) {
    throw new Error(`Failed staging ${guestPath} inside guest (exit ${ended.code})`);
  }
}

/** Probe mounted Shiraz runtime capabilities inside the guest (fast single exec). */
async function probeGuestRuntimeCapabilities(vm) {
  const probe = await vm.exec('sh', [
    '-c',
    `
AOT=0
if [ -f /opt/shiraz/usr/art/framework/arm64/boot.art ] && [ -f /opt/shiraz/usr/share/shiraz/oat/arm64/shiraz-framework.odex ]; then
  AOT=1
elif [ -f /tmp/shiraz/usr/art/framework/arm64/boot.art ] && [ -f /tmp/shiraz/usr/share/shiraz/oat/arm64/shiraz-framework.odex ]; then
  AOT=1
fi
FB=0
if grep -qa SHIRAZ_PRESENT_FB /tmp/shiraz/usr/lib/libshiraz-jni.so 2>/dev/null; then
  FB=1
fi
AOT_MODE=0
if grep -qa SHIRAZ_ART_INIT_HEAP_MB /tmp/shiraz/usr/bin/shiraz-app 2>/dev/null; then
  AOT_MODE=1
fi
DRI=0
if [ -f /tmp/shiraz/usr/lib/swrast_dri.so ] || [ -f /tmp/shiraz/usr/lib/dri/virtio_gpu_dri.so ]; then
  DRI=1
fi
echo "AOT=$AOT FB=$FB AOT_MODE=$AOT_MODE DRI=$DRI"
`
  ]);
  const text = probe.stdout || '';
  return {
    hasBootArt: text.includes('AOT=1'),
    hasDirectFb: text.includes('FB=1'),
    hasAotLauncher: text.includes('AOT_MODE=1'),
    hasMesaDri: text.includes('DRI=1')
  };
}

/** Fast non-cryptographic frame signature for inline /dev/fb0 change detection. */
function sampleFrameDigest(data) {
  let hash = 2166136261;
  const step = Math.max(4, Math.floor(data.length / 2048) & ~3);
  for (let i = 0; i < data.length; i += step) {
    hash ^= data[i] | (data[i + 1] << 8) | (data[i + 2] << 16);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

/**
 * Boot (or reuse / OPFS-restore) unkernel + Shiraz aarch64 runtime entirely in the browser
 * and launch the prepared APK.
 *
 * Supported runtime sources:
 *   - { kind: 'http-image', url, format: 'erofs' | 'ext4', version?, sha256?, features? }
 *       Streams 4 KiB blocks lazily over HTTP Range requests inside a Web Worker (SW block-cached).
 *   - { kind: 'local-image', file, format: 'erofs' | 'ext4' }
 *       Reads 4 KiB sectors directly from a local File via BlobBlockDevice (zero extraction).
 *   - { kind: 'http-appimage', url, sha256, size }
 *       Fallback for releases shipping only Shiraz-Browser-Base-*-aarch64.AppImage.
 *   - { kind: 'local-appimage', file }
 *       Fallback when the user selects a local Shiraz-Browser-Base-*-aarch64.AppImage.
 */
export async function startShirazBrowserSession({
  base,
  runtimeSource,
  apkBytes,
  prepared,
  width = 360,
  height = 640,
  artMode = 'auto',
  cpuCount = 'auto',
  memoryMiB = 768,
  persistData = true,
  enableSnapshot = true,
  precompileApk = false,
  enableGpu = false,
  enableAudio = true,
  onStage = () => {},
  onProgress = () => {},
  onFrame = () => {},
  onGpuDraw = null,
  onRuntimeInfo = () => {},
  onLog = () => {},
  onExit = () => {}
}) {
  const report = (step, label, detail = '', percent = null) => {
    onStage(detail || label);
    onProgress({ step, label, detail: detail || label, percent });
  };

  const {
    F_RO,
    MemoryFs,
    checkSupport,
    createAudioOut,
    createKernel,
    decodeSnapshot,
    encodeSnapshot
  } = await loadUnkernel();
  const support = await checkSupport();
  if (!support.ok) {
    throw new Error(`This browser cannot run unkernel yet: ${support.problems.join('; ')}`);
  }

  // Strictly same-origin unkernel & optional virgl WebGL2 renderer bundles.
  // We never connect to unkernel.dev or pass `net`, so zero telemetry occurs.
  const kernelUrl = new URL(`${base}/unkernel/unkernel.wasm`, window.location.href).href;
  const virglUrl = new URL(`${base}/unkernel/virgl.mjs`, window.location.href).href;

  let useWorker = false;
  let runtimeMode = 'disk';
  let fsType = 'ext4';
  let runtimeId = '';
  const disks = [];

  if (runtimeSource.kind === 'http-image') {
    useWorker = true;
    fsType = runtimeSource.format === 'erofs' ? 'erofs' : 'ext4';
    const fullUrl = new URL(runtimeSource.url, window.location.href).href;
    runtimeId = `http:${fullUrl}:${runtimeSource.sha256 || runtimeSource.version || ''}`;
    disks.push({
      kind: 'http',
      url: fullUrl,
      readOnly: true,
      prefetch: false
    });
  } else if (runtimeSource.kind === 'local-image') {
    useWorker = false;
    fsType = runtimeSource.format === 'erofs' ? 'erofs' : 'ext4';
    runtimeId = `local:${runtimeSource.file.name}:${runtimeSource.file.size}:${runtimeSource.file.lastModified}`;
    disks.push(createBlobBlockDevice(runtimeSource.file, F_RO));
  } else if (runtimeSource.kind === 'http-appimage' || runtimeSource.kind === 'local-appimage') {
    runtimeMode = 'appimage';
    useWorker = true;
    runtimeId =
      runtimeSource.kind === 'local-appimage'
        ? `local-appimage:${runtimeSource.file.name}:${runtimeSource.file.size}:${runtimeSource.file.lastModified}`
        : `http-appimage:${runtimeSource.url}:${runtimeSource.sha256 || ''}`;
  } else {
    throw new Error('No Shiraz aarch64 runtime selected.');
  }

  if (persistData) {
    disks.push({
      kind: 'data',
      template: 'bundled',
      name: DATA_DISK_NAME,
      version: DATA_DISK_VERSION,
      mode: 'persistent',
      optional: true
    });
  }

  const effectiveMemoryMiB =
    runtimeMode === 'appimage' ? Math.max(memoryMiB, 1024) : Math.max(memoryMiB, 512);
  const effectiveCpus = resolveCpuCount(cpuCount, useWorker);
  const useZeroMounts = runtimeSource.kind === 'http-image' && runtimeSupportsDirectFb(runtimeSource);
  const canUseOpfsSnapshot = enableSnapshot && useZeroMounts && !enableGpu;

  const poolKey = JSON.stringify({
    runtimeId,
    effectiveMemoryMiB,
    effectiveCpus,
    persistData,
    enableGpu,
    enableAudio,
    useZeroMounts
  });

  const snapshotFilename = canUseOpfsSnapshot
    ? await makeSnapshotFilename({
        runtimeId,
        effectiveMemoryMiB,
        effectiveCpus,
        persistData,
        enableAudio
      })
    : null;

  let vm = null;
  let vmAlive = true;
  let audio = null;
  let sessionFs = null;
  let runtimeCaps = null;
  let bootKind = 'cold'; // 'cold' | 'warm-reuse' | 'opfs-snapshot'
  let displayAttached = false;
  let displayChangeHandler = null;

  if (warmPool && warmPool.key === poolKey && warmPool.alive) {
    // Reuse already-booted Linux VM + mounted /opt/shiraz overlay + warm page cache!
    vm = warmPool.vm;
    vmAlive = warmPool.alive;
    audio = warmPool.audio;
    sessionFs = warmPool.sessionFs;
    runtimeCaps = warmPool.runtimeCaps;
    displayAttached = warmPool.displayAttached;
    warmPool.setDisplayHandler(mode => {
      displayAttached = Boolean(mode);
      if (warmPool) warmPool.displayAttached = displayAttached;
      displayChangeHandler?.(mode);
    });
    bootKind = 'warm-reuse';
    report('mount', 'Reusing warm Linux VM', 'Linux kernel & runtime already mounted (page cache hot)…', 70);
    onLog(
      `[unkernel] Reusing warm Linux VM (${effectiveMemoryMiB} MiB RAM, ${effectiveCpus} CPU${effectiveCpus > 1 ? 's' : ''}, page cache hot)\n`
    );
  } else {
    if (warmPool) {
      await disposeWarmVm();
    }

    if (enableAudio) {
      try {
        audio = createAudioOut();
      } catch {
        audio = null;
      }
    }

    let mounts = undefined;
    if (!useZeroMounts) {
      sessionFs = new MemoryFs();
      mounts = { '/mnt/session': sessionFs };
    }

    if (runtimeMode === 'appimage') {
      report('kernel', 'Preparing Linux kernel', 'Loading aarch64 Shiraz base AppImage…', 18);
      let appImageBytes;
      if (runtimeSource.kind === 'local-appimage') {
        appImageBytes = new Uint8Array(await runtimeSource.file.arrayBuffer());
      } else {
        const resp = await fetch(runtimeSource.url);
        if (!resp.ok) throw new Error(`Runtime AppImage download failed: HTTP ${resp.status}`);
        const totalBytes = runtimeSource.size || Number(resp.headers.get('content-length') || 0);
        let buf;
        if (resp.body && totalBytes > 0) {
          const reader = resp.body.getReader();
          const chunks = [];
          let received = 0;
          for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            chunks.push(value);
            received += value.byteLength;
            const dlPct = Math.min(100, Math.round((100 * received) / totalBytes));
            report(
              'kernel',
              'Downloading Shiraz runtime',
              `Downloading aarch64 Shiraz base (${dlPct}%)…`,
              18 + Math.round(dlPct * 0.22)
            );
          }
          const merged = new Uint8Array(received);
          let offset = 0;
          for (const chunk of chunks) {
            merged.set(chunk, offset);
            offset += chunk.byteLength;
          }
          buf = merged.buffer;
        } else {
          buf = await resp.arrayBuffer();
        }
        if (runtimeSource.size && buf.byteLength !== runtimeSource.size) {
          throw new Error('Runtime AppImage download is incomplete.');
        }
        if (runtimeSource.sha256) {
          report('kernel', 'Verifying runtime integrity', 'Verifying SHA-256 checksum locally…', 42);
          const digest = await sha256Hex(buf);
          if (digest !== runtimeSource.sha256) throw new Error('Runtime AppImage checksum mismatch.');
        }
        appImageBytes = new Uint8Array(buf);
      }
      const appImageFs = new MemoryFs();
      appImageFs.writeFile('/base.AppImage', appImageBytes, { mode: 0o755 });
      mounts = { ...(mounts || {}), '/mnt/appimage': appImageFs };
    }

    let poolDisplayHandler = mode => {
      displayAttached = Boolean(mode);
      if (warmPool) warmPool.displayAttached = displayAttached;
      displayChangeHandler?.(mode);
    };

    const baseKernelOptions = {
      kernel: kernelUrl,
      worker: useWorker,
      memoryMiB: effectiveMemoryMiB,
      cpus: effectiveCpus,
      disks,
      display: { width: Math.round(width), height: Math.round(height) },
      onDisplay: mode => poolDisplayHandler(mode),
      ...(mounts ? { mounts } : {}),
      ...(audio ? { sound: audio } : {}),
      ...(enableGpu ? { gpu: true, gl: { module: virglUrl } } : {})
    };

    // Try fast OPFS warm snapshot restore before cold booting when eligible.
    if (snapshotFilename) {
      const snapBytes = await loadOpfsSnapshot(snapshotFilename);
      if (snapBytes) {
        const restoreStarted = performance.now();
        report('kernel', 'Restoring VM snapshot', 'Restoring warm Linux snapshot from OPFS…', 45);
        try {
          const decoded = decodeSnapshot(snapBytes);
          vm = await createKernel({
            ...baseKernelOptions,
            restore: decoded
          });
          bootKind = 'opfs-snapshot';
          const restoreMs = Math.round(performance.now() - restoreStarted);
          onLog(
            `[unkernel] Restored warm Linux snapshot from OPFS in ${restoreMs} ms (${effectiveMemoryMiB} MiB RAM, ${effectiveCpus} CPU${effectiveCpus > 1 ? 's' : ''}, offline)\n`
          );
        } catch (err) {
          // Disk generation or kernel mismatch — delete stale snapshot and cold boot cleanly.
          await deleteOpfsSnapshot(snapshotFilename);
          onLog(`[unkernel] Saved OPFS snapshot invalidated (${err?.message || 'disk changed'}); cold booting…\n`);
          vm = null;
        }
      }
    }

    if (!vm) {
      const bootStarted = performance.now();
      report('kernel', 'Booting Linux kernel', 'Loading local unkernel WebAssembly Linux 7.3 kernel…', 24);
      // Notice: `net` is deliberately omitted so the guest stays offline and unkernel
      // never opens WebSockets to any external Wisp relay or telemetry endpoint.
      vm = await createKernel({
        ...baseKernelOptions,
        onProgress: p => {
          if (p.stage === 'download' && p.total) {
            const pct = Math.min(100, Math.round((100 * p.loaded) / p.total));
            report(
              'kernel',
              'Booting Linux kernel',
              `Downloading unkernel Linux kernel (${pct}%)…`,
              24 + Math.round(pct * 0.24)
            );
          } else if (p.stage === 'compile') {
            report('kernel', 'Booting Linux kernel', 'Compiling WebAssembly Linux kernel…', 52);
          } else if (p.stage === 'start') {
            report('kernel', 'Booting Linux kernel', 'Linux 7.3 (aarch64) kernel started…', 60);
          }
        }
      });
      const bootMs = Math.round(performance.now() - bootStarted);
      onLog(
        `[unkernel] Linux aarch64 booted in ${bootMs} ms (${effectiveMemoryMiB} MiB RAM, ${effectiveCpus} CPU${effectiveCpus > 1 ? 's' : ''}, ${useWorker ? 'Web Worker' : 'inline'}, offline${enableGpu ? ', virgl WebGL2' : ''})\n`
      );

      // Mount Shiraz runtime once per VM lifetime (reused across runs and OPFS snapshots).
      const mountScript =
        runtimeMode === 'disk'
          ? `
set -e
mkdir -p /opt/shiraz /tmp/ov/upper /tmp/ov/work /tmp/shiraz /tmp/home
mount -t ${fsType} -o ro /dev/wda /opt/shiraz 2>/dev/null || mount -t ext4 -o ro /dev/wda /opt/shiraz || mount -t erofs -o ro /dev/wda /opt/shiraz
if ! cat /opt/shiraz/usr/bin/shiraz-app /opt/shiraz/usr/lib/ld-linux-aarch64.so.1 >/dev/null 2>&1; then
  if dmesg 2>/dev/null | grep -qi 'failed to decompress (lz4)'; then
    echo "SHIRAZ_EROFS_LZ4_UNSUPPORTED" >&2
    exit 42
  fi
  echo "Runtime binary read failed after mount" >&2
  exit 43
fi
mount -t overlay overlay -o lowerdir=/opt/shiraz,upperdir=/tmp/ov/upper,workdir=/tmp/ov/work /tmp/shiraz
`
          : `
set -e
mount -t tmpfs -o size=${Math.max(700, Math.min(900, Math.round(effectiveMemoryMiB * 0.8)))}M tmpfs /tmp 2>/dev/null || true
mkdir -p /tmp/work /tmp/home
cd /tmp/work
/mnt/appimage/base.AppImage --appimage-extract >/dev/null
if [ "${enableGpu ? '1' : '0'}" != "1" ]; then
  rm -rf /tmp/work/squashfs-root/usr/lib/libLLVM* /tmp/work/squashfs-root/usr/lib/libgallium* /tmp/work/squashfs-root/usr/lib/*dri.so /tmp/work/squashfs-root/usr/lib/libpython*
else
  rm -rf /tmp/work/squashfs-root/usr/lib/libpython*
fi
ln -sfn /tmp/work/squashfs-root /tmp/shiraz
`;
      report(
        'mount',
        'Mounting Shiraz runtime',
        runtimeMode === 'disk'
          ? 'Mounting Shiraz aarch64 runtime…'
          : 'Unpacking aarch64 runtime inside guest (one-time per session)…',
        66
      );
      const mounted = await vm.exec('sh', ['-c', mountScript]);
      if (mounted.exitCode !== 0) {
        const detail = (mounted.stderr || mounted.stdout || '').trim();
        vm.stop();
        audio?.close?.();
        if (mounted.exitCode === 42 || detail.includes('SHIRAZ_EROFS_LZ4_UNSUPPORTED')) {
          const err = new Error(
            'This EROFS runtime image uses LZ4/LZ4HC compression, which fails with -ENOMEM in unkernel. Rebuild with zstd compression (--erofs-compress zstd,level=9) or ext4.'
          );
          err.code = 'EROFS_LZ4_UNSUPPORTED';
          throw err;
        }
        throw new Error(`Runtime mount failed (exit ${mounted.exitCode})${detail ? `: ${detail}` : ''}`);
      }

      // Save an initial clean OPFS snapshot for pure read-only sessions right after mount.
      if (snapshotFilename && !persistData) {
        try {
          const snap = await vm.snapshot({ stop: false, dropCaches: true });
          const encoded = encodeSnapshot(snap);
          saveOpfsSnapshot(snapshotFilename, encoded).catch(() => {});
        } catch {
          // Snapshot is a pure acceleration cache; ignore failures cleanly.
        }
      }
    }

    runtimeCaps = await probeGuestRuntimeCapabilities(vm);
    const createdPool = {
      key: poolKey,
      vm,
      alive: true,
      audio,
      sessionFs,
      runtimeCaps,
      displayAttached,
      setDisplayHandler(fn) {
        poolDisplayHandler = fn;
      }
    };
    warmPool = createdPool;
    vm.exited
      .then(() => {
        createdPool.alive = false;
        vmAlive = false;
      })
      .catch(() => {
        createdPool.alive = false;
        vmAlive = false;
      });
  }

  // Stage APK + launch metadata via stdin stream (no /mnt/apk 9p mount needed).
  report('mount', 'Staging APK', `Staging ${prepared.label || prepared.package}…`, 74);
  await vm.exec('sh', [
    '-c',
    'rm -rf /tmp/shiraz/usr/android-lib /tmp/shiraz/usr/share/shiraz/oat/arm64/original.* && mkdir -p /tmp/shiraz/usr/android-lib /tmp/home && : > /tmp/input.txt'
  ]);
  await Promise.all([
    writeGuestFile(vm, '/tmp/shiraz/usr/share/shiraz/original.apk', apkBytes),
    writeGuestFile(vm, '/tmp/shiraz/usr/share/shiraz/launch.properties', prepared.launch_properties),
    writeGuestFile(vm, '/tmp/shiraz/usr/share/shiraz/application.properties', prepared.application_properties)
  ]);

  if (prepared.native) {
    const unzipRes = await vm.exec('sh', [
      '-c',
      'unzip -jo /tmp/shiraz/usr/share/shiraz/original.apk "lib/arm64-v8a/*.so" -d /tmp/shiraz/usr/android-lib >/dev/null'
    ]);
    if (unzipRes.exitCode !== 0) {
      throw new Error(`Failed extracting arm64-v8a native libraries (exit ${unzipRes.exitCode})`);
    }
  }

  // Resolve effective ART execution mode and heap settings based on runtime capabilities.
  let effectiveArtMode = artMode;
  if (effectiveArtMode === 'auto') {
    effectiveArtMode = runtimeCaps.hasBootArt && runtimeCaps.hasAotLauncher ? 'aot' : 'interpreter';
  } else if (effectiveArtMode === 'aot' && !runtimeCaps.hasAotLauncher) {
    effectiveArtMode = 'interpreter';
  } else if (effectiveArtMode !== 'jit' && effectiveArtMode !== 'aot' && effectiveArtMode !== 'interpreter') {
    effectiveArtMode = 'interpreter';
  }

  const heapMiB = effectiveMemoryMiB >= 1024 ? 384 : 256;
  const initHeapMiB = effectiveMemoryMiB >= 1024 ? 96 : 64;
  const useVirgl = Boolean(enableGpu && runtimeCaps.hasMesaDri);
  if (enableGpu && !runtimeCaps.hasMesaDri) {
    onLog(
      '[shiraz] WebGL2 GPU requested, but this runtime image was built without Mesa DRI (build with --keep-gpu); using software /dev/fb0.\n'
    );
  }

  onRuntimeInfo({
    bootKind,
    cpus: effectiveCpus,
    transport: runtimeCaps.hasDirectFb ? (useVirgl ? 'virgl+fb0' : 'fb0') : 'ppm',
    artMode: effectiveArtMode,
    hasBootArt: runtimeCaps.hasBootArt,
    hasDirectFb: runtimeCaps.hasDirectFb,
    useVirgl
  });

  // Optional persistent per-APK dex2oat quicken cache on /data/shiraz/oat/<sha256>/.
  const apkSha = String(prepared.sha256 || '').replace(/[^0-9a-f]/gi, '');
  if (persistData && runtimeCaps.hasBootArt && apkSha.length === 64) {
    const cacheScript = `
OAT_DIR="/data/shiraz/oat/${apkSha}"
TARGET_DIR="/tmp/shiraz/usr/share/shiraz/oat/arm64"
if [ -d /data ]; then
  if [ -f "$OAT_DIR/original.odex" ] && [ -f "$OAT_DIR/original.vdex" ]; then
    mkdir -p "$TARGET_DIR"
    ln -sfn "$OAT_DIR/original.odex" "$TARGET_DIR/original.odex"
    ln -sfn "$OAT_DIR/original.vdex" "$TARGET_DIR/original.vdex"
    echo "APK_OAT_HIT"
  elif [ "${precompileApk ? '1' : '0'}" = "1" ] && [ -x /tmp/shiraz/usr/bin/dex2oat ]; then
    mkdir -p "$OAT_DIR" "$TARGET_DIR" /tmp/dex2oat-work/android-data/dalvik-cache
    FW=/tmp/shiraz/usr/art/framework
    SH=/tmp/shiraz/usr/share/shiraz
    BCP="$FW/core-oj-hostdex.jar:$FW/core-libart-hostdex.jar:$FW/okhttp-hostdex.jar:$FW/bouncycastle-hostdex.jar:$FW/wolfssljni-hostdex.jar"
    BLOC="/shiraz/boot/core-oj-hostdex.jar:/shiraz/boot/core-libart-hostdex.jar:/shiraz/boot/okhttp-hostdex.jar:/shiraz/boot/bouncycastle-hostdex.jar:/shiraz/boot/wolfssljni-hostdex.jar"
    ANDROID_ROOT=/tmp/shiraz/usr/art ANDROID_DATA=/tmp/dex2oat-work/android-data BOOTCLASSPATH="$BCP" \
    /tmp/shiraz/usr/lib/ld-linux-aarch64.so.1 --library-path /tmp/shiraz/usr/lib \
      /tmp/shiraz/usr/bin/dex2oat \
      --runtime-arg -Xms32m --runtime-arg -Xmx256m \
      --runtime-arg "-Xbootclasspath:$BCP" \
      --runtime-arg "-Xbootclasspath-locations:$BLOC" \
      --instruction-set=arm64 \
      --boot-image="$FW/boot.art" \
      --dex-file="$SH/original.apk" \
      --dex-location=original.apk \
      --oat-file="$OAT_DIR/original.odex" \
      --compiler-filter=quicken \
      --no-generate-debug-info \
      --host \
      --class-loader-context="PCL[shiraz-framework.jar]" \
      --classpath-dir="$SH" >/dev/null 2>&1 \
      && ln -sfn "$OAT_DIR/original.odex" "$TARGET_DIR/original.odex" \
      && ln -sfn "$OAT_DIR/original.vdex" "$TARGET_DIR/original.vdex" \
      && echo "APK_OAT_COMPILED" \
      || { rm -rf "$OAT_DIR"; echo "APK_OAT_SKIP"; }
  fi
fi
`;
    if (precompileApk) {
      report('art', 'Optimizing APK cache', 'Checking / compiling local per-APK OAT cache on /data…', 78);
    }
    const oatRes = await vm.exec('sh', ['-c', cacheScript]);
    if ((oatRes.stdout || '').includes('APK_OAT_HIT')) {
      onLog('[shiraz] Loaded cached per-APK OAT code from /data.\n');
    } else if ((oatRes.stdout || '').includes('APK_OAT_COMPILED')) {
      onLog('[shiraz] Compiled & cached per-APK OAT code on /data for faster repeat launches.\n');
    }
  }

  report(
    'art',
    'Starting ARM64 ART',
    `Starting ${prepared.label || prepared.package} (${effectiveArtMode.toUpperCase()}${runtimeCaps.hasBootArt ? ' + boot.art' : ''})…`,
    82
  );

  let closed = false;
  let frameReuse = null;
  let frameSeq = 0;
  let directFbActive = false;
  let fbRafId = 0;
  let lastWorkerFrameSeq = -1;
  let lastInlineDigest = -1;

  const emitFrame = (decoded, transport) => {
    frameReuse = decoded.data;
    frameSeq += 1;
    if (frameSeq === 1) {
      report('art', 'Running', `${prepared.label || prepared.package} painted its first frame.`, 100);
    }
    onFrame({ width: decoded.width, height: decoded.height, data: decoded.data, seq: frameSeq, transport });
  };

  // Zero-copy /dev/fb0 (kwfb) & optional WebGL2 virgl scanout frame loop.
  const stopFbLoop = () => {
    if (fbRafId) {
      cancelAnimationFrame(fbRafId);
      fbRafId = 0;
    }
  };

  const pollFbFrame = () => {
    fbRafId = 0;
    if (closed) return;

    // If WebGL2 virgl 3D scanout is active, draw directly from texture with zero readback.
    if (useVirgl && vm.gl?.gpu?.scanout && typeof onGpuDraw === 'function') {
      const drew = onGpuDraw(vm.gl.gpu);
      if (drew) {
        directFbActive = true;
        frameSeq += 1;
        if (frameSeq === 1) {
          report('art', 'Running', `${prepared.label || prepared.package} painted its first WebGL2 frame.`, 100);
        }
      }
    } else if (displayAttached) {
      if (typeof vm.frameSeq === 'number') {
        // WorkerKernel mode: pull latest decoded frame whenever worker published a new seq.
        if (vm.frameSeq !== lastWorkerFrameSeq) {
          const fb = vm.frame(frameReuse);
          lastWorkerFrameSeq = vm.frameSeq;
          if (fb && fb.width > 0 && fb.height > 0 && fb.data) {
            directFbActive = true;
            emitFrame(fb, 'fb0');
          }
        }
        vm.requestFrame?.();
      } else {
        // Inline KernelInstance mode: read directly from Wasm linear memory.
        const fb = vm.frame(frameReuse);
        if (fb && fb.width > 0 && fb.height > 0 && fb.data) {
          const digest = sampleFrameDigest(fb.data);
          if (digest !== lastInlineDigest) {
            lastInlineDigest = digest;
            directFbActive = true;
            emitFrame(fb, 'fb0');
          }
        }
      }
    }

    if (!closed) {
      fbRafId = requestAnimationFrame(pollFbFrame);
    }
  };

  displayChangeHandler = mode => {
    if (closed) return;
    if (mode && !fbRafId) {
      vm.requestFrame?.();
      fbRafId = requestAnimationFrame(pollFbFrame);
    }
  };
  if (displayAttached || useVirgl) {
    vm.requestFrame?.();
    fbRafId = requestAnimationFrame(pollFbFrame);
  }

  // Backwards-compatible fallback listener for runtimes without SHIRAZ_PRESENT_FB.
  let unsubscribeSessionFs = null;
  if (sessionFs) {
    unsubscribeSessionFs = sessionFs.onChange(change => {
      if (closed || directFbActive) return;
      if (change.type === 'rename' && change.to === '/frame.ppm') {
        try {
          const raw = sessionFs.readFile('/frame.ppm');
          const decoded = decodePpmToRgba(raw, frameReuse);
          if (decoded) {
            emitFrame(decoded, 'ppm');
          }
        } catch {
          // Ignore transient read race if guest overwrote frame immediately.
        }
      }
    });
  }

  // Persistent input pipe: browser events append directly to /tmp/input.txt.
  const feeder = await vm.spawn('sh', ['-c', 'cat >> /tmp/input.txt'], { pty: false });
  feeder.stdout.cancel().catch(() => {});
  feeder.stderr.cancel().catch(() => {});
  const feederWriter = feeder.stdin.getWriter();

  const dataBase = persistData ? '$([ -d /data ] && echo /data/shiraz || echo /tmp/home)' : '/tmp/home';
  const audioEnv = audio ? '' : 'SHIRAZ_AUDIO_DEVICE=null';
  const rendererEnv = useVirgl
    ? `export GALLIUM_DRIVER=virpipe
export LIBGL_ALWAYS_SOFTWARE=1
export SHIRAZ_RENDERER=auto
export SHIRAZ_CANVAS=auto`
    : `export SDL_VIDEODRIVER=dummy
export SDL_RENDER_DRIVER=software
export SHIRAZ_RENDERER=software
export SHIRAZ_CANVAS=software`;

  const appCmd = `
DATA_BASE=${dataBase}
mkdir -p "$DATA_BASE/.local/share" "$DATA_BASE/.cache"
export HOME=/tmp/home
export XDG_DATA_HOME="$DATA_BASE/.local/share"
export XDG_CACHE_HOME=/tmp/home/.cache
${rendererEnv}
export SHIRAZ_ART_MODE=${effectiveArtMode}
export SHIRAZ_ART_HEAP_MB=${heapMiB}
${runtimeCaps.hasAotLauncher ? `export SHIRAZ_ART_INIT_HEAP_MB=${initHeapMiB}` : ''}
export SHIRAZ_PRESENT_FB=/dev/fb0
${sessionFs ? 'export SHIRAZ_PRESENT_SNAPSHOT=/mnt/session/frame.ppm' : ''}
export SHIRAZ_LIVE_INPUT=/tmp/input.txt
${audioEnv ? `export ${audioEnv}` : ''}
/tmp/shiraz/usr/lib/ld-linux-aarch64.so.1 \
  --library-path /tmp/shiraz/usr/lib \
  /tmp/shiraz/usr/bin/shiraz-app --no-sandbox --width ${Math.round(width)} --height ${Math.round(height)} &
APP_PID=$!
echo "$APP_PID" > /tmp/shiraz-app.pid
# Tail ART stdout/stderr logs live into the browser console while the app runs.
(
  sleep 1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    LOG_DIR=$(ls -dt /tmp/home/.cache/shiraz-apps/*/runs/run-* 2>/dev/null | head -n 1)
    if [ -n "$LOG_DIR" ] && [ -f "$LOG_DIR/stderr.log" ]; then
      tail -n +1 -f "$LOG_DIR/stderr.log" "$LOG_DIR/stdout.log" 2>/dev/null &
      TAIL_PID=$!
      echo "$TAIL_PID" > /tmp/shiraz-tail.pid
      while kill -0 "$APP_PID" 2>/dev/null; do sleep 1; done
      sleep 0.3
      kill "$TAIL_PID" 2>/dev/null || true
      wait "$TAIL_PID" 2>/dev/null || true
      rm -f /tmp/shiraz-tail.pid
      break
    fi
    kill -0 "$APP_PID" 2>/dev/null || break
    sleep 0.5
  done
) &
TAIL_WATCHER=$!
wait "$APP_PID"
STATUS=$?
wait "$TAIL_WATCHER" 2>/dev/null || true
if [ "$STATUS" -ne 0 ]; then
  LOG_DIR=$(ls -dt /tmp/home/.cache/shiraz-apps/*/runs/run-* 2>/dev/null | head -n 1)
  if [ -n "$LOG_DIR" ] && [ -f "$LOG_DIR/stderr.log" ]; then
    echo "=== crash log tail ($LOG_DIR/stderr.log) ===" >&2
    tail -n 60 "$LOG_DIR/stderr.log" >&2
  fi
fi
rm -f /tmp/shiraz-app.pid
exit "$STATUS"
`;

  let bootHintStage = 0;
  const appProc = await vm.spawn('sh', ['-c', appCmd], { pty: false });
  const pump = async stream => {
    try {
      for await (const chunk of stream) {
        if (closed) break;
        const text = decoder.decode(chunk, { stream: true });
        if (text) {
          onLog(text);
          if (frameSeq === 0) {
            if (bootHintStage < 1 && /SHIRAZ|AndroidRuntime|art|boot/i.test(text)) {
              bootHintStage = 1;
              report('art', 'Starting ARM64 ART', 'Initializing Android framework classes…', 87);
            }
            if (bootHintStage < 2 && /SHIRAZ_FB|onCreate|Activity|Surface|Renderer|onResume/i.test(text)) {
              bootHintStage = 2;
              report('art', 'Starting ARM64 ART', 'Launching activity and rendering first frame…', 94);
            }
          }
        }
      }
    } catch {
      // Stream cancelled on stop.
    }
  };
  pump(appProc.stdout);
  pump(appProc.stderr);

  let writeChain = Promise.resolve();
  const sendCommand = line => {
    if (closed) return;
    const payload = encoder.encode(line.endsWith('\n') ? line : `${line}\n`);
    writeChain = writeChain
      .then(() => feederWriter.write(payload))
      .catch(() => {});
  };

  appProc.ended
    .then(async info => {
      if (closed) return;
      stopFbLoop();
      if (typeof unsubscribeSessionFs === 'function') unsubscribeSessionFs();
      onLog(`\n[shiraz] process ended (${info.type}${info.code !== undefined ? ` ${info.code}` : ''})\n`);
      if (persistData) {
        await vm.sync().catch(() => {});
      }
      onExit(info);
    })
    .catch(() => {});

  return {
    sendCommand,
    resumeAudio() {
      audio?.resume?.();
    },
    setMuted(muted) {
      if (audio) audio.muted = muted;
    },
    async stop({ keepWarm = true } = {}) {
      if (closed) return;
      closed = true;
      stopFbLoop();
      displayChangeHandler = null;
      if (typeof unsubscribeSessionFs === 'function') unsubscribeSessionFs();
      try {
        await feederWriter.write(encoder.encode('close\n'));
        await feederWriter.close();
      } catch {
        // Ignore if already closed.
      }
      await Promise.race([appProc.ended.catch(() => {}), new Promise(r => setTimeout(r, 1500))]);
      // Ensure no stray dalvikvm/shiraz-app/tail process holds /dev/fb0 or .session.lock.
      const stillAlive = vmAlive && (!warmPool || warmPool.vm !== vm || warmPool.alive);
      if (stillAlive) {
        await vm
          .exec('sh', [
            '-c',
            'for f in /tmp/shiraz-app.pid /tmp/shiraz-tail.pid; do [ -f "$f" ] && kill -9 "$(cat "$f")" 2>/dev/null || true; rm -f "$f"; done; pkill -9 ld-linux-aarch64 2>/dev/null || true'
          ])
          .catch(() => {});
        if (persistData) {
          await vm.sync().catch(() => {});
        }
        // Refresh OPFS snapshot after clean idle sync so persistent /data generation stays in sync!
        if (keepWarm && snapshotFilename && persistData) {
          try {
            const snap = await vm.snapshot({ stop: false, dropCaches: true });
            await saveOpfsSnapshot(snapshotFilename, encodeSnapshot(snap));
          } catch {
            // Ignore snapshot save failures.
          }
        }
      }
      if (!keepWarm) {
        await disposeWarmVm();
      }
    }
  };
}
