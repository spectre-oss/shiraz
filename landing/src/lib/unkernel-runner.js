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

const encoder = new TextEncoder();
const decoder = new TextDecoder();

/** Check whether this browser supports WebAssembly JSPI required by unkernel. */
export async function checkBrowserSupport() {
  const { checkSupport } = await loadUnkernel();
  return await checkSupport();
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

/** Clear IndexedDB persistent app data stored for browser Shiraz runs. */
export async function clearPersistentStorage() {
  const { resetDataDisk } = await loadUnkernel();
  await resetDataDisk({ name: DATA_DISK_NAME, version: DATA_DISK_VERSION });
}

/**
 * Boot unkernel + Shiraz aarch64 runtime entirely in the browser and launch the prepared APK.
 *
 * Supported runtime sources:
 *   - { kind: 'http-image', url, format: 'erofs' | 'ext4' }
 *       Streams 4 KiB blocks lazily over HTTP Range requests inside a Web Worker.
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
  artMode = 'interpreter',
  memoryMiB = 768,
  persistData = true,
  enableAudio = true,
  onStage = () => {},
  onProgress = () => {},
  onFrame = () => {},
  onLog = () => {},
  onExit = () => {}
}) {
  const report = (step, label, detail = '', percent = null) => {
    onStage(detail || label);
    onProgress({ step, label, detail: detail || label, percent });
  };

  const { F_RO, MemoryFs, checkSupport, createAudioOut, createKernel } = await loadUnkernel();
  const support = await checkSupport();
  if (!support.ok) {
    throw new Error(`This browser cannot run unkernel yet: ${support.problems.join('; ')}`);
  }

  // Strictly same-origin unkernel kernel binary; we never connect to unkernel.dev
  // or pass a `net` / Wisp relay option, so zero telemetry or third-party traffic occurs.
  const kernelUrl = new URL(`${base}/unkernel/unkernel.wasm`, window.location.href).href;
  const sessionFs = new MemoryFs();
  const apkFs = new MemoryFs({
    'original.apk': apkBytes,
    'launch.properties': prepared.launch_properties,
    'application.properties': prepared.application_properties
  });

  const mounts = {
    '/mnt/apk': apkFs,
    '/mnt/session': sessionFs
  };

  let useWorker = false;
  const disks = [];
  let runtimeMode = 'disk';
  let fsType = 'ext4';

  if (runtimeSource.kind === 'http-image') {
    useWorker = true;
    fsType = runtimeSource.format === 'erofs' ? 'erofs' : 'ext4';
    disks.push({
      kind: 'http',
      url: new URL(runtimeSource.url, window.location.href).href,
      readOnly: true,
      prefetch: false
    });
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
  } else if (runtimeSource.kind === 'local-image') {
    useWorker = false;
    fsType = runtimeSource.format === 'erofs' ? 'erofs' : 'ext4';
    disks.push(createBlobBlockDevice(runtimeSource.file, F_RO));
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
  } else if (runtimeSource.kind === 'http-appimage' || runtimeSource.kind === 'local-appimage') {
    runtimeMode = 'appimage';
    useWorker = true;
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
    mounts['/mnt/appimage'] = appImageFs;
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
  } else {
    throw new Error('No Shiraz aarch64 runtime selected.');
  }

  let audio = null;
  if (enableAudio) {
    try {
      audio = createAudioOut();
    } catch {
      audio = null;
    }
  }

  let closed = false;
  let frameReuse = null;
  let frameSeq = 0;

  sessionFs.onChange(change => {
    if (closed) return;
    if (change.type === 'rename' && change.to === '/frame.ppm') {
      try {
        const raw = sessionFs.readFile('/frame.ppm');
        const decoded = decodePpmToRgba(raw, frameReuse);
        if (decoded) {
          frameReuse = decoded.data;
          frameSeq += 1;
          if (frameSeq === 1) {
            report('art', 'Running', `${prepared.label || prepared.package} painted its first frame.`, 100);
          }
          onFrame({ ...decoded, seq: frameSeq });
        }
      } catch {
        // Ignore transient read race if guest overwrote frame immediately.
      }
    }
  });

  report('kernel', 'Booting Linux kernel', 'Loading local unkernel WebAssembly Linux 7.3 kernel…', 24);
  const effectiveMemoryMiB =
    runtimeMode === 'appimage' ? Math.max(memoryMiB, 1024) : Math.max(memoryMiB, 512);

  // Notice: `net` is deliberately omitted so the guest stays offline and unkernel
  // never opens WebSockets to any external Wisp relay or telemetry endpoint.
  const vm = await createKernel({
    kernel: kernelUrl,
    worker: useWorker,
    memoryMiB: effectiveMemoryMiB,
    disks,
    mounts,
    ...(audio ? { sound: audio } : {}),
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

  onLog(`[unkernel] Linux aarch64 booted (${effectiveMemoryMiB} MiB RAM, ${useWorker ? 'Web Worker' : 'inline'}, offline)\n`);

  const prepScript =
    runtimeMode === 'disk'
      ? `
set -e
mkdir -p /opt/shiraz /tmp/ov/upper /tmp/ov/work /tmp/shiraz /tmp/home
mount -t ${fsType} -o ro /dev/wda /opt/shiraz 2>/dev/null || mount -t ext4 -o ro /dev/wda /opt/shiraz || mount -t erofs -o ro /dev/wda /opt/shiraz
mount -t overlay overlay -o lowerdir=/opt/shiraz,upperdir=/tmp/ov/upper,workdir=/tmp/ov/work /tmp/shiraz
cp /mnt/apk/original.apk /tmp/shiraz/usr/share/shiraz/original.apk
cp /mnt/apk/launch.properties /tmp/shiraz/usr/share/shiraz/launch.properties
cp /mnt/apk/application.properties /tmp/shiraz/usr/share/shiraz/application.properties
rm -rf /tmp/shiraz/usr/android-lib && mkdir -p /tmp/shiraz/usr/android-lib
if [ "${prepared.native ? '1' : '0'}" = "1" ]; then
  unzip -jo /mnt/apk/original.apk "lib/arm64-v8a/*.so" -d /tmp/shiraz/usr/android-lib >/dev/null
fi
: > /tmp/input.txt
`
      : `
set -e
mount -t tmpfs -o size=700M tmpfs /tmp 2>/dev/null || true
mkdir -p /tmp/work /tmp/home
cd /tmp/work
/mnt/appimage/base.AppImage --appimage-extract >/dev/null
rm -rf /tmp/work/squashfs-root/usr/lib/libLLVM* /tmp/work/squashfs-root/usr/lib/libgallium* /tmp/work/squashfs-root/usr/lib/*dri.so /tmp/work/squashfs-root/usr/lib/libpython*
ln -sfn /tmp/work/squashfs-root /tmp/shiraz
cp /mnt/apk/original.apk /tmp/shiraz/usr/share/shiraz/original.apk
cp /mnt/apk/launch.properties /tmp/shiraz/usr/share/shiraz/launch.properties
cp /mnt/apk/application.properties /tmp/shiraz/usr/share/shiraz/application.properties
rm -rf /tmp/shiraz/usr/android-lib && mkdir -p /tmp/shiraz/usr/android-lib
if [ "${prepared.native ? '1' : '0'}" = "1" ]; then
  unzip -jo /mnt/apk/original.apk "lib/arm64-v8a/*.so" -d /tmp/shiraz/usr/android-lib >/dev/null
fi
: > /tmp/input.txt
`;

  report(
    'mount',
    'Mounting Shiraz runtime',
    runtimeMode === 'disk'
      ? 'Mounting Shiraz aarch64 runtime and staging APK…'
      : 'Unpacking aarch64 runtime inside guest (one-time per session)…',
    68
  );
  const prep = await vm.exec('sh', ['-c', prepScript]);
  if (prep.exitCode !== 0) {
    const detail = (prep.stderr || prep.stdout || '').trim();
    vm.stop();
    audio?.close();
    throw new Error(`Runtime setup failed (exit ${prep.exitCode})${detail ? `: ${detail}` : ''}`);
  }

  report(
    'art',
    'Starting ARM64 ART',
    `Starting ${prepared.label || prepared.package} on standalone ARM64 ART…`,
    80
  );

  // Persistent input pipe: browser events append directly to /tmp/input.txt.
  const feeder = await vm.spawn('sh', ['-c', 'cat >> /tmp/input.txt'], { pty: false });
  feeder.stdout.cancel().catch(() => {});
  feeder.stderr.cancel().catch(() => {});
  const feederWriter = feeder.stdin.getWriter();

  const dataBase = persistData ? '$([ -d /data ] && echo /data/shiraz || echo /tmp/home)' : '/tmp/home';
  const safeMode = artMode === 'jit' ? 'jit' : 'interpreter';
  const heapMiB = effectiveMemoryMiB >= 1024 ? 384 : 256;
  const audioEnv = audio ? '' : 'SHIRAZ_AUDIO_DEVICE=null';

  const appCmd = `
DATA_BASE=${dataBase}
mkdir -p "$DATA_BASE/.local/share" "$DATA_BASE/.cache"
export HOME=/tmp/home
export XDG_DATA_HOME="$DATA_BASE/.local/share"
export XDG_CACHE_HOME=/tmp/home/.cache
export SDL_VIDEODRIVER=dummy
export SDL_RENDER_DRIVER=software
export SHIRAZ_RENDERER=software
export SHIRAZ_CANVAS=software
export SHIRAZ_ART_MODE=${safeMode}
export SHIRAZ_ART_HEAP_MB=${heapMiB}
export SHIRAZ_PRESENT_SNAPSHOT=/mnt/session/frame.ppm
export SHIRAZ_LIVE_INPUT=/tmp/input.txt
${audioEnv ? `export ${audioEnv}` : ''}
/tmp/shiraz/usr/lib/ld-linux-aarch64.so.1 \
  --library-path /tmp/shiraz/usr/lib \
  /tmp/shiraz/usr/bin/shiraz-app --no-sandbox --width ${Math.round(width)} --height ${Math.round(height)} &
APP_PID=$!
# Tail ART stdout/stderr logs live into the browser console while the app runs.
(
  sleep 1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    LOG_DIR=$(ls -dt /tmp/home/.cache/shiraz-apps/*/runs/run-* 2>/dev/null | head -n 1)
    if [ -n "$LOG_DIR" ] && [ -f "$LOG_DIR/stderr.log" ]; then
      tail -n +1 -f "$LOG_DIR/stderr.log" "$LOG_DIR/stdout.log" 2>/dev/null &
      TAIL_PID=$!
      while kill -0 "$APP_PID" 2>/dev/null; do sleep 1; done
      kill "$TAIL_PID" 2>/dev/null || true
      break
    fi
    kill -0 "$APP_PID" 2>/dev/null || break
    sleep 0.5
  done
) &
wait "$APP_PID"
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
              report('art', 'Starting ARM64 ART', 'Initializing Android framework classes…', 86);
            }
            if (bootHintStage < 2 && /onCreate|Activity|Surface|Renderer|onResume/i.test(text)) {
              bootHintStage = 2;
              report('art', 'Starting ARM64 ART', 'Launching activity and rendering first frame…', 93);
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
    async stop() {
      if (closed) return;
      closed = true;
      try {
        await feederWriter.write(encoder.encode('close\n'));
        await feederWriter.close();
      } catch {
        // Ignore if already closed.
      }
      await Promise.race([appProc.ended.catch(() => {}), new Promise(r => setTimeout(r, 1500))]);
      if (persistData) {
        await vm.sync().catch(() => {});
      }
      try {
        vm.stop();
      } catch {
        // Ignore if already stopped.
      }
      audio?.close?.();
    }
  };
}
